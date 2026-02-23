package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaGuardianRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro BakaGuardianRepository.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class BakaGuardianRepositoryTest {

    @Mock LDAPConnector ldap;

    private BakaGuardianRepository repo;

    @BeforeEach
    void setUp() {
        repo = new BakaGuardianRepository(ldap);
    }

    @Test
    void findAllContacts() {
        Map<Integer, Map<String, String>> raw = new LinkedHashMap<>();
        raw.put(0, createContactEntry("99001", "Nováková", "Jana", "novakova@email.cz", "777888999"));

        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class))).thenReturn(raw);

        List<GuardianRecord> result = repo.findAllContacts("OU=Kontakty");

        assertEquals(1, result.size());
        assertEquals("99001", result.get(0).getInternalId());
        assertEquals("Nováková", result.get(0).getSurname());
        assertEquals("novakova@email.cz", result.get(0).getEmail());
        assertEquals("777888999", result.get(0).getPhone());
        assertTrue(result.get(0).isGalHidden());
        assertTrue(result.get(0).isRequireAuth());
    }

    @Test
    void findByInternalId() {
        Map<Integer, Map<String, String>> raw = new LinkedHashMap<>();
        raw.put(0, createContactEntry("99001", "Nováková", "Jana", "novakova@email.cz", "777888999"));
        raw.put(1, createContactEntry("99002", "Dvořáková", "Marie", "dvorakova@email.cz", "666777888"));

        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class))).thenReturn(raw);

        GuardianRecord found = repo.findByInternalId("OU=Kontakty", "99002");
        assertNotNull(found);
        assertEquals("Dvořáková", found.getSurname());
    }

    @Test
    void findByInternalIdNotFound() {
        when(ldap.getObjectInfo(anyString(), any(HashMap.class), any(String[].class)))
                .thenReturn(new LinkedHashMap<>());

        assertNull(repo.findByInternalId("OU=Kontakty", "neexistuje"));
    }

    @Test
    void deleteContactDelegates() {
        when(ldap.deleteContact("CN=Test")).thenReturn(true);
        assertTrue(repo.deleteContact("CN=Test"));
    }

    @Test
    void listMembershipDelegates() {
        ArrayList<String> groups = new ArrayList<>(List.of("CN=Skupina1", "CN=Skupina2"));
        when(ldap.listMembership("CN=Test")).thenReturn(groups);

        List<String> result = repo.listMembership("CN=Test");
        assertEquals(2, result.size());
    }

    @Test
    void listMembershipNull() {
        when(ldap.listMembership("CN=Test")).thenReturn(null);
        List<String> result = repo.listMembership("CN=Test");
        assertTrue(result.isEmpty());
    }

    @Test
    void addToGroupDelegates() {
        when(ldap.addObjectToGroup("CN=Test", "CN=Skupina")).thenReturn(true);
        assertTrue(repo.addToGroup("CN=Test", "CN=Skupina"));
    }

    @Test
    void removeFromAllGroupsDelegates() {
        when(ldap.removeObjectFromAllGroups("CN=Test")).thenReturn(true);
        assertTrue(repo.removeFromAllGroups("CN=Test"));
    }

    // --- pomocná metoda ---

    private Map<String, String> createContactEntry(String id, String surname, String givenName,
                                                    String email, String phone) {
        Map<String, String> entry = new HashMap<>();
        entry.put(EBakaLDAPAttributes.EXT01.attribute(), id);
        entry.put(EBakaLDAPAttributes.NAME_LAST.attribute(), surname);
        entry.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), givenName);
        entry.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), surname + " " + givenName);
        entry.put(EBakaLDAPAttributes.MAIL.attribute(), email);
        entry.put(EBakaLDAPAttributes.MOBILE.attribute(), phone);
        entry.put(EBakaLDAPAttributes.DN.attribute(), "CN=" + surname + " " + givenName + ",OU=Kontakty,DC=skola,DC=local");
        entry.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), "TRUE");
        entry.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), "TRUE");
        return entry;
    }
}
