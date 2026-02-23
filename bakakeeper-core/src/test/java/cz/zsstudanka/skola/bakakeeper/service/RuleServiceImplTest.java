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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro konvergentní RuleServiceImpl.
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

    /** Pomocná metoda – vytvoří žáka s přednastavenými ruleAttributes. */
    private StudentRecord studentWithRuleAttrs(String id, String className, int year,
                                                String dn, Map<String, String> ruleAttrs) {
        StudentRecord s = student(id, className, year, dn);
        s.setRuleAttributes(new HashMap<>(ruleAttrs));
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

    // =====================================================================
    // Základní testy matching (CLASS, GRADE, LEVEL, ALL_STUDENTS, ...)
    // =====================================================================

    @Test
    void pravidloPodleTřidy_aplikujeNaSprávnéŽáky() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "5.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=Novak,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "5.B", 5,
                "CN=Svoboda,OU=Trida-B,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s3 = student("003", "6.A", 6,
                "CN=Kovar,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2, s3), true, SyncProgressListener.SILENT);

        // s1 dostane hodnotu, s2 a s3 ne
        verify(ldapRepo).updateAttribute(s1.getDn(),
                EBakaLDAPAttributes.EXT05, "Zaci");
        // s2 a s3 nemají aktuální hodnotu → žádné čištění
        verify(ldapRepo, never()).updateAttribute(eq(s2.getDn()), eq(EBakaLDAPAttributes.EXT05), eq(""));
    }

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

        // s1 a s2 dostanou title, s3 ne
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Pátý ročník");
        verify(ldapRepo).updateAttribute(s2.getDn(), EBakaLDAPAttributes.TITLE, "Pátý ročník");
    }

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

        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Žák");
        verify(ldapRepo).updateAttribute(s2.getDn(), EBakaLDAPAttributes.TITLE, "Žák");
    }

    @Test
    void allStudents_neodpovídáUčiteli() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Žák");

        // učitel nemá OU=Zaci v DN
        StudentRecord t = teacher("T1",
                "CN=Ucitel,OU=Ucitele,OU=Zamestnanci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "ucitel", "ucitel@skola.ext");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(t), true, SyncProgressListener.SILENT);

        // učitel nedostane title, a protože nemá aktuální title, žádné čištění
        verify(ldapRepo, never()).updateAttribute(eq(t.getDn()), eq(EBakaLDAPAttributes.TITLE), anyString());
    }

    // =====================================================================
    // Suchý běh
    // =====================================================================

    @Test
    void suchýBěh_neprovedaZápis() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), false, SyncProgressListener.SILENT);

        assertFalse(results.isEmpty());
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
        verify(ldapRepo, never()).addToGroup(anyString(), anyString());
        verify(ldapRepo, never()).removeFromGroup(anyString(), anyString());
    }

    // =====================================================================
    // Neznámý atribut
    // =====================================================================

    @Test
    void neznámýAtribut_vrátíError() {
        SyncRule rule = new SyncRule(SyncScope.ALL_STUDENTS, null,
                "neexistujiciAtribut", "hodnota");

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        assertTrue(results.stream().anyMatch(r -> r.getType() == SyncResult.Type.ERROR));
    }

    // =====================================================================
    // Žák bez DN
    // =====================================================================

    @Test
    void žákBezDn_přeskočen() {
        SyncRule rule = new SyncRule(SyncScope.WHOLE_SCHOOL, null,
                EBakaLDAPAttributes.TITLE.attribute(), "Test");

        StudentRecord s1 = student("001", "5.A", 5, null); // bez DN

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        verifyNoInteractions(ldapRepo);
    }

    // =====================================================================
    // LEVEL_1, LEVEL_2, LEVEL scope
    // =====================================================================

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

        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "1. stupeň");
        // s2 nemá aktuální title → žádné čištění
        verify(ldapRepo, never()).updateAttribute(eq(s2.getDn()), any(), anyString());
    }

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

        verify(ldapRepo).updateAttribute(s2.getDn(), EBakaLDAPAttributes.TITLE, "2. stupeň");
    }

    // =====================================================================
    // WHOLE_SCHOOL, TEACHERS, USER, CATEGORY scope
    // =====================================================================

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

        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Člen školy");
        verify(ldapRepo).updateAttribute(t1.getDn(), EBakaLDAPAttributes.TITLE, "Člen školy");
    }

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

        verify(ldapRepo).updateAttribute(t1.getDn(), EBakaLDAPAttributes.TITLE, "Vedoucí");
    }

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

        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Žák");
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

        verify(ldapRepo).updateAttribute(v1.getDn(), EBakaLDAPAttributes.TITLE, "Vedení");
    }

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

        verify(ldapRepo).updateAttribute(t1.getDn(), EBakaLDAPAttributes.TITLE, "Učitel");
    }

    // =====================================================================
    // Více atributů v jednom pravidle
    // =====================================================================

    @Test
    void pravidloSVíceAtributy() {
        List<SyncRuleAttribute> attrs = List.of(
                new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Žák 6.A"),
                new SyncRuleAttribute(EBakaLDAPAttributes.EXT05.attribute(), "Zaci")
        );
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A", attrs, List.of());

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.TITLE, "Žák 6.A");
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.EXT05, "Zaci");
    }

    // =====================================================================
    // Skupiny – přidání
    // =====================================================================

    @Test
    void pravidloSeSkupinami() {
        String groupDn = "CN=Skupina-Zaci,OU=Uzivatele,OU=Skupiny,OU=Skola,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                List.of(new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Žák")),
                List.of(groupDn));

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);
        when(ldapRepo.listDirectMembers(groupDn)).thenReturn(new ArrayList<>());

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

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

        when(ldapRepo.listDirectMembers(anyString())).thenReturn(new ArrayList<>());

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        verify(ldapRepo).addToGroup(s1.getDn(), groupDn1);
        verify(ldapRepo).addToGroup(s1.getDn(), groupDn2);
    }

    @Test
    void suchýBěhSeSkupinami_neprovedePřidání() {
        String groupDn = "CN=Skupina,OU=Skupiny,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.WHOLE_SCHOOL, null,
                List.of(new SyncRuleAttribute(EBakaLDAPAttributes.TITLE.attribute(), "Test")),
                List.of(groupDn));

        StudentRecord s1 = student("001", "5.A", 5,
                "CN=N,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        // i v suchém běhu se čte listDirectMembers pro reporting
        when(ldapRepo.listDirectMembers(anyString())).thenReturn(new ArrayList<>());

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), false, SyncProgressListener.SILENT);

        assertFalse(results.isEmpty());
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
        verify(ldapRepo, never()).addToGroup(anyString(), anyString());
        verify(ldapRepo, never()).removeFromGroup(anyString(), anyString());
    }

    // =====================================================================
    // KONVERGENTNÍ TESTY – rekonciliace atributů
    // =====================================================================

    @Test
    void rekonciliace_vyčistíAtributNaUživateliKterémuPravidloNeodpovídá() {
        // pravidlo: 6.A → extensionAttribute5 = "Zaci"
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci");

        // s1 = 6.A (matchuje), s2 = 5.A s existující hodnotou (nematchuje → čistit)
        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = studentWithRuleAttrs("002", "5.A", 5,
                "CN=S,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                Map.of("extensionAttribute5", "StaryZaznam"));

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // s1 dostane hodnotu
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.EXT05, "Zaci");
        // s2 má starý záznam → vyčistit
        verify(ldapRepo).updateAttribute(s2.getDn(), EBakaLDAPAttributes.EXT05, "");
    }

    @Test
    void rekonciliace_nevyčistíAtributKdyžHodnotaUžNeníNastavena() {
        // pravidlo: 6.A → extensionAttribute5 = "Zaci"
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci");

        // s2 = 5.A BEZ aktuální hodnoty extensionAttribute5 → nic nečistit
        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "5.A", 5,
                "CN=S,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        // s2 nemá ruleAttributes nastaven → getRuleAttribute("extensionAttribute5") = null

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // s2 nemá co čistit → žádný updateAttribute s prázdnou hodnotou
        verify(ldapRepo, never()).updateAttribute(eq(s2.getDn()), eq(EBakaLDAPAttributes.EXT05), eq(""));
    }

    @Test
    void rekonciliace_nevyčistíChráněnéAtributy() {
        // pravidlo nastavuje extensionAttribute1 (chráněný!) na konkrétního uživatele
        SyncRule rule = new SyncRule(SyncScope.USER, "novak.jan",
                EBakaLDAPAttributes.EXT01.attribute(), "SPEC01");

        StudentRecord s1 = studentWithLogin("001", "5.A", 5,
                "CN=novak.jan,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "novak.jan", "novak.jan@skola.ext");
        s1.setInternalId("SPEC01");

        // s2 má extensionAttribute1 (INTERN_KOD) nastavený jinými sync fázemi
        StudentRecord s2 = studentWithLogin("002", "5.B", 5,
                "CN=svoboda.petr,OU=Trida-B,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                "svoboda.petr", "svoboda.petr@skola.ext");
        s2.setInternalId("002"); // aktuální hodnota v LDAP

        // stubbing NENÍ potřeba – s1 má hodnotu shodnou s požadovanou (SPEC01),
        // s2 má chráněný atribut → žádný updateAttribute se nezavolá

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // s2 má INTERN_KOD "002", ale extensionAttribute1 je chráněný → NESMÍ se vyčistit
        verify(ldapRepo, never()).updateAttribute(eq(s2.getDn()), eq(EBakaLDAPAttributes.EXT01), eq(""));
        // celkově žádný zápis – s1 má shodnou hodnotu, s2 je chráněný
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
    }

    @Test
    void rekonciliace_nepřepisujeHodnotuKdyžSeNezměnila() {
        // pravidlo: 6.A → extensionAttribute5 = "Zaci"
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci");

        // s1 = 6.A a UŽ MÁ správnou hodnotu
        StudentRecord s1 = studentWithRuleAttrs("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                Map.of("extensionAttribute5", "Zaci"));

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        // hodnota je shodná → žádný zápis
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
    }

    @Test
    void rekonciliace_posledníPravidloVyhrává() {
        // pravidlo 1: 6.A → ext5 = "Zaci6A"
        // pravidlo 2: LEVEL_2 → ext5 = "DruhyStupen"
        // 6.A student matchuje obě → pravidlo 2 (poslední) vyhrává
        SyncRule rule1 = new SyncRule(SyncScope.CLASS, "6.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci6A");
        SyncRule rule2 = new SyncRule(SyncScope.LEVEL_2, null,
                EBakaLDAPAttributes.EXT05.attribute(), "DruhyStupen");

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        List<SyncResult> results = service.applyRules(
                List.of(rule1, rule2), List.of(s1), true, SyncProgressListener.SILENT);

        // poslední pravidlo vyhrává → "DruhyStupen"
        verify(ldapRepo).updateAttribute(s1.getDn(), EBakaLDAPAttributes.EXT05, "DruhyStupen");
    }

    // =====================================================================
    // KONVERGENTNÍ TESTY – rekonciliace skupin
    // =====================================================================

    @Test
    void rekonciliaceSkupin_odebereNepatřícíhoČlena() {
        String groupDn = "CN=Skupina-6A,OU=Skupiny,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                List.of(), List.of(groupDn));

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = student("002", "5.A", 5,
                "CN=S,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        // s2 je aktuálně ve skupině, ale neměl by tam být
        String s2Dn = "CN=S,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local";
        when(ldapRepo.listDirectMembers(groupDn))
                .thenReturn(new ArrayList<>(List.of(s1.getDn(), s2Dn)));

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), true, SyncProgressListener.SILENT);

        // s1 už je ve skupině → bez přidání
        verify(ldapRepo, never()).addToGroup(eq(s1.getDn()), eq(groupDn));
        // s2 nepatří → odebrat
        verify(ldapRepo).removeFromGroup(s2Dn, groupDn);
    }

    @Test
    void rekonciliaceSkupin_přidáChybějícíhoČlena() {
        String groupDn = "CN=Skupina-6A,OU=Skupiny,DC=skola,DC=local";
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                List.of(), List.of(groupDn));

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        // skupina je prázdná
        when(ldapRepo.listDirectMembers(groupDn)).thenReturn(new ArrayList<>());

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        verify(ldapRepo).addToGroup(s1.getDn(), groupDn);
    }

    @Test
    void rekonciliaceSkupin_vyprázdníSkupinuKdyžŽádnýNeodpovídá() {
        String groupDn = "CN=Skupina,OU=Skupiny,DC=skola,DC=local";
        // pravidlo pro neexistující třídu
        SyncRule rule = new SyncRule(SyncScope.CLASS, "9.E",
                List.of(), List.of(groupDn));

        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");

        // skupina obsahuje starého člena
        when(ldapRepo.listDirectMembers(groupDn))
                .thenReturn(new ArrayList<>(List.of(s1.getDn())));

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1), true, SyncProgressListener.SILENT);

        // nikdo nematchuje → odebrat stávajícího člena
        verify(ldapRepo).removeFromGroup(s1.getDn(), groupDn);
        verify(ldapRepo, never()).addToGroup(anyString(), anyString());
    }

    // =====================================================================
    // Prázdná pravidla
    // =====================================================================

    @Test
    void prázdnáPravidla_žádnéOperace() {
        StudentRecord s1 = studentWithRuleAttrs("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                Map.of("extensionAttribute5", "StaráHodnota"));

        List<SyncResult> results = service.applyRules(
                List.of(), List.of(s1), true, SyncProgressListener.SILENT);

        // žádná pravidla → žádné spravované atributy → žádné operace
        // (konvergentní model bez pravidel nic nečistí – viz dokumentace)
        verifyNoInteractions(ldapRepo);
    }

    // =====================================================================
    // Rekonciliace v suchém běhu
    // =====================================================================

    @Test
    void suchýBěh_reportujeRekonciliaci() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                EBakaLDAPAttributes.EXT05.attribute(), "Zaci");

        // s2 má starý záznam, který by se v repair modu vyčistil
        StudentRecord s1 = student("001", "6.A", 6,
                "CN=N,OU=Trida-A,OU=Rocnik-6,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local");
        StudentRecord s2 = studentWithRuleAttrs("002", "5.A", 5,
                "CN=S,OU=Trida-A,OU=Rocnik-5,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local",
                Map.of("extensionAttribute5", "StaryZaznam"));

        List<SyncResult> results = service.applyRules(
                List.of(rule), List.of(s1, s2), false, SyncProgressListener.SILENT);

        // suchý běh – reportuje, ale neprovede zápis
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> r.getDescription() != null
                && r.getDescription().contains("Rekonciliace")));
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
    }
}
