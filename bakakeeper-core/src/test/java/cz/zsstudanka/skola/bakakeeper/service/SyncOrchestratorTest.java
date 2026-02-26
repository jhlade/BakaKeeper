package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro SyncOrchestrator.
 */
@ExtendWith(MockitoExtension.class)
class SyncOrchestratorTest {

    @Mock private AppConfig config;
    @Mock private StudentRepository studentRepo;
    @Mock private LDAPUserRepository ldapUserRepo;
    @Mock private FacultyRepository facultyRepo;
    @Mock private GuardianRepository guardianRepo;
    @Mock private StructureService structureService;
    @Mock private StudentService studentService;
    @Mock private FacultyService facultyService;
    @Mock private GuardianService guardianService;
    @Mock private RuleService ruleService;

    private SyncOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        orchestrator = new SyncOrchestrator(config, studentRepo, ldapUserRepo,
                facultyRepo, guardianRepo, structureService, studentService,
                facultyService, guardianService, ruleService);
    }

    /** Společné stubování pro testy runFullSync – strukturní kontrola vrací prázdno. */
    private void stubStructureOk() {
        when(structureService.checkAndRepairStructure(anyBoolean(), any())).thenReturn(List.of());
    }

    @Test
    void runFullSync_voláVšechnyFáze() {
        stubStructureOk();
        // příprava dat
        StudentRecord sqlStudent = new StudentRecord();
        sqlStudent.setInternalId("001");
        StudentRecord ldapStudent = new StudentRecord();
        ldapStudent.setInternalId("001");

        when(studentRepo.findActive(null, null)).thenReturn(List.of(sqlStudent));
        when(ldapUserRepo.findAllStudents(any(), any())).thenReturn(List.of(ldapStudent));
        when(config.getLdapBaseStudents()).thenReturn("OU=Zaci");
        when(config.getLdapBaseAlumni()).thenReturn("OU=Alumni");
        when(config.getLdapBaseContacts()).thenReturn("OU=Kontakty");
        when(config.getRules()).thenReturn(List.of());
        when(facultyRepo.findActive(true)).thenReturn(List.of());
        when(guardianRepo.findAllContacts(any())).thenReturn(List.of());

        // services vracejí prázdné výsledky
        when(facultyService.syncClassTeachers(any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.initializeNewStudents(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.syncStudentData(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.retireOrphanedStudents(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(guardianService.syncGuardians(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        // konvergentní model – pravidla se volají vždy (kvůli rekonciliaci)
        when(ruleService.applyRules(any(), any(), anyBoolean(), any())).thenReturn(List.of());

        SyncReport report = orchestrator.runFullSync(false, SyncProgressListener.SILENT);

        assertNotNull(report);
        assertNotNull(report.results());
        verify(structureService).checkAndRepairStructure(eq(false), any());
        verify(facultyService).syncClassTeachers(any(), eq(false), any());
        verify(studentService).initializeNewStudents(any(), any(), eq(false), any());
        verify(studentService).syncStudentData(any(), any(), eq(false), any());
        verify(studentService).retireOrphanedStudents(any(), any(), eq(false), any());
        verify(guardianService).syncGuardians(any(), any(), eq(false), any());
        // konvergentní model – pravidla se volají vždy (i s prázdnými rules – kvůli rekonciliaci)
        verify(ruleService).applyRules(eq(List.of()), any(), eq(false), any());
    }

    @Test
    void runFullSync_sRepair_znoveNačítáLDAP() {
        stubStructureOk();
        StudentRecord sqlStudent = new StudentRecord();
        StudentRecord ldapStudent = new StudentRecord();

        when(studentRepo.findActive(null, null)).thenReturn(List.of(sqlStudent));
        when(ldapUserRepo.findAllStudents(any(), any())).thenReturn(List.of(ldapStudent));
        when(config.getLdapBaseStudents()).thenReturn("OU=Zaci");
        when(config.getLdapBaseAlumni()).thenReturn("OU=Alumni");
        when(config.getLdapBaseContacts()).thenReturn("OU=Kontakty");
        when(config.getRules()).thenReturn(List.of());
        when(facultyRepo.findActive(true)).thenReturn(List.of());
        when(guardianRepo.findAllContacts(any())).thenReturn(List.of());

        when(facultyService.syncClassTeachers(any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.initializeNewStudents(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.syncStudentData(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(studentService.retireOrphanedStudents(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(guardianService.syncGuardians(any(), any(), anyBoolean(), any())).thenReturn(List.of());
        when(ruleService.applyRules(any(), any(), anyBoolean(), any())).thenReturn(List.of());

        orchestrator.runFullSync(true, SyncProgressListener.SILENT);

        // s repair=true se LDAP načítá vícekrát (po inicializaci + před pravidly)
        verify(ldapUserRepo, atLeast(3)).findAllStudents(any(), any());
    }

    @Test
    void runFullSync_agregaceVýsledků() {
        when(studentRepo.findActive(null, null)).thenReturn(List.of());
        when(ldapUserRepo.findAllStudents(any(), any())).thenReturn(List.of());
        when(config.getLdapBaseStudents()).thenReturn("OU=Zaci");
        when(config.getLdapBaseAlumni()).thenReturn("OU=Alumni");
        when(config.getLdapBaseContacts()).thenReturn("OU=Kontakty");
        when(config.getRules()).thenReturn(List.of());
        when(facultyRepo.findActive(true)).thenReturn(List.of());
        when(guardianRepo.findAllContacts(any())).thenReturn(List.of());

        // strukturní kontrola vrátí jeden výsledek
        when(structureService.checkAndRepairStructure(anyBoolean(), any()))
                .thenReturn(List.of(SyncResult.noChange("OU=Skupiny")));
        when(facultyService.syncClassTeachers(any(), anyBoolean(), any()))
                .thenReturn(List.of(SyncResult.updated("T1", "ok")));
        when(studentService.initializeNewStudents(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of(SyncResult.created("S1", "ok")));
        when(studentService.syncStudentData(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of(SyncResult.error("S2", "chyba")));
        when(studentService.retireOrphanedStudents(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());
        when(guardianService.syncGuardians(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());
        when(ruleService.applyRules(any(), any(), anyBoolean(), any())).thenReturn(List.of());

        SyncReport report = orchestrator.runFullSync(false, SyncProgressListener.SILENT);

        assertEquals(4, report.results().size());
        assertEquals(3, report.results().stream().filter(SyncResult::isSuccess).count());
        assertEquals(1, report.results().stream().filter(r -> !r.isSuccess()).count());
        assertTrue(report.guardianErrors().isEmpty());
    }

    @Test
    void runInitOnly_voláPouzeInicializaci() {
        when(studentRepo.findActive(null, null)).thenReturn(List.of());
        when(ldapUserRepo.findAllStudents(any(), any())).thenReturn(List.of());
        when(config.getLdapBaseStudents()).thenReturn("OU=Zaci");
        when(config.getLdapBaseAlumni()).thenReturn("OU=Alumni");
        when(studentService.initializeNewStudents(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());

        orchestrator.runInitOnly(false, SyncProgressListener.SILENT);

        verify(studentService).initializeNewStudents(any(), any(), eq(false), any());
        verifyNoInteractions(facultyService, guardianService, ruleService);
    }

    @Test
    void runCheckOnly_voláPouzeKontrolu() {
        when(studentRepo.findActive(null, null)).thenReturn(List.of());
        when(ldapUserRepo.findAllStudents(any(), any())).thenReturn(List.of());
        when(config.getLdapBaseStudents()).thenReturn("OU=Zaci");
        when(config.getLdapBaseAlumni()).thenReturn("OU=Alumni");
        when(studentService.syncStudentData(any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());

        orchestrator.runCheckOnly(false, SyncProgressListener.SILENT);

        verify(studentService).syncStudentData(any(), any(), eq(false), any());
        verifyNoInteractions(facultyService, guardianService, ruleService);
    }
}
