package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
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
 * Testy pro AccountServiceImpl.
 */
@ExtendWith(MockitoExtension.class)
class AccountServiceImplTest {

    @Mock
    private LDAPUserRepository ldapRepo;

    private AccountServiceImpl service;

    /** Normální účet: NORMAL_ACCOUNT (512). */
    private static final int UAC_NORMAL = EBakaUAC.NORMAL_ACCOUNT.value();

    /** Zakázaný účet: NORMAL_ACCOUNT | ACCOUNTDISABLE (514). */
    private static final int UAC_DISABLED = EBakaUAC.NORMAL_ACCOUNT.value() | EBakaUAC.ACCOUNTDISABLE.value();

    @BeforeEach
    void setUp() {
        service = new AccountServiceImpl(ldapRepo);
    }

    @Test
    void suspendAccount_normalniUcet_zakazeHo() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.UAC), eq("514")))
                .thenReturn(true);

        SyncResult result = service.suspendAccount("CN=Test,OU=Users", UAC_NORMAL);

        assertTrue(result.isSuccess());
        assertEquals(SyncResult.Type.UPDATED, result.getType());
        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.UAC, "514");
    }

    @Test
    void suspendAccount_jizZakazanyUcet_bezZmeny() {
        SyncResult result = service.suspendAccount("CN=Test,OU=Users", UAC_DISABLED);

        assertEquals(SyncResult.Type.NO_CHANGE, result.getType());
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
    }

    @Test
    void suspendAccount_selzeLdap_vratiChybu() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.UAC), anyString()))
                .thenReturn(false);

        SyncResult result = service.suspendAccount("CN=Test,OU=Users", UAC_NORMAL);

        assertFalse(result.isSuccess());
        assertEquals(SyncResult.Type.ERROR, result.getType());
    }

    @Test
    void unsuspendAccount_zakazanyUcet_povoliHo() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.UAC), eq("512")))
                .thenReturn(true);

        SyncResult result = service.unsuspendAccount("CN=Test,OU=Users", UAC_DISABLED);

        assertTrue(result.isSuccess());
        assertEquals(SyncResult.Type.UPDATED, result.getType());
        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.UAC, "512");
    }

    @Test
    void unsuspendAccount_jizPovolenyUcet_bezZmeny() {
        SyncResult result = service.unsuspendAccount("CN=Test,OU=Users", UAC_NORMAL);

        assertEquals(SyncResult.Type.NO_CHANGE, result.getType());
        verify(ldapRepo, never()).updateAttribute(anyString(), any(), anyString());
    }

    @Test
    void unsuspendAccount_selzeLdap_vratiChybu() {
        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.UAC), anyString()))
                .thenReturn(false);

        SyncResult result = service.unsuspendAccount("CN=Test,OU=Users", UAC_DISABLED);

        assertFalse(result.isSuccess());
        assertEquals(SyncResult.Type.ERROR, result.getType());
    }

    @Test
    void suspendAccount_zachovaDalsiPriznakyUAC() {
        // účet s DONT_EXPIRE_PASSWORD (66048 = 0x10200)
        int uacWithNoExpire = EBakaUAC.NORMAL_ACCOUNT.value() | EBakaUAC.DONT_EXPIRE_PASSWORD.value();
        int expectedUac = uacWithNoExpire | EBakaUAC.ACCOUNTDISABLE.value();

        when(ldapRepo.updateAttribute(anyString(), eq(EBakaLDAPAttributes.UAC), eq(Integer.toString(expectedUac))))
                .thenReturn(true);

        SyncResult result = service.suspendAccount("CN=Test,OU=Users", uacWithNoExpire);

        assertTrue(result.isSuccess());
        verify(ldapRepo).updateAttribute("CN=Test,OU=Users", EBakaLDAPAttributes.UAC, Integer.toString(expectedUac));
    }
}
