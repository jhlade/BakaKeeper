package cz.zsstudanka.skola.bakakeeper.constants;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro operace s UAC příznaky (UserAccountControl).
 *
 * @author Jan Hladěna
 */
class EBakaUACTest {

    // běžný aktivní účet: NORMAL_ACCOUNT (0x0200) = 512
    static final int NORMAL = 512;

    // zakázaný účet: NORMAL_ACCOUNT + ACCOUNTDISABLE = 514
    static final int DISABLED = 514;

    // účet s nevypršujícím heslem: NORMAL_ACCOUNT + DONT_EXPIRE_PASSWORD = 66048
    static final int NO_EXPIRE = 66048;

    @Test
    void checkFlag() {
        assertTrue(EBakaUAC.NORMAL_ACCOUNT.checkFlag(NORMAL));
        assertFalse(EBakaUAC.ACCOUNTDISABLE.checkFlag(NORMAL));

        assertTrue(EBakaUAC.ACCOUNTDISABLE.checkFlag(DISABLED));
        assertTrue(EBakaUAC.NORMAL_ACCOUNT.checkFlag(DISABLED));

        assertTrue(EBakaUAC.DONT_EXPIRE_PASSWORD.checkFlag(NO_EXPIRE));
        assertFalse(EBakaUAC.ACCOUNTDISABLE.checkFlag(NO_EXPIRE));
    }

    @Test
    void checkFlagString() {
        assertTrue(EBakaUAC.NORMAL_ACCOUNT.checkFlag("512"));
        assertTrue(EBakaUAC.ACCOUNTDISABLE.checkFlag("514"));
    }

    @Test
    void setFlag() {
        // nastavení ACCOUNTDISABLE na normální účet → 514
        assertEquals(DISABLED, EBakaUAC.ACCOUNTDISABLE.setFlag(NORMAL));

        // nastavení DONT_EXPIRE_PASSWORD na normální účet → 66048
        assertEquals(NO_EXPIRE, EBakaUAC.DONT_EXPIRE_PASSWORD.setFlag(NORMAL));

        // idempotentní: nastavení již nastaveného příznaku nic nezmění
        assertEquals(DISABLED, EBakaUAC.ACCOUNTDISABLE.setFlag(DISABLED));
    }

    @Test
    void setFlagString() {
        assertEquals(DISABLED, EBakaUAC.ACCOUNTDISABLE.setFlag("512"));
    }

    @Test
    void clearFlag() {
        // odebrání ACCOUNTDISABLE z disabled účtu → normální účet
        assertEquals(NORMAL, EBakaUAC.ACCOUNTDISABLE.clearFlag(DISABLED));

        // odebrání DONT_EXPIRE_PASSWORD → normální účet
        assertEquals(NORMAL, EBakaUAC.DONT_EXPIRE_PASSWORD.clearFlag(NO_EXPIRE));

        // idempotentní: odebrání nenastaveného příznaku nic nezmění
        assertEquals(NORMAL, EBakaUAC.ACCOUNTDISABLE.clearFlag(NORMAL));
    }

    @Test
    void clearFlagString() {
        assertEquals(NORMAL, EBakaUAC.ACCOUNTDISABLE.clearFlag("514"));
    }

    @Test
    void valueAndToString() {
        assertEquals(0x0200, EBakaUAC.NORMAL_ACCOUNT.value());
        assertEquals(0x0002, EBakaUAC.ACCOUNTDISABLE.value());
        assertEquals("512", EBakaUAC.NORMAL_ACCOUNT.toString());
        assertEquals("2", EBakaUAC.ACCOUNTDISABLE.toString());
    }
}
