package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro GuardianServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class GuardianServiceImplTest {

    @Mock private AppConfig config;
    @Mock private GuardianRepository guardianRepo;
    @Mock private LDAPUserRepository ldapRepo;

    private GuardianServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GuardianServiceImpl(config, guardianRepo, ldapRepo);
    }

    private StudentRecord sqlStudent(String id, String guardianId,
                                      String guaSurname, String guaGivenName,
                                      String guaEmail, String guaPhone,
                                      String className) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname("Žák");
        s.setGivenName("Testovací");
        s.setClassName(className);
        s.setGuardianInternalId(guardianId);
        s.setGuardianSurname(guaSurname);
        s.setGuardianGivenName(guaGivenName);
        s.setGuardianEmail(guaEmail);
        s.setGuardianPhone(guaPhone);
        return s;
    }

    private GuardianRecord existingContact(String id, String surname, String givenName,
                                            String email, String phone, String dn) {
        GuardianRecord g = new GuardianRecord();
        g.setInternalId(id);
        g.setSurname(surname);
        g.setGivenName(givenName);
        g.setDisplayName(surname + " " + givenName);
        g.setEmail(email);
        g.setPhone(phone);
        g.setDn(dn);
        return g;
    }

    @Test
    void syncGuardians_vytvořiNovýKontakt() {
        StudentRecord s = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "5.A");

        when(config.getLdapBaseContacts()).thenReturn("OU=Kontakty");
        when(config.getLdapBaseDistributionLists()).thenReturn("OU=DL");
        when(ldapRepo.checkDN(anyString())).thenReturn(false);

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.CREATED, outcome.results().get(0).getType());
        verify(guardianRepo).createContact(eq("Nováková Marie"), any());
    }

    @Test
    void syncGuardians_suchýBěh_neprovádíZápis() {
        StudentRecord s = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "5.A");

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(), Map.of(), false, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.SKIPPED, outcome.results().get(0).getType());
        verifyNoInteractions(guardianRepo, ldapRepo);
    }

    @Test
    void syncGuardians_existujícíKontaktBezZměn() {
        StudentRecord s = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "5.A");
        GuardianRecord g = existingContact("G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "CN=Nováková Marie,OU=Kontakty");

        when(config.getLdapBaseDistributionLists()).thenReturn("OU=DL");
        when(ldapRepo.listDirectMembers(anyString())).thenReturn(List.of());

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(g), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.NO_CHANGE, outcome.results().get(0).getType());
    }

    @Test
    void syncGuardians_aktualizujeTelefon() {
        StudentRecord s = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novakova@email.cz", "999888777", "5.A");
        GuardianRecord g = existingContact("G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "CN=Nováková Marie,OU=Kontakty");

        when(config.getLdapBaseDistributionLists()).thenReturn("OU=DL");
        when(ldapRepo.listDirectMembers(anyString())).thenReturn(List.of());
        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(g), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.UPDATED, outcome.results().get(0).getType());
        verify(ldapRepo).updateAttribute("CN=Nováková Marie,OU=Kontakty",
                EBakaLDAPAttributes.MOBILE, "999888777");
    }

    @Test
    void syncGuardians_aktualizujePříjmení() {
        StudentRecord s = sqlStudent("S1", "G1", "Svobodová", "Marie",
                "novakova@email.cz", "777111222", "5.A");
        GuardianRecord g = existingContact("G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "CN=Nováková Marie,OU=Kontakty");

        when(config.getLdapBaseDistributionLists()).thenReturn("OU=DL");
        when(ldapRepo.listDirectMembers(anyString())).thenReturn(List.of());
        when(ldapRepo.updateAttribute(anyString(), any(), anyString())).thenReturn(true);

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(g), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.UPDATED, outcome.results().get(0).getType());
        verify(ldapRepo).updateAttribute("CN=Nováková Marie,OU=Kontakty",
                EBakaLDAPAttributes.NAME_LAST, "Svobodová");
        verify(ldapRepo).updateAttribute("CN=Nováková Marie,OU=Kontakty",
                EBakaLDAPAttributes.NAME_DISPLAY, "Svobodová Marie");
    }

    @Test
    void syncGuardians_suchýBěh_hlásíZměny() {
        StudentRecord s = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novy@email.cz", "999888777", "5.A");
        GuardianRecord g = existingContact("G1", "Nováková", "Marie",
                "starý@email.cz", "777111222", "CN=Nováková Marie,OU=Kontakty");

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(g), Map.of(), false, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.SKIPPED, outcome.results().get(0).getType());
        assertTrue(outcome.results().get(0).getDescription().contains("Suchý běh"));
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void syncGuardians_smazeOsiřelýKontakt() {
        GuardianRecord g = existingContact("G_ORPHAN", "Starý", "Kontakt",
                "stary@email.cz", "111222333", "CN=Starý Kontakt,OU=Kontakty");

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(), List.of(g), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.RETIRED, outcome.results().get(0).getType());
        verify(guardianRepo).deleteContact("CN=Starý Kontakt,OU=Kontakty");
    }

    @Test
    void syncGuardians_bezGuardianId_validačníChyba() {
        StudentRecord s = new StudentRecord();
        s.setInternalId("S1");
        s.setSurname("Nový");
        s.setGivenName("Žák");
        s.setGuardianInternalId(null);

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(), Map.of(), true, SyncProgressListener.SILENT);

        assertTrue(outcome.results().isEmpty());
        assertEquals(1, outcome.validationErrors().size());
        assertEquals(GuardianValidationError.ErrorType.NO_PRIMARY_GUARDIAN,
                outcome.validationErrors().get(0).type());
    }

    @Test
    void syncGuardians_chybějícíPříjmeníZástupce_vrátíError() {
        StudentRecord s = sqlStudent("S1", "G1", null, "Marie",
                "novakova@email.cz", "777111222", "5.A");

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s), List.of(), Map.of(), true, SyncProgressListener.SILENT);

        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.ERROR, outcome.results().get(0).getType());
    }

    @Test
    void syncGuardians_deduplikaceGuardianId() {
        // dva žáci sdílí jednoho zástupce → zpracuje se jen jednou
        StudentRecord s1 = sqlStudent("S1", "G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "5.A");
        StudentRecord s2 = sqlStudent("S2", "G1", "Nováková", "Marie",
                "novakova@email.cz", "777111222", "5.B");

        when(config.getLdapBaseContacts()).thenReturn("OU=Kontakty");
        when(config.getLdapBaseDistributionLists()).thenReturn("OU=DL");
        when(ldapRepo.checkDN(anyString())).thenReturn(false);

        GuardianSyncOutcome outcome = service.syncGuardians(
                List.of(s1, s2), List.of(), Map.of(), true, SyncProgressListener.SILENT);

        // jen 1 vytvoření, ne 2
        assertEquals(1, outcome.results().size());
        assertEquals(SyncResult.Type.CREATED, outcome.results().get(0).getType());
    }
}
