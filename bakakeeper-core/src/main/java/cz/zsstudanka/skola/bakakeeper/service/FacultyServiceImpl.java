package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;

import java.util.ArrayList;
import java.util.HashMap;
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
 * Vyhledávání učitele v LDAP probíhá postupně:
 * 1. dle emailu (mail = E_MAIL ze SQL)
 * 2. dle UPN prefixu (shodná lokální část emailu)
 * 3. dle zobrazovaného jména (displayName)
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

        // LDAP adresář zaměstnanců – pro vyhledání DN učitele.
        // Používáme findAllStudents() – vrací StudentRecord, ale protože potřebujeme
        // jen email, UPN a DN (zděděno z Person), funguje to i pro zaměstnanecké účty.
        List<StudentRecord> facultyLdap = ldapRepo.findAllStudents(
                config.getLdapBaseFaculty(), null);

        // indexy pro vyhledávání
        FacultyLdapIndex index = new FacultyLdapIndex(facultyLdap);

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

                // najít DN učitele v LDAP (email → UPN prefix → displayName)
                StudentRecord teacherLdap = index.resolve(teacher);
                if (teacherLdap == null || teacherLdap.getDn() == null) {
                    results.add(SyncResult.error(classLabel,
                            "TU " + teacher.getDisplayName() + " nenalezen v LDAP"
                                    + " (email: " + teacher.getEmail() + ")."));
                    continue;
                }

                String expectedDn = teacherLdap.getDn();

                // synchronizace INTERN_KOD → extensionAttribute1
                String sqlInternKod = teacher.getInternalId();
                String ldapInternKod = teacherLdap.getInternalId();
                if (repair && sqlInternKod != null && !sqlInternKod.equals(ldapInternKod)) {
                    ldapRepo.updateAttribute(expectedDn, EBakaLDAPAttributes.EXT01, sqlInternKod);
                    results.add(SyncResult.updated(classLabel,
                            "INTERN_KOD → extensionAttribute1: " + sqlInternKod));
                }

                // porovnat a opravit DL
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

    // ---- Vnitřní index pro robustní vyhledávání učitelů v LDAP ----

    /**
     * Index zaměstnanců z LDAP pro rychlé vyhledávání dle emailu,
     * UPN prefixu a zobrazovaného jména.
     */
    private static class FacultyLdapIndex {
        private final Map<String, StudentRecord> byEmail;
        private final Map<String, StudentRecord> byUpnPrefix;
        private final Map<String, StudentRecord> byDisplayName;

        FacultyLdapIndex(List<StudentRecord> facultyLdap) {
            byEmail = new HashMap<>();
            byUpnPrefix = new HashMap<>();
            byDisplayName = new HashMap<>();

            for (StudentRecord s : facultyLdap) {
                if (s.getEmail() != null) {
                    byEmail.put(s.getEmail().toLowerCase(), s);
                }
                if (s.getUpn() != null) {
                    // UPN prefix = lokální část před @
                    String prefix = s.getUpn().contains("@")
                            ? s.getUpn().substring(0, s.getUpn().indexOf('@')).toLowerCase()
                            : s.getUpn().toLowerCase();
                    byUpnPrefix.put(prefix, s);
                }
                if (s.getDisplayName() != null) {
                    byDisplayName.put(s.getDisplayName().toLowerCase(), s);
                }
            }
        }

        /**
         * Vyhledá učitele v LDAP – postupně dle emailu, UPN prefixu, displayName.
         *
         * @param teacher učitel ze SQL
         * @return nalezený LDAP záznam, nebo null
         */
        StudentRecord resolve(FacultyRecord teacher) {
            String email = teacher.getEmail();
            if (email == null) return null;

            // 1. přesná shoda emailu
            StudentRecord found = byEmail.get(email.toLowerCase());
            if (found != null) return found;

            // 2. shoda lokální části emailu s UPN prefixem
            // SQL: ucitel.karel@skola.ext, LDAP UPN: ucitel.karel@skola.local
            String localPart = email.contains("@")
                    ? email.substring(0, email.indexOf('@')).toLowerCase()
                    : email.toLowerCase();
            found = byUpnPrefix.get(localPart);
            if (found != null) return found;

            // 3. fallback: displayName (Příjmení Jméno)
            if (teacher.getDisplayName() != null) {
                found = byDisplayName.get(teacher.getDisplayName().toLowerCase());
                if (found != null) return found;
            }

            return null;
        }
    }
}
