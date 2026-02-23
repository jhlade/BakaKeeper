package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Orchestrátor synchronizace – nahrazuje Sync.actionSync().
 * Koordinuje služby ve správném pořadí a agreguje výsledky.
 *
 * Pořadí fází:
 * 0. Kontrola a oprava AD struktury (StructureService)
 * 1. Synchronizace třídních distribučních seznamů (FacultyService)
 * 2. Inicializace nových žáků – vytvoření účtů (StudentService)
 * 3. Kontrola a srovnání dat spárovaných žáků (StudentService)
 * 4. Vyřazení osiřelých žáků (StudentService)
 * 5. Synchronizace zákonných zástupců (GuardianService)
 * 6. Aplikace deklarativních pravidel (RuleService)
 *
 * @author Jan Hladěna
 */
public class SyncOrchestrator {

    private final AppConfig config;
    private final StudentRepository studentRepo;
    private final LDAPUserRepository ldapUserRepo;
    private final FacultyRepository facultyRepo;
    private final GuardianRepository guardianRepo;

    private final StructureService structureService;
    private final StudentService studentService;
    private final FacultyService facultyService;
    private final GuardianService guardianService;
    private final RuleService ruleService;

    public SyncOrchestrator(AppConfig config,
                            StudentRepository studentRepo,
                            LDAPUserRepository ldapUserRepo,
                            FacultyRepository facultyRepo,
                            GuardianRepository guardianRepo,
                            StructureService structureService,
                            StudentService studentService,
                            FacultyService facultyService,
                            GuardianService guardianService,
                            RuleService ruleService) {
        this.config = config;
        this.studentRepo = studentRepo;
        this.ldapUserRepo = ldapUserRepo;
        this.facultyRepo = facultyRepo;
        this.guardianRepo = guardianRepo;
        this.structureService = structureService;
        this.studentService = studentService;
        this.facultyService = facultyService;
        this.guardianService = guardianService;
        this.ruleService = ruleService;
    }

    /**
     * Spustí kompletní synchronizaci – všechny fáze v pořadí.
     *
     * @param repair provést zápis (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return agregované výsledky ze všech fází
     */
    public List<SyncResult> runFullSync(boolean repair, SyncProgressListener listener) {
        listener.onPhaseStart("Kompletní synchronizace");
        List<SyncResult> allResults = new ArrayList<>();

        // 0. Kontrola a oprava AD struktury (OU, skupiny, distribuční seznamy)
        allResults.addAll(structureService.checkAndRepairStructure(repair, listener));

        // --- Načtení dat z repozitářů ---
        listener.onProgress("Načítání dat z SQL evidence...");
        List<StudentRecord> sqlStudents = studentRepo.findActive(null, null);
        listener.onProgress("Nalezeno " + sqlStudents.size() + " žáků v SQL.");

        listener.onProgress("Načítání dat z Active Directory...");
        List<StudentRecord> ldapStudents = ldapUserRepo.findAllStudents(
                config.getLdapBaseStudents(), config.getLdapBaseAlumni());
        listener.onProgress("Nalezeno " + ldapStudents.size() + " žáků v LDAP.");

        // 1. Synchronizace distribučních skupin třídních učitelů
        allResults.addAll(syncFaculty(repair, listener));

        // 2. Inicializace nových žáků
        allResults.addAll(studentService.initializeNewStudents(
                sqlStudents, ldapStudents, repair, listener));

        // 3. Kontrola a srovnání dat (znovu načíst LDAP – mohly přibýt nové účty)
        if (repair) {
            ldapStudents = ldapUserRepo.findAllStudents(
                    config.getLdapBaseStudents(), config.getLdapBaseAlumni());
        }
        allResults.addAll(studentService.syncStudentData(
                sqlStudents, ldapStudents, repair, listener));

        // 4. Vyřazení osiřelých žáků
        allResults.addAll(studentService.retireOrphanedStudents(
                sqlStudents, ldapStudents, repair, listener));

        // 5. Synchronizace zákonných zástupců
        allResults.addAll(syncGuardians(sqlStudents, repair, listener));

        // 6. Aplikace deklarativních pravidel
        if (!config.getRules().isEmpty()) {
            // znovu načíst – po všech úpravách
            if (repair) {
                ldapStudents = ldapUserRepo.findAllStudents(
                        config.getLdapBaseStudents(), config.getLdapBaseAlumni());
            }
            allResults.addAll(ruleService.applyRules(
                    config.getRules(), ldapStudents, repair, listener));
        }

        // --- Souhrn ---
        int ok = (int) allResults.stream().filter(SyncResult::isSuccess).count();
        int err = (int) allResults.stream().filter(r -> !r.isSuccess()).count();
        listener.onPhaseEnd("Kompletní synchronizace", ok, err);

        return allResults;
    }

    /**
     * Spustí pouze inicializaci nových žáků.
     *
     * @param repair provést zápis nebo jen kontrolu
     * @param listener sledování průběhu
     * @return výsledky inicializace
     */
    public List<SyncResult> runInitOnly(boolean repair, SyncProgressListener listener) {
        List<StudentRecord> sqlStudents = studentRepo.findActive(null, null);
        List<StudentRecord> ldapStudents = ldapUserRepo.findAllStudents(
                config.getLdapBaseStudents(), config.getLdapBaseAlumni());

        return studentService.initializeNewStudents(sqlStudents, ldapStudents, repair, listener);
    }

    /**
     * Spustí pouze kontrolu dat (bez inicializace a vyřazení).
     *
     * @param repair provést opravu nebo jen kontrolu
     * @param listener sledování průběhu
     * @return výsledky kontroly
     */
    public List<SyncResult> runCheckOnly(boolean repair, SyncProgressListener listener) {
        List<StudentRecord> sqlStudents = studentRepo.findActive(null, null);
        List<StudentRecord> ldapStudents = ldapUserRepo.findAllStudents(
                config.getLdapBaseStudents(), config.getLdapBaseAlumni());

        return studentService.syncStudentData(sqlStudents, ldapStudents, repair, listener);
    }

    /**
     * Spustí pouze synchronizaci zákonných zástupců.
     *
     * @param repair provést zápis nebo jen kontrolu
     * @param listener sledování průběhu
     * @return výsledky synchronizace
     */
    public List<SyncResult> runGuardiansOnly(boolean repair, SyncProgressListener listener) {
        List<StudentRecord> sqlStudents = studentRepo.findActive(null, null);
        return syncGuardians(sqlStudents, repair, listener);
    }

    /**
     * Spustí pouze synchronizaci třídních distribučních seznamů.
     *
     * @param repair provést zápis nebo jen kontrolu
     * @param listener sledování průběhu
     * @return výsledky synchronizace
     */
    public List<SyncResult> runFacultyOnly(boolean repair, SyncProgressListener listener) {
        return syncFaculty(repair, listener);
    }

    /**
     * Spustí pouze kontrolu a opravu AD struktury.
     *
     * @param repair provést opravu nebo jen kontrolu
     * @param listener sledování průběhu
     * @return výsledky kontroly/opravy
     */
    public List<SyncResult> runStructureOnly(boolean repair, SyncProgressListener listener) {
        return structureService.checkAndRepairStructure(repair, listener);
    }

    // --- Interní pomocné metody ---

    /**
     * Synchronizace distribučních skupin třídních učitelů.
     */
    private List<SyncResult> syncFaculty(boolean repair, SyncProgressListener listener) {
        List<FacultyRecord> classTeachers = facultyRepo.findActive(true);
        return facultyService.syncClassTeachers(classTeachers, repair, listener);
    }

    /**
     * Synchronizace zákonných zástupců (načte kontakty z LDAP).
     */
    private List<SyncResult> syncGuardians(List<StudentRecord> sqlStudents,
                                            boolean repair, SyncProgressListener listener) {
        List<GuardianRecord> contacts = guardianRepo.findAllContacts(
                config.getLdapBaseContacts());
        return guardianService.syncGuardians(sqlStudents, contacts, repair, listener);
    }
}
