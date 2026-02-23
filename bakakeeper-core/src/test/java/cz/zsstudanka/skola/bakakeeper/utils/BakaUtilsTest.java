package cz.zsstudanka.skola.bakakeeper.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Testy pro manipulační jednotku BakaUtils.
 *
 * @author Jan Hladěna
 */
class BakaUtilsTest {

    static final String DOMAIN = "test.org";

    @Test
    void createSAMloginfromName() {
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
    void createUPNfromName() {
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
    void fileBaseName() {
        assertEquals("test.txt", BakaUtils.fileBaseName("./test.txt"));
        assertEquals("test.1A.pdf", BakaUtils.fileBaseName("/složka/adresář/test.1A.pdf"));
    }

    @Test
    void cnFromDn() {
        assertEquals("Kocour Ušatý", BakaUtils.parseCN("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals(null, BakaUtils.parseCN("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

    @Test
    void baseFromDn() {
        assertEquals("OU=Kočky,OU=Zvířata,DC=test,DC=org", BakaUtils.parseBase("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals("OU=Zvířata,DC=test,DC=org", BakaUtils.parseBase("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

    @Test
    void ouFromDn() {
        assertEquals("Kočky", BakaUtils.parseLastOU("CN=Kocour Ušatý,OU=Kočky,OU=Zvířata,DC=test,DC=org"));
        assertEquals("Kočky", BakaUtils.parseLastOU("OU=Kočky,OU=Zvířata,DC=test,DC=org"));
    }

    @Test
    void createInitialPassword() {
        int year = BakaUtils.getCurrentClassYear() % 100;

        assertEquals("No.Ja." + String.format("%02d", year) + "0184", BakaUtils.createInitialPassword("Novák", "Jakub", 2, 1));
        assertEquals("Pr.Sa." + String.format("%02d", year) + "3107", BakaUtils.createInitialPassword("Příkopová", "Šárka Sofie", 3, 27));
        assertEquals("Pi.Vo." + String.format("%02d", year) + "2709", BakaUtils.createInitialPassword("Pivoňka", "Vojtěch", 9, 11));
        assertEquals("Vi.Le." + String.format("%02d", year) + "1276", BakaUtils.createInitialPassword("Da Vinci", "Leonardo", 8, 3));
    }

    @Test
    void nextPassword() {
        int year = BakaUtils.getCurrentClassYear() % 100;

        assertEquals("No.Ja." + String.format("%02d", year) + "0184", BakaUtils.nextPassword("Novák", "Jakub", 2, 1, 0));
        assertEquals("Pi.Vo." + String.format("%02d", year) + "0411", BakaUtils.nextPassword("Pivoňka", "Vojtěch", 9, 11, 3));
    }

    @Test
    void createSAMloginFromUPNbase() {
        assertEquals("novak.adam", BakaUtils.createSAMloginFromUPNbase("Novák", "Adam", "novak.adam" + DOMAIN));
        assertEquals("novak.adam1", BakaUtils.createSAMloginFromUPNbase("Novák", "Adam", "novak.adam1" + DOMAIN));
        assertEquals("novak.adam37", BakaUtils.createSAMloginFromUPNbase("Novák", "Adam", "novak.adam37" + DOMAIN));
    }

    @Test
    void nextDN() {
        assertEquals("CN=Novák Adam 01,OU=Skupina,DC=skola,DC=local", BakaUtils.nextDN("CN=Novák Adam,OU=Skupina,DC=skola,DC=local"));
        assertEquals("CN=Novák Adam 06,OU=Skupina,DC=skola,DC=local", BakaUtils.nextDN("CN=Novák Adam 05,OU=Skupina,DC=skola,DC=local"));
        assertEquals("CN=Novák Adam 27,OU=Skupina,DC=skola,DC=local", BakaUtils.nextDN("CN=Novák Adam 26,OU=Skupina,DC=skola,DC=local"));
    }

    @Test
    void classStringFromDN() {
        assertEquals("1.A", BakaUtils.classStringFromDN("CN=Malý Vojta,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void validateEmail() {
        assertEquals("kocour.v.botach@domena.org", BakaUtils.validateEmail("kocour.v.botach@domena.org"));
        assertEquals("mail@zs-kocourkov.cz", BakaUtils.validateEmail("mail@zs-kocourkov.cz"));
        assertEquals("a-b-c-d@zs-kocourkov.cz", BakaUtils.validateEmail("a-b-c-d@zs-kocourkov.cz"));
        assertEquals("", BakaUtils.validateEmail("mail1@dom1.cz, mail2@dom2.cz"));
        assertEquals("", BakaUtils.validateEmail("abcdefgh"));
        assertEquals("", BakaUtils.validateEmail("konik.@konikov.tld"));
    }

    @Test
    void createWebAppLoginFromName() {
        String match = "[A-Z]{1}[a-z]{4}[0-9]{5}";
        assertTrue(BakaUtils.createWebAppLoginFromName("Kocour", "Mikeš").matches(match));
        assertTrue(BakaUtils.createWebAppLoginFromName("Thi", "Li Ka").matches(match));
        assertTrue(BakaUtils.createWebAppLoginFromName("Šňůrka Tkanička", "Tibor Petr").matches(match));
    }

    // --- rozšířené safety-net testy ---

    @Test
    void mailIsValid() {
        assertTrue(BakaUtils.mailIsValid("novak@skola.cz"));
        assertTrue(BakaUtils.mailIsValid("")); // prázdný řetězec je validní (= žádný mail)
        assertFalse(BakaUtils.mailIsValid("neplatny"));
        assertFalse(BakaUtils.mailIsValid("a@b.c, d@e.f"));
    }

    @Test
    void validateEmailNull() {
        assertEquals("", BakaUtils.validateEmail(null));
    }

    @Test
    void validatePhone() {
        assertEquals("123456789", BakaUtils.validatePhone("123456789"));
        assertEquals("123456789", BakaUtils.validatePhone("123 456 789"));
        assertEquals("", BakaUtils.validatePhone("12345")); // příliš krátké
        assertEquals("", BakaUtils.validatePhone("+420123456789")); // se předvolbou
        assertEquals("", BakaUtils.validatePhone(null));
        assertEquals("", BakaUtils.validatePhone(""));
    }

    @Test
    void removeAccents() {
        assertEquals("Prilis zlutoucky kun upel dabelske ody.",
                BakaUtils.removeAccents("Příliš žluťoučký kůň úpěl ďábelské ódy."));
        assertEquals("Novak", BakaUtils.removeAccents("Novák"));
        assertEquals("Ciperna", BakaUtils.removeAccents("Čiperná"));
    }

    @Test
    void classYearFromDn() {
        assertEquals(1, BakaUtils.classYearFromDn("CN=Malý Vojta,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Skola,DC=skola,DC=local"));
        assertEquals(9, BakaUtils.classYearFromDn("CN=Novák Adam,OU=Trida-B,OU=Rocnik-9,OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void classLetterFromDn() {
        assertEquals("A", BakaUtils.classLetterFromDn("CN=Malý Vojta,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Skola,DC=skola,DC=local"));
        assertEquals("C", BakaUtils.classLetterFromDn("CN=Novák Adam,OU=Trida-C,OU=Rocnik-5,OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void parseLastOU() {
        assertEquals("Zaci", BakaUtils.parseLastOU("OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void parseBase() {
        // DN bez CN
        assertEquals("OU=Skola,DC=skola,DC=local", BakaUtils.parseBase("OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void parseCNnull() {
        // DN bez CN vrací null
        assertNull(BakaUtils.parseCN("OU=Zaci,OU=Skola,DC=skola,DC=local"));
    }

    @Test
    void getCurrentClassYear() {
        // školní rok začíná v září, končí v srpnu – vrací rok začátku
        int year = BakaUtils.getCurrentClassYear();
        assertTrue(year >= 2020 && year <= 2100);
    }
}
