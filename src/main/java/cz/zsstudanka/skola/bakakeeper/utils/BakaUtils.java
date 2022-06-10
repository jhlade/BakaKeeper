package cz.zsstudanka.skola.bakakeeper.utils;

import java.text.Normalizer;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Pomocné statické nástroje pro úpravu a validaci dat.
 *
 * @author Jan Hladěna
 */
public class BakaUtils {

    /**
     * Validace a úprava e-mailové adresy.
     * Pokud je adresa nevalidní, je vrácen prázdný řetězec.
     *
     * @param email mailová adresa k validaci
     * @return validovaná adresa s malými písmeny nebo prázdný řetězec
     */
    public static String validateEmail(String email) {
        if (email == null) {
            return "";
        }

        String regex = "^[\\w-_\\.+]*[\\w-_]\\@([\\w-_]+\\.)+[\\w]+[\\w]$";
        return (email.matches(regex)) ? email.trim().toLowerCase() : "";
    }

    /**
     * Jednoduché ověření platnosti e-mailové adresy. Pokud je vložen prázdný řetězec,
     * jsou data považována také za platná (= žádná e-mailová adresa).
     *
     * @param email vstupní e-mailová adresa
     * @return příznak - formát je platný
     */
    public static Boolean mailIsValid(String email) {
        if (email.equals("")) {
            return true;
        }

        return (validateEmail(email) != "") ? true : false;
    }

    /**
     * Validace a úprava telefonního čísla.
     * Pokud číslo není platné, je vrácen prázdný řetězec.
     *
     * @param phone telefonní číslo
     * @return upravené telefonní číslo bez mezer nebo prázdný řetězec
     */
    public static String validatePhone(String phone) {
        if (phone == null) return "";
        String basePhone = phone.replaceAll(" ", "").trim();
        return (basePhone.length() > 0 && basePhone.matches("^\\d{9}$")) ? basePhone : "";
    }

    /**
     * Odstranění diakritiky z textového řetězce.
     *
     * @param input vstupní řetězec
     * @return výstupní řetězec bez diakritiky
     */
    public static String removeAccents(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFD).replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
    }

    /**
     * @deprecated
     *
     * Vytvoření neověřeného 4+2-INDOŠ loginu z celého jména. Projekt INDOŠ z roku 2001 formátoval přihlašovací
     * jméno jako první čtyři písmena z příjmení a první dvě písmena z křestního jména uživatele.
     * Např. Novák Josef - novajo.
     *
     * @param displayName zobrazované jméno (ve formátu Příjmení Jméno; př. Novák Josef, Svobodová Nováková Jana Kateřina)
     * @return login 4+2 (Novák Josef = novajo, Svobodová Nováková Jana Kateřina = svobja)
     */
    public static String create4p2(String displayName) {
        StringBuilder login = new StringBuilder();

        String[] parts = removeAccents(displayName.toLowerCase()).split(" ");

        if (parts.length == 1 || parts[0].length() < 4 || displayName.contains("-")) {
            login.append(parts[0]);
        } else {
            login.append((parts[0]).substring(0, 4));
            login.append((parts[1]).substring(0, 2)); // pouze první jméno
        }

        return login.toString();
    }

    /**
     * Vytvoření přihlašovacího jména (žáka) do webové aplikace Bakaláři. Ve výchozím stavu aplikace
     * je použito pět písmen z příjmení (bez diakritiky, první velké) a náhodné pětimístné číslo. Pro použití
     * webové aplikace musí být tento login nejprve vytvořen; heslo lze následně samostaně změnit žádostí o změnu hesla,
     * kdy aplikace zašle tento údaj s ověřovacím řetězcem na e-mail uvedený v poli dbo.zaci.E_MAIL (školní adresa
     * vytvořená procesem synchronizace, nebo ověřená adresa zákonného zástupce v poli dbo.zaci_zzd.E_MAIL).
     *
     * @param surname celé příjmení
     * @param givenName celé jméno
     * @return login do webové aplikace
     */
    public static String createWebAppLoginFromName(String surname, String givenName) {

        // konstanty převzaty z výchozího chování Bakalářů, lze změnit
        final int nameLength = 5;
        final int max = 99999;
        final int min = 1;

        Integer randomPart = (int) (Math.random() * (max - min + 1) + min);

        String namePart = removeAccents(surname.substring(0, 1) + (surname.substring(1) + givenName).toLowerCase()).substring(0, nameLength);

        return String.format("%s%05d", namePart, randomPart);
    }

    /**
     * Rozklad jména na strojově zpracovatelné části.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @return jednotlivé části "sn" a "gn" + "snn" pro normalizované příjmení použitelné pro tvorbu hesla
     */
    private static Map<String, String[]> createBaseNameParts(String surname, String givenName) {
        String[] snParts = surname.replace("-", " ")
                .replaceAll("\\s+", " ")
                .replaceFirst("^(v|V)(a|o)n ", "$1$2n")
                .replaceFirst("^(d|D)(a|e|i) ", "$1$2")
                .replaceFirst("^(a|A)l ", "$1l")
                .split(" ");

        String[] snNormalized = surname.replace("-", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("^(d|D)(a|e|i) ", "")
                .replaceAll("(v|V)(a|o)n ", "")
                .replaceAll("(a|A)l ", "")
                .split(" ");

        String[] gnParts = givenName.replace("-", " ")
                .replaceAll("\\s+", " ")
                .split(" ");

        HashMap<String, String[]> baseParts = new HashMap<>();
        baseParts.put("sn", snParts);
        baseParts.put("snn", snNormalized);
        baseParts.put("gn", gnParts);

        return baseParts;
    }

    /**
     * Vytvoření plného UPN pro zadané jméno, příjmení a číslo pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param domain doména
     * @param attempt číslo pokusu
     * @return UPN v zadaném pokusu
     */
    public static String createUPNfromName(String surname, String givenName, String domain, Integer attempt) {
        Map<String, String[]> base = createBaseNameParts(surname, givenName);
        return removeAccents(base.get("sn")[0]).toLowerCase() + "." + removeAccents(base.get("gn")[0]).toLowerCase() + ((attempt == 0) ? "" : attempt.toString()) + "@" + domain;
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
     * @return pre-Windows 2000 login (LDAP atribut sAMAccountName)
     */
    public static String createSAMloginFromName(String surname, String givenName, Integer attempt) {

        // maximální limit délky řetězce hodnoty atributu sAMAccountName v AD - výchozí je 20 znaků
        final int MAX_SAM_LIMIT = 20;

        Map<String, String[]> baseData = createBaseNameParts(surname, givenName);
        String base = removeAccents(baseData.get("sn")[0]).toLowerCase() + "." + removeAccents(baseData.get("gn")[0]).toLowerCase();

        if (base.length() <= MAX_SAM_LIMIT - ((attempt == 0) ? 0 : String.format("%d", attempt).length())) {
            return base + ((attempt == 0) ? "" : attempt.toString());
        } else {
            return (base.substring(0, MAX_SAM_LIMIT - ((attempt == 0) ? 0 : String.format("%d", attempt).length()))) + ((attempt == 0) ? "" : String.format("%d", attempt));
        }
    }

    /**
     * Vytvoření pre-Windows 2000 přihlašovacího jména (do 20 znaků) na základě
     * příjmení a jména v prvním pokusu.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @return pre-Windows 2000 login (LDAP atribut sAMAccountName) v prvním pokusu
     */
    public static String createSAMloginFromName(String surname, String givenName) {
        return createSAMloginFromName(surname, givenName, 0);
    }

    /**
     * Vytvoření pre-Windows 2000 přihlašovacího jména (do 20 znaků) na základě
     * příjmení, jména a již existujícího UPN.
     *
     * @param surname příjmení
     * @param givenName jméno
     * @param currentUPN současně přidělené UPN
     * @return pre-Windows 2000 login (LDAP atribut sAMAccountName)
     */
    public static String createSAMloginFromUPNbase(String surname, String givenName, String currentUPN) {

        Integer attempt = 0;

        // odhadnutí čísla pokusu na základě UPN
        String extractedNumber = currentUPN.replaceAll("\\D+","");
        if (extractedNumber.length() >= 1) {
            attempt = Integer.parseInt(extractedNumber);
        }

        return createSAMloginFromName(surname, givenName, attempt);
    }

    /**
     * Vytvoření počátečního hesla v prvním pokusu.
     *
     * @param surname příjmení žáka
     * @param givenName jméno žáka
     * @param classYear ročník žáka
     * @param classID číslo v třídním výkazu
     * @return počáteční heslo
     */
    public static String createInitialPassword(String surname, String givenName, Integer classYear, Integer classID) {
        return nextPassword(surname, givenName, classYear, classID, 0);
    }


    /**
     * Vytvoření nebo reset výchozího heslo žáka na základě jeho příjmení, jména a čísla v třídním výkazu
     * ve formátu 'Pr.Jm.##yy', kde ## je číslo v třídním výkazu XOR ročník žáka a 'yy' začátek současného
     * školního roku.
     *
     * Aktualizováno podle navržených pravidel 2020/2021.
     *
     * @param surname příjmení žáka
     * @param givenName jméno žáka
     * @param classYear ročník žáka
     * @param classID číslo v třídním výkazu
     * @param attempt číslo pokusu
     * @return heslo vytvořené v daném pokusu
     */
    public static String nextPassword(String surname, String givenName, Integer classYear, Integer classID, Integer attempt) {

        Map<String, String[]> base = createBaseNameParts(surname, givenName);

        return removeAccents(base.get("snn")[0].substring(0, 2))
                + "."
                + removeAccents(base.get("gn")[0].substring(0, 2))
                + "."
                + String.format("%02d", classID ^ (classYear + attempt)) // číslo v tř. výkazu XOR ročník žáka + číslo pokusu
                + ""
                + String.format("%02d", getCurrentClassYear() % 100);
    }

    /**
     * Začátek aktuálního školního roku.
     *
     * @return počáteční kalendářní rok aktuálního školního roku
     */
    public static int getCurrentClassYear() {
        int year = ZonedDateTime.now(ZoneId.of("Europe/Prague")).getYear();
        int month = ZonedDateTime.now(ZoneId.of("Europe/Prague")).getMonthValue();

        // 2. pololetí
        if (month >= 1 && month <= 8) {
            year--;
        }

        return year;
    }

    /**
     * Bázové jméno souboru ze zadané úplné nebo částečné cesty.
     *
     * @param path úlpná nebo částečná cesta k souboru
     * @return jednoduchý tvar jména souboru
     */
    public static String fileBaseName(String path) {
        String[] parts = path.split(".+?/(?=[^/]+$)");
        return parts[parts.length - 1];
    }

    /**
     * Zpracování DN objektu pro identifikaci zařazení do školní třídy.
     * Pokud objekt není aktivním žákem (nachází se mimo základní OU pro žáky),
     * je vráceno pole prázdných položek (null).
     *
     * Př.:
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => String[2] = {"1", "A"}
     *
     * @param dn celé DN objektu žáka
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
     * Př.:
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => 1
     *
     * @param dn plné DN objektu žáka
     * @return číslo ročníku
     */
    public static Integer classYearFromDn(String dn) {
        return Integer.parseInt(classFromDn(dn)[1]);
    }

    /**
     * Písmeno třídy žáka na základě jeho DN, které musí obsahovat
     * odpovídající OU.
     *
     * Př.:
     * CN=Novák Josef,OU=Trida-A,OU=Rocnik-1,OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local
     * => 'A'
     *
     * @param dn plné DN objektu žáka
     * @return písmeno třídy
     */
    public static String classLetterFromDn(String dn) {
        return classFromDn(dn)[0];
    }

    /**
     * Textový řetězec označení třídy ve formátu X.Y na základě DN, které musí
     * obsahovat odpovídající OU.
     *
     * @param dn plné DN žáka
     * @return řetězec označení třídy
     */
    public static String classStringFromDN(String dn) {
        return classYearFromDn(dn).toString() + "." + classLetterFromDn(dn);
    }

    /**
     * Zpracování DN do částí základního jména, názvu nejbližší organizační jendotky
     * a celé bázové cesty.
     *
     * @param dn plné DN objektu
     * @return pole řetězců [3] {název (CN), jméno nejbližší OU, bázová cesta}
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

        // báze
        StringBuilder baseBuilder = new StringBuilder();
        for(int s = 1; s < split.length; s++) {
            baseBuilder.append(split[s]);

            if (s < split.length - 1) {
                baseBuilder.append(",");
            }
        }

        result[2] = baseBuilder.toString();

        return result;
    }

    /**
     * Zpracování názvu (CN) objektu z jeho DN.
     *
     * @param dn plné DN objektu
     * @return CN objektu
     */
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

    /**
     * Vytvoření následujícího číselného DN označení LDAP objektu
     * na dvě místa.
     *
     * @param dn vstupní DN objektu
     * @return DN následujícího objektu
     */
    public static String nextDN(String dn) {

        // současné CN
        String cn = parseCN(dn);

        // extrakce současného označení
        Integer currentNumber = 0;
        String currentDigit = cn.replaceAll("\\D+","");
        if (currentDigit.length() > 0) {
            currentNumber = Integer.parseInt(currentDigit);
        }

        // přidání dalšího čísla v řadě
        String nextCN = cn.replace(currentDigit, "") + ((currentNumber == 0) ? " " : "") + String.format("%02d", (currentNumber + 1));

        // následující DN
        return "CN=" + nextCN + "," + parseBase(dn);
    }
}
