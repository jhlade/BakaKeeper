package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
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
 * Testy pro RuleServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class RuleServiceImplTest {

    @Mock
    private LDAPUserRepository ldapRepo;

    private RuleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RuleServiceImpl(ldapRepo);
    }

    private StudentRecord student(String id, String className, int year, String dn) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setClassName(className);
        s.setClassYear(year);
        s.setClassLetter(className.contains(".") ? className.split("\\.")[1] : "A");
        s.setDn(dn);
        return s;
    }

    @Test
    void pravidloPodleTřidy_aplikujeNaSprávnéŽáky() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "5.A",
                EBakaLDAPAttributes.EXT02.attribute(), "TRUE");

        StudentRecord s1 = student("001", "5.A", 5, "CN=Novak,OU=Zaci");
        StudentRecord s2 = student("002", "5.B", 5, "CN=Svoboda,OU=Zaci");
        StudentRecord s3 = student("003", "6.A", 6, "CN=Kovar,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2, s3), true, SyncProgressListener.SILENT);

        // jen s1 odpovídá třídě 5.A
        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.UPDATED, results.get(0).getType());
        verify(ldapRepo).updateAttribute("CN=Novak,OU=Zaci",
                EBakaLDAPAttributes.EXT02, "TRUE");
    }

    @Test
    void pravidloPodleRočníku() {
        SyncRule rule = new SyncRule(SyncScope.GRADE, "5",
                EBakaLDAPAttributes.TITLE.attribute(), "Pátý ročník");

        StudentRecord s1 = student("001", "5.A", 5, "CN=Novak,OU=Zaci");
        StudentRecord s2 = student("002", "5.B", 5, "CN=Svo,OU=Zaci");
        StudentRecord s3 = student("003", "6.A", 6, "CN=Kov,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2, s3), true, SyncProgressListener.SILENT);

        assertEquals(2, results.size()); // s1 a s2
    }

    @Test
    void pravidloVšichniŽáci() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        StudentRecord s1 = student("001", "5.A", 5, "CN=N,OU=Zaci");
        StudentRecord s2 = student("002", "6.B", 6, "CN=S,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        assertEquals(2, results.size());
    }

    @Test
    void suchýBěh_neprovedaZápis() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5, "CN=N,OU=Zaci");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), false, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.SKIPPED, results.get(0).getType());
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void neznámýAtribut_vrátíError() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                "neexistujiciAtribut", "hodnota");

        StudentRecord s1 = student("001", "5.A", 5, "CN=N,OU=Zaci");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.ERROR, results.get(0).getType());
    }

    @Test
    void žákBezDn_přeskočen() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5, null); // bez DN

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        assertTrue(results.isEmpty());
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void pravidloPrvníStupeň() {
        SyncRule rule = new SyncRule(SyncScope.LEVEL_1, null,
                EBakaLDAPAttributes.TITLE.attribute(), "1. stupeň");

        StudentRecord s1 = student("001", "3.A", 3, "CN=N,OU=Zaci");
        StudentRecord s2 = student("002", "7.A", 7, "CN=S,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // jen s1 (ročník 3, 1. stupeň)
        assertEquals(1, results.size());
    }
}
