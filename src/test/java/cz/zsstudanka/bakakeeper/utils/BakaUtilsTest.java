package cz.zsstudanka.bakakeeper.utils;

import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Testy pro manipulační jednotku BakaUtils.
 *
 * (c) Jan Hladěna
 */
public class BakaUtilsTest {

    static final String DOMAIN = "test.org";

    @Test
    public void createSAMloginfromName() {
        assertEquals("novak.tomas", BakaUtils.createSAMloginFromName("Novák", "Tomáš"));
        assertEquals("janikovic.milisalek", BakaUtils.createSAMloginFromName("Janíkovič", "Milisálek Kociáš"));
        assertEquals("vesela.marie", BakaUtils.createSAMloginFromName("Veselá Smutná", "Marie Tonička Eliška"));
        assertEquals("kocino.juan", BakaUtils.createSAMloginFromName("Kocino Čertino", "Juan Carlos"));
        assertEquals("nguyen.linh", BakaUtils.createSAMloginFromName("Nguyen Thi Dieu", "Linh"));
        assertEquals("podnikatelska.viktor", BakaUtils.createSAMloginFromName("Podnikatelská", "Viktorie"));
        assertEquals("podnikatelska.vikto1", BakaUtils.createSAMloginFromName("Podnikatelská", "Viktorie", 1));
        assertEquals("podnikatelska.vikt10", BakaUtils.createSAMloginFromName("Podnikatelská", "Viktorie", 10));
        assertEquals("schwarzwaldova.anas7", BakaUtils.createSAMloginFromName("Schwarzwaldová", "Anastázie", 7));
        assertEquals("vonlanditz.kristen", BakaUtils.createSAMloginFromName("von Landitz", "Kristen"));
        assertEquals("vanhouten.milhouse", BakaUtils.createSAMloginFromName("Van Houten", "Milhouse Mussolini"));
        assertEquals("ciperna.sarah", BakaUtils.createSAMloginFromName("Čiperná", "Sarah-Vanessa"));
        assertEquals("ciperna.sarah", BakaUtils.createSAMloginFromName("Čiperná", "Sarah - Vanessa"));
        assertEquals("ciperna.sarah", BakaUtils.createSAMloginFromName("Čiperná", "Sarah- Vanessa"));
        assertEquals("ciperna.sarah", BakaUtils.createSAMloginFromName("Čiperná", "Sarah -Vanessa"));
        assertEquals("vesela.julie2", BakaUtils.createSAMloginFromName("Veselá-Smutná", "Julie Magdaléna", 2));
        assertEquals("davinci.leonardo", BakaUtils.createSAMloginFromName("Da Vinci", "Leonardo"));
        assertEquals("davinci.leonardo", BakaUtils.createSAMloginFromName("Da-Vinci", "Leonardo"));
    }

    @Test
    public void createUPNfromName() {
        assertEquals("novak.tomas@test.org", BakaUtils.createUPNfromName("Novák", "Tomáš", DOMAIN));
        assertEquals("janikovic.milisalek@test.org", BakaUtils.createUPNfromName("Janíkovič", "Milisálek Kociáš", DOMAIN));
        assertEquals("vesela.marie@test.org", BakaUtils.createUPNfromName("Veselá Smutná", "Marie Tonička Eliška", DOMAIN));
        assertEquals("kocino.juan@test.org", BakaUtils.createUPNfromName("Kocino Čertino", "Juan Carlos", DOMAIN));
        assertEquals("nguyen.linh@test.org", BakaUtils.createUPNfromName("Nguyen Thi Dieu", "Linh", DOMAIN));
        assertEquals("podnikatelska.viktorie@test.org", BakaUtils.createUPNfromName("Podnikatelská", "Viktorie", DOMAIN));
        assertEquals("podnikatelska.viktorie1@test.org", BakaUtils.createUPNfromName("Podnikatelská", "Viktorie", DOMAIN, 1));
        assertEquals("schwarzwaldova.anastazie7@test.org", BakaUtils.createUPNfromName("Schwarzwaldová", "Anastázie", DOMAIN, 7));
        assertEquals("vonlanditz.kristen@test.org", BakaUtils.createUPNfromName("von Landitz", "Kristen", DOMAIN));
        assertEquals("vanhouten.milhouse@test.org", BakaUtils.createUPNfromName("Van Houten", "Milhouse Mussolini", DOMAIN));
        assertEquals("ciperna.sarah@test.org", BakaUtils.createUPNfromName("Čiperná", "Sarah-Vanessa", DOMAIN));
        assertEquals("ciperna.sarah@test.org", BakaUtils.createUPNfromName("Čiperná", "Sarah - Vanessa", DOMAIN));
        assertEquals("ciperna.sarah@test.org", BakaUtils.createUPNfromName("Čiperná", "Sarah- Vanessa", DOMAIN));
        assertEquals("ciperna.sarah@test.org", BakaUtils.createUPNfromName("Čiperná", "Sarah -Vanessa", DOMAIN));
        assertEquals("vesela.julie2@test.org", BakaUtils.createUPNfromName("Veselá-Smutná", "Julie Magdaléna", DOMAIN, 2));
        assertEquals("davinci.leonardo@test.org", BakaUtils.createUPNfromName("Da Vinci", "Leonardo", DOMAIN));
        assertEquals("davinci.leonardo@test.org", BakaUtils.createUPNfromName("Da-Vinci", "Leonardo", DOMAIN));
    }

    @Test
    public void fileBaseName() {
        assertEquals("test.txt", BakaUtils.fileBaseName("./test.txt"));
        assertEquals("test.1A.pdf", BakaUtils.fileBaseName("/složka/adresář/test.1A.pdf"));
    }

    @Test
    public void cnFromDn() {
        assertEquals("Kocour Ušatý", BakaUtils.parseCN("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals(null, BakaUtils.parseCN("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

    @Test
    public void baseFromDn() {
        assertEquals("OU=Kočky,OU=Zvířata,DC=test,DC=org", BakaUtils.parseBase("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals("OU=Zvířata,DC=test,DC=org", BakaUtils.parseBase("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

    @Test
    public void ouFromDn() {
        assertEquals("Kočky", BakaUtils.parseLastOU("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals("Kočky", BakaUtils.parseLastOU("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

}
