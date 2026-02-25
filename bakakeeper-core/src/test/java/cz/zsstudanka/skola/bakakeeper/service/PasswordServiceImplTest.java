package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro PasswordServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class PasswordServiceImplTest {

    @Mock
    private LDAPUserRepository ldapRepo;

    private PasswordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PasswordServiceImpl(ldapRepo);
    }

    @Test
    void setPassword_zapisHesloALastSet() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(true);
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_LASTSET), anyString()))
                .thenReturn(true);

        boolean result = service.setPassword("CN=Test,OU=Users", "Heslo123", false);

        assertTrue(result);
        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.PW_UNICODE, "Heslo123");
        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.PW_LASTSET, "-1");
    }

    @Test
    void setPassword_mustChange_nastaviLastSet0() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(true);

        service.setPassword("CN=Test,OU=Users", "Heslo123", true);

        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.PW_LASTSET, "0");
    }

    @Test
    void setPassword_selzeLiZapisHesla_vratiFalse() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(false);

        boolean result = service.setPassword("CN=Test,OU=Users", "Heslo123", false);

        assertFalse(result);
        verify(ldapRepo, never()).updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_LASTSET), anyString());
    }

    @Test
    void resetStudentPassword_uspech() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(true);
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_LASTSET), anyString()))
                .thenReturn(true);

        SyncResult result = service.resetStudentPassword("CN=Novak,OU=Users", "Novák", "Jan", 5, 1);

        assertTrue(result.isSuccess());
        assertEquals(SyncResult.Type.UPDATED, result.getType());
    }

    @Test
    void resetStudentPassword_vsechnyPokusySelzou() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(false);

        SyncResult result = service.resetStudentPassword("CN=Novak,OU=Users", "Novák", "Jan", 5, 1);

        assertFalse(result.isSuccess());
        assertEquals(SyncResult.Type.ERROR, result.getType());
    }

    @Test
    void resetStudentPasswordWithResult_uspech_vratiHeslo() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(true);
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_LASTSET), anyString()))
                .thenReturn(true);

        PasswordResetResult result = service.resetStudentPasswordWithResult(
                "CN=Novak,OU=Users", "Novák", "Jan", 5, 1);

        assertTrue(result.isSuccess());
        assertNotNull(result.password());
        assertFalse(result.password().isEmpty());
    }

    @Test
    void resetStudentPasswordWithResult_selhani_vratiNullHeslo() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.PW_UNICODE), anyString()))
                .thenReturn(false);

        PasswordResetResult result = service.resetStudentPasswordWithResult(
                "CN=Novak,OU=Users", "Novák", "Jan", 5, 1);

        assertFalse(result.isSuccess());
        assertNull(result.password());
    }
}
