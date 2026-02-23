package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Implementace služby pro synchronizaci distribučních skupin třídních učitelů.
 * Extrahováno ze Sync.syncClassTeacher().
 *
 * Pro každou třídu (1–9, A–E) se ověří, zda distribuční skupina
 * obsahuje správného třídního učitele.
 *
 * @author Jan Hladěna
 */
public class FacultyServiceImpl implements FacultyService {

    private final AppConfig config;
    private final LDAPUserRepository ldapRepo;

    public FacultyServiceImpl(AppConfig config, LDAPUserRepository ldapRepo) {
        this.config = config;
        this.ldapRepo = ldapRepo;
    }

    @Override
    public List<SyncResult> syncClassTeachers(List<FacultyRecord> classTeachers,
                                               boolean repair,
                                               SyncProgressListener listener) {
        listener.onPhaseStart("Synchronizace třídních učitelů");
        List<SyncResult> results = new ArrayList<>();

        // index učitelů podle třídy (např. "5.A")
        Map<String, FacultyRecord> teacherByClass = classTeachers.stream()
                .filter(f -> f.getClassLabel() != null)
                .collect(Collectors.toMap(FacultyRecord::getClassLabel, f -> f, (a, b) -> a));

        // LDAP adresář zaměstnanců pro vyhledání DN učitele.
        // Používáme findAllStudents() – vrací StudentRecord, ale protože potřebujeme
        // jen email a DN (zděděno z Person), funguje to i pro zaměstnanecké účty.
        List<StudentRecord> facultyLdap = ldapRepo.findAllStudents(
                config.getLdapBaseFaculty(), null);
        Map<String, StudentRecord> facultyByEmail = facultyLdap.stream()
                .filter(s -> s.getEmail() != null)
                .collect(Collectors.toMap(
                        s -> s.getEmail().toLowerCase(), s -> s, (a, b) -> a));

        // iterace přes třídy 1–9, A–E
        for (int year = 1; year <= 9; year++) {
            for (char letter = 'A'; letter <= 'E'; letter++) {
                String classLabel = year + "." + letter;
                String dlDn = "CN=Ucitele-Tridni-" + year + letter
                        + "," + config.getLdapBaseDistributionLists();

                FacultyRecord teacher = teacherByClass.get(classLabel);
                List<String> currentMembers = ldapRepo.listDirectMembers(dlDn);

                if (teacher == null) {
                    // žádný třídní učitel – DL musí být prázdná
                    if (!currentMembers.isEmpty() && repair) {
                        for (String member : currentMembers) {
                            ldapRepo.removeFromGroup(member, dlDn);
                        }
                        results.add(SyncResult.updated(classLabel, "DL vyprázdněna (žádný TU)."));
                    }
                    continue;
                }

                // najít DN učitele v LDAP
                String teacherEmail = teacher.getEmail();
                if (teacherEmail == null) continue;

                StudentRecord teacherLdap = facultyByEmail.get(teacherEmail.toLowerCase());
                if (teacherLdap == null || teacherLdap.getDn() == null) {
                    results.add(SyncResult.error(classLabel,
                            "TU " + teacher.getDisplayName() + " nenalezen v LDAP."));
                    continue;
                }

                String expectedDn = teacherLdap.getDn();
                List<String> expectedMembers = List.of(expectedDn);

                // porovnat a opravit
                boolean needsUpdate = false;

                // odebrat nepatřící
                for (String member : currentMembers) {
                    if (!member.equalsIgnoreCase(expectedDn)) {
                        if (repair) {
                            ldapRepo.removeFromGroup(member, dlDn);
                        }
                        needsUpdate = true;
                    }
                }

                // přidat chybějícího
                boolean teacherPresent = currentMembers.stream()
                        .anyMatch(m -> m.equalsIgnoreCase(expectedDn));
                if (!teacherPresent) {
                    if (repair) {
                        ldapRepo.addToGroup(expectedDn, dlDn);
                    }
                    needsUpdate = true;
                }

                if (needsUpdate) {
                    results.add(SyncResult.updated(classLabel,
                            "DL aktualizována → " + teacher.getDisplayName()));
                } else {
                    results.add(SyncResult.noChange(classLabel));
                }
            }
        }

        int ok = (int) results.stream().filter(SyncResult::isSuccess).count();
        int err = (int) results.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Synchronizace třídních učitelů", ok, err);
        return results;
    }
}
