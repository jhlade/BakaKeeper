package cz.zsstudanka.skola.bakakeeper.utils;

import java.text.Normalizer;
import java.util.Base64;

/**
 * Pomocné statické nástroje pro úpravu dat.
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
     * Zakódování vstupu do Base64.
     *
     * @param input vstupní řetězec
     * @return b64 kódovaný výstup
     */
    public static String base64encode(String input) {
        return Base64.getEncoder().encodeToString(input.getBytes());
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
     * Vytvoření historického loginu ve formátu 4+2 ("prijjm") jako v projektu INDOŠ z roku 2001.
     * Použitelné pro vyučující. Číslo pokusu identifikuje posun písmen na pozici [3].
     * TODO - refaktor create4p2
     *
     * @param surname celé příjmení
     * @param givenName celé prnví jméno
     * @param attempt číslpo pokusu
     * @return histroická forma loginu ve tvaru 4+2
     */
    public static String createLegacyLogin(String surname, String givenName, Integer attempt) {
        // TODO
        return null;
    }

    /**
     * Vytvoření plného UPN pro zadané jméno, příjmení a číslo pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param domain doména
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

    /**
     * Vytvoření plného UPN pro zadané jméno, příjmení v prvním pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param domain doména
     * @return UPN v prvním pokusu
     */
    public static String createUPNfromName(String surname, String givenName, String domain) {
        return createUPNfromName(surname, givenName, domain, 0);
    }

    /**
     * Vytvoření pre-Windows 2000 přihlašovacího jména (do 20 znaků) na základě
     * příjmení, jména a čísla pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param attempt číslo pokusu
     * @return pre-Windows 2000 login (sAMAccountName)
     */
    public static String createSAMloginFromName(String surname, String givenName, Integer attempt) {

        // maximální limit délky řetězce hodnoty atributu sAMAccountNate v AD
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

    /**
     * Vytvoření pre-Windows 2000 přihlašovacího jména (do 20 znaků) na základě
     * příjmení a jména v prvním pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @return pre-Windows 2000 login (sAMAccountName) v prvním pokusu
     */
    public static String createSAMloginFromName(String surname, String givenName) {
        return createSAMloginFromName(surname, givenName, 0);
    }

    /**
     * Vytvoří počáteční heslo žáka na základě jeho příjmení, jména a čísla v třídním výkazu
     * ve formátu Pr.Jm.##
     *
     * @param surname příjmení žáka
     * @param givenName jméno žáka
     * @param classID číslo v třídním výkazu
     * @return počáteční heslo
     */
    public static String createInitialPassword(String surname, String givenName, Integer classID) {
        return BakaUtils.removeAccents(surname
                .replace("-", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^(d|D)(a|e|i) ", "")
                .replaceAll("(v|V)(a|o)n ", "")
                .replaceAll("(a|A)l ", "")
                .substring(0, 2))
                + "."
                + BakaUtils.removeAccents(givenName.substring(0, 2))
                + "."
                + String.format("%02d", classID);
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
     * Zpracování DN objektu pro identifikaci zařazení do školní třídy.
     * Pokud objekt není aktivním žákem (nachází se mimo OU pro žáky),
     * je vráceno pole prázdných položek.
     *
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => String[2] = {"1", "A"}
     *
     * @param dn celé DN objektu
     * @return pole řetězců ve tvaru {ročník, písmeno třídy}
     */
    private static String[] classFromDn(String dn) {
        // null[2]
        String[] result = new String[2];

        if (dn.contains("Trida-") && dn.contains("Rocnik-")) {

            // 0 = CN, 1 = Trida, 2 = Rocnik, 3 = Zaci, ...
            String[] ous = dn.split(",");

            result[0] = ous[1].replace("OU=Trida-", "");
            result[1] = ous[2].replace("OU=Rocnik-", "").toUpperCase();
        }

        return result;
    }

    /**
     * Číslo ročníku žáka na základě jeho DN, které musí obsahovat
     * odpovídající OU.
     *
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => 1
     *
     * @param dn plné DN žáka
     * @return číslo ročníku
     */
    public static Integer classYearFromDn(String dn) {
        return Integer.parseInt(classFromDn(dn)[1]);
    }

    /**
     * Písmeno třídy žáka na základě jeho DN, které musí obsahovat
     * odpovídající OU.
     *
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => A
     *
     * @param dn plné DN žáka
     * @return písmeno třídy
     */
    public static String classLetterFromDn(String dn) {
        return classFromDn(dn)[0];
    }

    /**
     * Zpracování DN do částí základního jména, názvu nejbližší organizační jendotky
     * a celé bázové cesty.
     *
     * @param dn plné DN objektu
     * @return pole řetězců [3] {název (cn), jméno nejbližší ou, bázová cesta}
     */
    private static String[] parseDN(String dn) {
        String[] result = new String[3];

        String[] split = dn.split(",");

        // CN=xyz,OU=abc,OU=def,DC=domain,DC=tld
        Integer ou = 1;

        // CN=xyz
        if (split[0].toLowerCase().contains("cn")) {
            result[0] = split[0].split("=")[1];
        } else {
            // OU=abc, + [0] == null
            ou = 0;
        }

        // nejbližší OU
        result[1] = split[ou].split("=")[1];

        // base
        StringBuilder baseBulder = new StringBuilder();
        for(int s = 1; s < split.length; s++) {
            baseBulder.append(split[s]);

            if (s < split.length - 1) {
                baseBulder.append(",");
            }
        }

        result[2] = baseBulder.toString();

        return result;
    }

    public static String parseCN(String dn) {
        return parseDN(dn)[0];
    }

    /**
     * Název nejbližší organizační jednotky daného objektu.
     *
     * @param dn plné DN objektu
     * @return název nejbližší OU
     */
    public static String parseLastOU(String dn) {
        return parseDN(dn)[1];
    }

    /**
     * Bázová LDAP cesta pro daný objekt.
     *
     * @param dn plné DN objektu
     * @return bázová cesta
     */
    public static String parseBase(String dn) {
        return parseDN(dn)[2];
    }

}
