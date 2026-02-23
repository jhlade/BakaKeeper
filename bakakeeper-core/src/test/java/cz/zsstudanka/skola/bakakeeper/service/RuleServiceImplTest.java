package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.config.SyncRuleAttribute;
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

    /** Pomocná metoda – vytvoří žáka s plným DN v OU=Zaci. */
    private StudentRecord student(String id, String className, int year, String dn) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setClassName(className);
        s.setClassYear(year);
        s.setClassLetter(className.contains(".") ? className.split("\\.")[1] : "A");
        s.setDn(dn);
        return s;
    }

    /** Pomocná metoda – vytvoří žáka s loginem a UPN. */
    private StudentRecord studentWithLogin(String id, String className, int year,
                                            String dn, String samAccountName, String upn) {
        StudentRecord s = student(id, className, year, dn);
        s.setSamAccountName(samAccountName);
        s.setUpn(upn);
        return s;
    }

    /** Pomocná metoda – vytvoří zaměstnance (učitele) s DN v OU=Ucitele. */
    private StudentRecord teacher(String id, String dn, String samAccountName, String upn) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setDn(dn);
        s.setSamAccountName(samAccountName);
        s.setUpn(upn);
        return s;
    }

    // --- CLASS scope ---

    @Test
    void pravidloPodleTřidy_aplikujeNaSprávnéŽáky() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "5.A",
                EBakaLDAPAttributes.EXT02.attribute(), "TRUE");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=Novak,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "5.B", 5,
                "CN=Svoboda,OU=Trida-B,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s3 = student("003", "6.A", 6,
                "CN=Kovar,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2, s3), true, SyncProgressListener.SILENT);

        // jen s1 odpovídá třídě 5.A
        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.UPDATED, results.get(0).getType());
        verify(ldapRepo).updateAttribute(s1.getDn(),
                EBakaLDAPAttributes.EXT02, "TRUE");
    }

    // --- GRADE scope ---

    @Test
    void pravidloPodleRočníku() {
        SyncRule rule = new SyncRule(SyncScope.GRADE, "5",
                EBakaLDAPAttributes.TITLE.attribute(), "Pátý ročník");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=Novak,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "5.B", 5,
                "CN=Svo,OU=Trida-B,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s3 = student("003", "6.A", 6,
                "CN=Kov,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2, s3), true, SyncProgressListener.SILENT);

        assertEquals(2, results.size()); // s1 a s2
    }

    // --- ALL_STUDENTS scope ---

    @Test
    void pravidloVšichniŽáci() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "6.B", 6,
                "CN=S,OU=Trida-B,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        assertEquals(2, results.size());
    }

    @Test
    void allStudents_neodpovídáUčiteli() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        // učitel nemá OU=Zaci v DN
        StudentRecord teacher = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(teacher), true, SyncProgressListener.SILENT);

        assertTrue(results.isEmpty());
    }

    // --- Suchý běh ---

    @Test
    void suchýBěh_neprovedaZápis() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), false, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.SKIPPED, results.get(0).getType());
        verifyNoInteractions(ldapRepo);
    }

    // --- Neznámý atribut ---

    @Test
    void neznámýAtribut_vrátíError() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                "neexistujiciAtribut", "hodnota");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.ERROR, results.get(0).getType());
    }

    // --- Žák bez DN ---

    @Test
    void žákBezDn_přeskočen() {
        SyncRule rule = new SyncRule(SyncScope.WHOLE_SCHOOL, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5, null); // bez DN

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        assertTrue(results.isEmpty());
        verifyNoInteractions(ldapRepo);
    }

    // --- LEVEL_1 scope (zpětná kompatibilita) ---

    @Test
    void pravidloPrvníStupeň() {
        SyncRule rule = new SyncRule(SyncScope.LEVEL_1, null,
                EBakaLDAPAttributes.TITLE.attribute(), "1. stupeň");

        StudentRecord s1 = student("001", "3.A", 3,
                "CN=N,OU=Trida-A,OU=Rocnik-3,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "7.A", 7,
                "CN=S,OU=Trida-A,OU=Rocnik-7,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // jen s1 (ročník 3, 1. stupeň)
        assertEquals(1, results.size());
    }

    // --- LEVEL scope (nový, s match) ---

    @Test
    void pravidloStupeň2() {
        SyncRule rule = new SyncRule(SyncScope.LEVEL, "2",
                EBakaLDAPAttributes.TITLE.attribute(), "2. stupeň");

        StudentRecord s1 = student("001", "3.A", 3,
                "CN=N,OU=Trida-A,OU=Rocnik-3,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "7.A", 7,
                "CN=S,OU=Trida-A,OU=Rocnik-7,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // jen s2 (ročník 7, 2. stupeň)
        assertEquals(1, results.size());
    }

    // --- WHOLE_SCHOOL scope ---

    @Test
    void pravidloCeláŠkola_odpovídáVšem() {
        SyncRule rule = new SyncRule(SyncScope.WHOLE_SCHOOL, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Člen školy");

        StudentRecord s1 = student("001", "3.A", 3,
                "CN=N,OU=Trida-A,OU=Rocnik-3,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord t1 = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, t1), true, SyncProgressListener.SILENT);

        assertEquals(2, results.size());
    }

    // --- USER scope ---

    @Test
    void pravidloUserPodleSamAccountName() {
        SyncRule rule = new SyncRule(SyncScope.USER, "novak.jan",
                EBakaLDAPAttributes.TITLE.attribute(), "Speciální");

        StudentRecord s1 = studentWithLogin("001", "5.A", 5,
                "CN=novak.jan,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "novak.jan", "novak.jan@skola.ext");
        StudentRecord s2 = studentWithLogin("002", "5.B", 5,
                "CN=svoboda.petr,OU=Trida-B,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "svoboda.petr", "svoboda.petr@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Speciální");
    }

    @Test
    void pravidloUserPodleUpnPrefix() {
        SyncRule rule = new SyncRule(SyncScope.USER, "ucitel.karel",
                EBakaLDAPAttributes.TITLE.attribute(), "Vedoucí");

        StudentRecord t1 = teacher("T1",
                "CN=ucitel.karel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                null, "ucitel.karel@skola.ext"); // nemá sAMAccountName, ale má UPN

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(t1), true, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
    }

    // --- CATEGORY scope ---

    @Test
    void pravidloKategorieUcitel() {
        SyncRule rule = new SyncRule(SyncScope.CATEGORY, "UCITEL",
                EBakaLDAPAttributes.TITLE.attribute(), "Učitel");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord t1 = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, t1), true, SyncProgressListener.SILENT);

        // jen t1 (učitel)
        assertEquals(1, results.size());
        verify(ldapRepo).updateAttribute(t1.getDn(), EBakaLDAPAttributes.TITLE, "Učitel");
    }

    @Test
    void pravidloKategorieZak() {
        SyncRule rule = new SyncRule(SyncScope.CATEGORY, "ZAK",
                EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord t1 = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, t1), true, SyncProgressListener.SILENT);

        // jen s1 (žák)
        assertEquals(1, results.size());
    }

    @Test
    void pravidloKategorieVedeni() {
        SyncRule rule = new SyncRule(SyncScope.CATEGORY, "VEDENI",
                EBakaLDAPAttributes.TITLE.attribute(), "Vedení");

        StudentRecord v1 = teacher("V1",
                "CN=Reditel,OU=Vedeni,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "reditel", "reditel@skola.ext");
        StudentRecord t1 = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(v1, t1), true, SyncProgressListener.SILENT);

        // jen v1 (vedení)
        assertEquals(1, results.size());
    }

    // --- Více atributů v jednom pravidle ---

    @Test
    void pravidloSVíceAtributy() {
        List<SyncRuleAttribute> attrs = List.of(
                new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Žák 6.A"),
                new SyncRuleAttribute(EBakaLDAPAttributes.EXT02.attribute(), "TRUE")
        );
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A", attrs, List.of());

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        // 2 výsledky – jeden pro každý atribut
        assertEquals(2, results.size());
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Žák 6.A");
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.EXT02, "TRUE");
    }

    // --- Skupiny ---

    @Test
    void pravidloSeSkupinami() {
        String groupDn = "CN=Skupina-Zaci,OU=Uzivatele,OU=Skupiny,OU=Skola,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                List.of(new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Žák")),
                List.of(groupDn));

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        // 2 výsledky – 1 atribut + 1 skupina
        assertEquals(2, results.size());
        verify(ldapRepo).addToGroup(s1.getDn(), groupDn);
    }

    @Test
    void pravidloPouzeSeSkupinami_bezAtributů() {
        String groupDn1 = "CN=Skupina-A,OU=Skupiny,DC=skola,DC=local";
        String groupDn2 = "CN=Skupina-B,OU=Skupiny,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                List.of(), List.of(groupDn1, groupDn2));

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        // 2 výsledky – 2 skupiny
        assertEquals(2, results.size());
        verify(ldapRepo).addToGroup(s1.getDn(), groupDn1);
        verify(ldapRepo).addToGroup(s1.getDn(), groupDn2);
    }

    // --- Suchý běh se skupinami ---

    @Test
    void suchýBěhSeSkupinami_neprovedePřidání() {
        String groupDn = "CN=Skupina,OU=Skupiny,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.WHOLE_SCHOOL, null,
                List.of(new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Test")),
                List.of(groupDn));

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), false, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        assertEquals(SyncResult.Type.SKIPPED, results.get(0).getType());
        verifyNoInteractions(ldapRepo);
    }

    // --- TEACHERS scope ---

    @Test
    void pravidloTeachers_odpovídáPouzeUčitelům() {
        SyncRule rule = new SyncRule(SyncScope.TEACHERS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Učitel");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord t1 = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, t1), true, SyncProgressListener.SILENT);

        assertEquals(1, results.size());
        verify(ldapRepo).updateAttribute(t1.getDn(), EBakaLDAPAttributes.TITLE, "Učitel");
    }
}
