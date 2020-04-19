package cz.zsstudanka.skola.bakakeeper.utils;

import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Base64;

/**
 * Nástroje pro úpravu dat.
 *
 * @author Jan Hladěna
 */
public class BakaUtils {

    /**
     * Validace a úprava e-mailové adresy.
     * TODO doplnit
     *
     * @param email mailová adresa k validaci
     * @return validovaná adresa nebo null
     */
    public static String validateEmail(String email) {
        // regexp - nebo null
        // TODO validace
        return email.trim();
    }

    /**
     * Validace a úprava telefonního čísla.
     * TODO doplnit
     *
     * @param phone telefonní číslo
     * @return upravené telefonní číslo nebo null TODO ?
     */
    public static String validatePhone(String phone) {

        if (phone == null) return "";
        // TODO validace


        String basePhone = phone.replace(" ", "").trim();

        return (basePhone.length() > 0) ? basePhone : "";
    }

    /**
     * Odstranění diakritiky.
     *
     * @param input vstupní řetězec
     * @return výstup bez diakritiky
     */
    public static String removeAccents(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }

    /**
     *
     * @param input
     * @return
     *
     * @deprecated
     */
    public static Boolean parseSkolniRok(String input) {
        boolean check = true;

        // 2019/20
        if (input.length() != 7) {
            check = false;
        }

        if (!input.contains("/")) {
            check = false;
        }

        return check;
    }

    /**
     * MD5 hash vstupního řetězce
     *
     * @param input vstupní řetězec
     * @return MD5 hash velkými písmeny
     * @deprecated
     */
    public static String hashMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] messageDigest = md.digest(input.getBytes());
            BigInteger no = new BigInteger(1, messageDigest);

            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext.toUpperCase();

        } catch (NoSuchAlgorithmException e) {
            // TODO
        }

        return "";
    }

    /**
     * B64 zakódování
     *
     * @param input vstupní řetězec
     * @return b64 kódovaný výstup
     * @deprecated
     */
    public static String base64encode(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes());
    }

    /**
     * B64 dekódování.
     *
     * @param b64input b64-vstupní řetězec
     * @return dekódovaný výstup
     * @deprecated
     */
    public static String base64decode(String b64input) {
        return Base64.getDecoder().decode(b64input.getBytes()).toString();
    }

    /**
     * Vytvoří neověřený 4+2-INDOŠ login z celého jména.
     *
     * @param displayName zobrazované jméno (Příjmení Jméno Jméno, Svobodová Nováková Jana Kateřina)
     * @return login 4+2 (svobja)
     *
     * @deprecated
     */
    public static String create4p2(String displayName) {
        StringBuilder login = new StringBuilder();

        String[] parts = BakaUtils.removeAccents(displayName.toLowerCase()).split(" ");

        if (parts.length == 1 || parts[0].length() < 4 || displayName.contains("-")) {
            login.append(parts[0]);
        } else {
            login.append((parts[0]).substring(0, 4));
            login.append((parts[1]).substring(0, 2)); // pouze první jméno
        }

        return login.toString();
    }

    /**
     * Vytvoří neověřený plný login z celého jména.
     *
     * @param displayName zobrazované jméno (Příjmení Jméno Jméno)
     * @return plný login
     *
     * @deprecated
     */
    public static String createFullLogin(String displayName) {

        final String PREFIX = "x"; // prefix pro speciální případy?

        StringBuilder login = new StringBuilder();

        String[] parts = BakaUtils.removeAccents(displayName.toLowerCase()).split(" ");

        if (parts.length <= 2) {
            login.append(PREFIX);
            login.append(parts[0]);
        } else {
            login.append(parts[0]);
            login.append(".");
            login.append(parts[1]);
        }

        return login.toString();
    }

    /**
     * Vytvoří neověřený tvar loginu ze jména a příjmení.
     *
     * @param prijmeni celé příjmení (Svobodová Nováková)
     * @param jmeno celé křestní jméno (Jana Kateřina)
     * @return login ve tvaru prijmeni.jmeno (svobodova.katerina)
     *
     * @deprecated
     */
    public static String createFullLogin(String prijmeni, String jmeno) {
        return createFullLogin(prijmeni + " " + jmeno);
    }

    /**
     * Na základě loginu vytvoří tvar e-mailové adresy.
     *
     * @param login očekává se tvar svobodova.katerina nebo svobodova.katerina1
     * @return e-mailová adresa
     *
     * @deprecated
     */
    public static String createFullMail(String login) {

        if (!login.contains(".")) return login + "@" + Settings.getInstance().getMailDomain();

        return login.split("\\.")[1] + "." + login.split("\\.")[0] + "@" + Settings.getInstance().getMailDomain();
    }

    /**
     * TODO 2020-04-08 nové -- je potřeba přepsat pro SURNAME, GIVEN_NAME
     * TODO popis metody
     * TODO napsat testy
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param attempt číslo pokusu
     * @return
     */
    public static String createUPNfromName(String surname, String givenName, String domain, Integer attempt) {

        String[] snParts = surname.replace("-", " ")
                .replaceAll("\\s+", " ")
                .replaceFirst("^(v|V)(a|o)n ", "$1$2n")
                .replaceFirst("^(d|D)(a|e|i) ", "$1$2")
                .replaceFirst("^(a|A)l ", "$1l")
                .split(" ");

        String[] gnParts = givenName.replace("-", " ")
                .replaceAll("\\s+", " ")
                .split(" ");

        return removeAccents(snParts[0]).toLowerCase() + "." + removeAccents(gnParts[0]).toLowerCase() + ((attempt == 0) ? "" : attempt.toString()) + "@" + domain;
    }

    public static String createUPNfromName(String surname, String givenName, String domain) {
        return createUPNfromName(surname, givenName, domain, 0);
    }

    /**
     * TODO 2020-04-08 popis metody
     * TODO napsat testy
     *
     * @param surname
     * @param givenName
     * @param attempt
     * @return
     */
    public static String createSAMloginFromName(String surname, String givenName, Integer attempt) {

        final int MAX_LIMIT = 20;

        String[] snParts = surname.replace("-", " ")
                .replaceAll("\\s+", " ")
                .replaceFirst("^(v|V)(a|o)n ", "$1$2n")
                .replaceFirst("^(d|D)(a|e|i) ", "$1$2")
                .replaceFirst("^(a|A)l ", "$1l")
                .split(" ");

        String[] gnParts = givenName.replace("-", " ")
                .replaceAll("\\s+", " ")
                .split(" ");

        String base = removeAccents(snParts[0]).toLowerCase() + "." + removeAccents(gnParts[0]).toLowerCase();

        if (base.length() <= MAX_LIMIT - ((attempt == 0) ? 0 : String.format("%d", attempt).length())) {
            return base + ((attempt == 0) ? "" : attempt.toString());
        } else {
            return (base.substring(0, MAX_LIMIT - ((attempt == 0) ? 0 : String.format("%d", attempt).length()))) + ((attempt == 0) ? "" : String.format("%d", attempt));
        }
    }

    public static String createSAMloginFromName(String surname, String givenName) {
        return createSAMloginFromName(surname, givenName, 0);
    }

    /**
     * Bázové jméno souboru.
     *
     * @param path cesta k souboru
     * @return jednoduchý tvar jména
     */
    public static String fileBaseName(String path) {
        String[] parts = path.split(".+?/(?=[^/]+$)");
        return parts[parts.length - 1];
    }

    /**
     * Třída v textovém tvaru "1.B" z plné cesty DN v Active Directory.
     * TODO jestli je to vůbec ještě potřeba?
     * TODO překlad o angličtiny
     * TODO případně ještě inverzní funkce DN do třídy
     *
     * @param dn DN ve tvaru CN=Novák Jan,OU=Trida-E,OU=Rocnik-2,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zs,DC=local
     * @return textové označení třídy
     */
    public static String tridaFromDN(String dn) {

        if (dn.contains("Trida-") && dn.contains("Rocnik-")) {

            String[] ous = dn.split(",");

            String pismeno = ous[1].replace("OU=Trida-", "");
            String rocnik = ous[2].replace("OU=Rocnik-", "");

            return (rocnik + "." + pismeno).toUpperCase();
        }

        return null;
    }

}
