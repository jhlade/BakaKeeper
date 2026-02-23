package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro PairingServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class PairingServiceImplTest {

    @Mock
    private LDAPUserRepository ldapRepo;

    private PairingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PairingServiceImpl(ldapRepo);
    }

    private StudentRecord createSqlStudent(String id, String surname, String givenName, int year, String letter) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname(surname);
        s.setGivenName(givenName);
        s.setClassYear(year);
        s.setClassLetter(letter);
        return s;
    }

    private StudentRecord createLdapStudent(String id, String surname, String givenName, String dn) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname(surname);
        s.setGivenName(givenName);
        s.setDn(dn);
        return s;
    }

    @Test
    void plnaShoda_sparujePriRepair() {
        StudentRecord sql = createSqlStudent("ABC123", "Novák", "Jan", 5, "A");
        StudentRecord ldap = createLdapStudent(null, "Novák", "Jan",
                "CN=Novak Jan,OU=Trida-A,OU=Rocnik-5,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.EXT01), eq("ABC123")))
                .thenReturn(true);

        SyncResult result = service.attemptToPair(sql, ldap, true);

        assertEquals(SyncResult.Type.PAIRED, result.getType());
        verify(ldapRepo).updateAttribute(ldap.getDn(), EBakaLDAPAttributes.EXT01, "ABC123");
    }

    @Test
    void plnaShoda_bezRepair_preskoci() {
        StudentRecord sql = createSqlStudent("ABC123", "Novák", "Jan", 5, "A");
        StudentRecord ldap = createLdapStudent(null, "Novák", "Jan",
                "CN=Novak Jan,OU=Trida-A,OU=Rocnik-5,OU=Zaci");

        SyncResult result = service.attemptToPair(sql, ldap, false);

        assertEquals(SyncResult.Type.SKIPPED, result.getType());
        assertTrue(result.getDescription().contains("suchý běh"));
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void uzSparovanyUcet_preskoci() {
        StudentRecord sql = createSqlStudent("ABC123", "Novák", "Jan", 5, "A");
        StudentRecord ldap = createLdapStudent("XYZ789", "Novák", "Jan",
                "CN=Novak Jan,OU=Trida-A,OU=Rocnik-5,OU=Zaci");

        SyncResult result = service.attemptToPair(sql, ldap, true);

        assertEquals(SyncResult.Type.SKIPPED, result.getType());
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void nízkeSkore_nesparuje() {
        StudentRecord sql = createSqlStudent("ABC123", "Novák", "Jan", 5, "A");
        StudentRecord ldap = createLdapStudent(null, "Svoboda", "Petr",
                "CN=Svoboda Petr,OU=Trida-B,OU=Rocnik-3,OU=Zaci");

        SyncResult result = service.attemptToPair(sql, ldap, true);

        assertEquals(SyncResult.Type.SKIPPED, result.getType());
        assertTrue(result.getDescription().contains("neúspěšné"));
        verifyNoInteractions(ldapRepo);
    }

    @Test
    void castecnaShoda_příjmeníARočník_sparuje() {
        StudentRecord sql = createSqlStudent("ABC123", "Novák", "Jan", 5, "A");
        StudentRecord ldap = createLdapStudent(null, "Novák", "Petr",
                "CN=Novak Petr,OU=Trida-B,OU=Rocnik-5,OU=Zaci");

        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.EXT01), anyString()))
                .thenReturn(true);

        // příjmení (0.25) + ročník (0.25) = 0.5, yearMatch=true → práh splněn
        SyncResult result = service.attemptToPair(sql, ldap, true);

        assertEquals(SyncResult.Type.PAIRED, result.getType());
    }
}
