package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaLDAPUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro BakaLDAPUserRepository.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class BakaLDAPUserRepositoryTest {

    @Mock LDAPConnector ldap;

    private BakaLDAPUserRepository repo;

    @BeforeEach
    void setUp() {
        repo = new BakaLDAPUserRepository(ldap);
    }

    @Test
    void findAllStudentsReturnsMappedRecords() {
        Map<Integer, Map<String, String>> raw = new LinkedHashMap<>();
        raw.put(0, createUserEntry("12345", "Novák", "Tomáš", "novak.tomas@skola.local",
                "CN=Novák Tomáš,OU=Zaci,DC=skola,DC=local", "512"));

        when(ldap.getObjectInfo(eq("OU=Zaci"), any(HashMap.class), any(String[].class))).thenReturn(raw);

        List<StudentRecord> result = repo.findAllStudents("OU=Zaci", "OU=Alumni");

        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).getInternalId());
        assertEquals("Novák", result.get(0).getSurname());
        assertEquals("novak.tomas@skola.local", result.get(0).getUpn());
        assertEquals(512, result.get(0).getUac());
    }

    @Test
    void findAllStudentsExcludesAlumni() {
        Map<Integer, Map<String, String>> raw = new LinkedHashMap<>();
        raw.put(0, createUserEntry("12345", "Novák", "Tomáš", "novak.tomas@skola.local",
                "CN=Novák Tomáš,OU=Zaci,DC=skola,DC=local", "512"));
        raw.put(1, createUserEntry("99999", "Starý", "Adam", "stary.adam@skola.local",
                "CN=Starý Adam,OU=Alumni,DC=skola,DC=local", "514"));

        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class))).thenReturn(raw);

        List<StudentRecord> result = repo.findAllStudents("OU=Skola", "OU=Alumni");

        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).getInternalId());
    }

    @Test
    void findAllStudentsEmpty() {
        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class)))
                .thenReturn(new LinkedHashMap<>());

        List<StudentRecord> result = repo.findAllStudents("OU=Zaci", null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByUPN() {
        Map<Integer, Map<String, String>> raw = new LinkedHashMap<>();
        raw.put(0, createUserEntry("12345", "Novák", "Tomáš", "novak.tomas@skola.local",
                "CN=Novák Tomáš,OU=Zaci,DC=skola,DC=local", "512"));
        raw.put(1, createUserEntry("67890", "Dvořák", "Petr", "dvorak.petr@skola.local",
                "CN=Dvořák Petr,OU=Zaci,DC=skola,DC=local", "512"));

        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class))).thenReturn(raw);

        StudentRecord found = repo.findByUPN("OU=Zaci", "dvorak.petr@skola.local");
        assertNotNull(found);
        assertEquals("67890", found.getInternalId());
    }

    @Test
    void findByUPNnotFound() {
        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class)))
                .thenReturn(new LinkedHashMap<>());

        assertNull(repo.findByUPN("OU=Zaci", "neexistuje@skola.local"));
    }

    @Test
    void checkDNdelegates() {
        when(ldap.checkDN("CN=Test,DC=skola,DC=local")).thenReturn(true);
        assertTrue(repo.checkDN("CN=Test,DC=skola,DC=local"));
    }

    @Test
    void updateAttributeDelegates() {
        when(ldap.replaceAttribute("CN=Test", EBakaLDAPAttributes.MAIL, "new@skola.cz")).thenReturn(true);
        assertTrue(repo.updateAttribute("CN=Test", EBakaLDAPAttributes.MAIL, "new@skola.cz"));
    }

    @Test
    void moveObjectDelegates() {
        when(ldap.moveObject("CN=Test,OU=Old", "OU=New")).thenReturn(true);
        assertTrue(repo.moveObject("CN=Test,OU=Old", "OU=New"));
    }

    // --- pomocná metoda ---

    private Map<String, String> createUserEntry(String id, String surname, String givenName,
                                                 String upn, String dn, String uac) {
        Map<String, String> entry = new HashMap<>();
        entry.put(EBakaLDAPAttributes.EXT01.attribute(), id);
        entry.put(EBakaLDAPAttributes.NAME_LAST.attribute(), surname);
        entry.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), givenName);
        entry.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), surname + " " + givenName);
        entry.put(EBakaLDAPAttributes.UPN.attribute(), upn);
        entry.put(EBakaLDAPAttributes.DN.attribute(), dn);
        entry.put(EBakaLDAPAttributes.LOGIN.attribute(), upn.split("@")[0]);
        entry.put(EBakaLDAPAttributes.UAC.attribute(), uac);
        entry.put(EBakaLDAPAttributes.MAIL.attribute(), upn);
        entry.put(EBakaLDAPAttributes.EXT02.attribute(), "FALSE");
        entry.put(EBakaLDAPAttributes.TITLE.attribute(), "Žák");
        return entry;
    }
}
