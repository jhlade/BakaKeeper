package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.collections.Rocnik;
import cz.zsstudanka.skola.bakakeeper.model.collections.Trida;
import cz.zsstudanka.skola.bakakeeper.model.collections.Absolventi;
import cz.zsstudanka.skola.bakakeeper.model.collections.Zamestnanci;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Model žáka.
 *
 * Žák je objekt uživatel Active Directory.
 * Žák je záznam na SQL Serveru.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Zak implements IUzivatelAD {

    /** Primární klíč žáka INTERN_KOD z Bakalářů. */
    private String intern_kod;

    private Integer c_tr_vyk;

    /** Pole E_MAIL z Bakalářů. */
    private String baka_email;

    /** Celé první jméno */
    private String jmeno;
    /** Příjmení */
    private String prijmeni;

    /** Ročník žáka */
    private Integer rocnik;
    /** Písmeno třídy */
    private String trida;

    /** Vygenerovaný login do AD */
    private String ad_login;
    /** Vygenerovaný e-mail */
    private String ad_email;

    /** Zákonnéhý zástupce žáka */
    private ZakonnyZastupce zakonnyZastupce;

    /** zpětně párovaný objekt třídy */
    private Trida objTrida;

    /** stavové zprávy */
    private ArrayList<String> debugMessages;

    private Boolean isLDAPrecord;
    private Boolean isSQLrecord;


    public Zak(String intern_kod) {
        // TODO konstrukce žáka ze SQL dotazu pomocí interního kódu
    }

    /**
     * Konstruktor z SQL dotazování Bakalářů.
     *
     * @param intern_kod INTERN_KOD z Bakalářů
     * @param baka_email aktuální hodnota e-mailu žáka uložená v SQL
     * @param jmeno jméno žáka
     * @param prijmeni příjmení žáka
     * @param trida třída ve formátu "1.A"
     */
    public Zak(String intern_kod, String c_tr_vyk, String baka_email, String jmeno, String prijmeni, String trida, ZakonnyZastupce zz) {
        this.intern_kod = intern_kod.trim();
        this.baka_email = baka_email.trim(); // současný e-mail
        this.jmeno = jmeno.trim();
        this.prijmeni = prijmeni.trim();
        this.rocnik = Integer.parseInt(trida.trim().substring(0,1));
        this.trida = trida.trim().split("\\.")[1];

        this.c_tr_vyk = Integer.parseInt(c_tr_vyk);

        this.debugMessages = new ArrayList<>();

        // mailová adresa obsahuje školní doménu => předpokládá se platný školní e-mail
        if (this.baka_email.contains(Settings.getInstance().getMailDomain())) {
            this.ad_email = this.baka_email;
        } else {
            this.debugMessages.add("E-mailová adresa žáka se nenachází ve školní doméně.");
        }

        this.zakonnyZastupce = zz;

        // TODO ověření proti AD - dotaz na Žáky podle políčka
    }

    public String getIntern_kod() {
        return intern_kod;
    }

    public String getHashedIntern_kod() {
        return BakaUtils.hashMD5(this.getIntern_kod());
    }

    public String getADLogin() {
        return this.ad_login;
    }

    public String getADEmail() {
        return this.ad_email;
    }

    public String getBakaEmail() {
        return this.baka_email;
    }

    public String getCTrVyk() {
        return String.format("%02d", this.c_tr_vyk);
    }

    /**
     * Přiřazení ZZ k žákovi.
     *
     * @param zakonnyZastupce
     */
    public void setZakonnyZastupce(ZakonnyZastupce zakonnyZastupce) {
        this.zakonnyZastupce = zakonnyZastupce;
    }

    /**
     * Nastavení nového e-mailu - zápis do Bakalářů.
     *
     * @param email nový e-mail
     */
    private void setEmail(String email) {
        // TODO SQL dotaz - aktualiace mailové adresy žáka - je to privátní, adresa se vyrábí
    }

    /**
     * Synchronizace e-mailové adresy mezi SQL a AD. Prioritu má vygenerovaný e-mail pro AD.
     */
    private void syncMail() {
        // ověření
        if (this.ad_email == null) {
            //createNewEmail();
        }

        // zapsat do AD
        // TODO 1) instance AD konektoru, 2) zapsání atributu pro UPN (ad_login, mail)

        // zapsat do SQL
        // TODO
    }

    /**
     * Celé křestní jméno žáka.
     *
     * SQL pole JMENO (dbo.zaci.JMENO)
     * LDAP atribut givenName
     *
     * @return křestní jméno žáka
     */
    public String getJmeno() {
        return jmeno;
    }

    /**
     * Pouze první jméno v případě vícejmenných křestních jmen.
     *
     * Příklad: Anna Marie Nováková => Anna
     *
     * @return pouze první jméno
     */
    public String getPrvniJmeno() {
        return this.getJmeno().split(" ")[0];
    }

    /**
     * Celé příjmení žáka.
     *
     * SQL pole JMENO (dbo.zaci.PRIJMENI)
     * LDAP atribut sn
     *
     * @return příjmení žáka
     */
    public String getPrijmeni() {
        return prijmeni;
    }

    /**
     * Celé zobrazované jméno včetně vícečetných křestních jmen.
     *
     * @return Celé zobrazované jméno.
     */
    public String getDisplayName() {

        StringBuilder displayNameBuilder = new StringBuilder();

        displayNameBuilder.append(getPrijmeni());
        displayNameBuilder.append(" ");
        displayNameBuilder.append(getJmeno());

        return displayNameBuilder.toString();
    }

    @Override
    public ArrayList<String> getDebugMessages() {
        return this.debugMessages;
    }

    /**
     * Číslo ročníku žáka.
     *
     * @return jednoduché číslo ročníku, např. 5
     */
    public Integer getRocnik() {
        return rocnik;
    }

    /**
     * Písmeno třídy žáka.
     *
     * @return jednoduché písmeno třídy, např. A
     */
    public String getPismenoTridy() {
        return trida.toUpperCase();
    }

    /**
     * Textové označení třídy.
     *
     * @return třída ve tvaru 1.B, 9.A
     */
    public String getTridaText() {
        return this.getRocnik() + "." + this.getPismenoTridy();
    }

    public Trida getTrida() {
        if (this.objTrida == null) {
            this.objTrida = new Trida(this.getRocnik(), this.getPismenoTridy());
        }

        return this.objTrida;
    }

    /**
     * Vytvoření ukazatele na předanou třídu.
     *
     * @param trida
     */
    public void setTrida(Trida trida) {

        if (trida.getCisloRocniku().equals(this.getRocnik())
            &&
            trida.getPismeno().equals(this.getPismenoTridy())
        ) {

            this.objTrida = trida;
        }

        return;
    }

    public ZakonnyZastupce getZakonnyZastupce() {
        return zakonnyZastupce;
    }

    /**
     * Vytvoří žákovský login ve tvaru prijmeni.jmeno. Během tvorby se kontrolují duplicity
     * a v případě shody je ke křestnímu jménu přidáno nové číslo začínající od 1.
     * Systém kontroly je prováděn nejprve vůči zaměstnancům, absolventům a dále vůči žákům
     * od nejvyšších ročníků.
     *
     * Adam Jáchym Novák Novotný => novak.adam, novak.adam1, novak.adam2 ...
     *
     */
    private void generateNewCredentials() {

        String login; // nový login
        String email; // nový email

        // stav hledání
        Boolean loginUnique;

        Integer pokus = 0;
        do {

            // reset do pozitivního stavu
            loginUnique = true;

            // vytvoření loginu
            login = BakaUtils.createFullLogin(this.getPrijmeni(), this.getJmeno());

            // přidání čísla za jméno
            if (pokus > 0) {
                login = login + pokus;
            }

            // vytvoření e-mailu z loginu
            email = BakaUtils.createFullMail(login);

            // porovnat 1) se seznamem zaměstnanců (pouze e-mail)
            if (Zamestnanci.getInstance().findCollisions(email) != null) {

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ INFO ] Byla nalezena kolize se zaměstnancem, zkouší se znovu.");
                }

                if (Settings.getInstance().debugMode()) {
                    Zamestnanec colZamestnanec = Zamestnanec.createFromLogin(login);
                    System.err.println("[ DEBUG ] Kolize se zaměstnancem " + colZamestnanec.getDisplayName() + ".");
                }

                loginUnique = false;

                // další pokus
                pokus++;
                continue;
            }

            // porovnat 2) se seznamem absolventů
            if (Absolventi.getInstance().findCollisions(login) != null) {

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ INFO ] Byla nalezena kolize s absolventem, zkouší se znovu.");
                }

                if (Settings.getInstance().debugMode()) {
                    Absolvent colZabsolvent = Absolvent.createFromLogin(login);
                    System.err.println("[ DEBUG ] Kolize s absolventem " + colZabsolvent.getDisplayName() + " (vyřazen " + colZabsolvent.getAbsYear() + ").");
                }

                loginUnique = false;

                // další pokus
                pokus++;
                continue;
            }

            // porovnat 3) se seznamem žáků od nejstarších dětí dolu
            for (int roc = 9; roc <= 1; roc--) {

                // vytvoření kolekce žáků ročníku "roc"
                Rocnik tmpRocnik = new Rocnik(roc);

                if (tmpRocnik.findCollisions(login) != null) {

                    if (Settings.getInstance().beVerbose()) {
                        System.err.println("[ INFO ] Byla nalezena kolize se současným žákem, zkouší se znovu.");
                    }

                    if (Settings.getInstance().debugMode()) {
                        Zak colZak = Zak.createFromLogin(login);
                        System.err.println("[ DEBUG ] Kolize s žákem " + colZak.getDisplayName() + " (" + colZak.getTridaText() + ").");
                    }

                    loginUnique = false;
                }

            }

            // další pokus
            pokus++;
        } while (!loginUnique);

        // přiřazení k objektu
        this.ad_login = login;
        this.ad_email = email;
    }

    /**
     * Dohledání údajů žáka v Active Directory podle jména a třídy získané z Bakalářů.
     *
     * @return záznam žáka existuje v Active Directory
     */
    public Boolean findByData() {

        // vygenerování cesty k OU
        String zakOU = this.getTrida().getOU() + "," + Settings.getInstance().getLDAP_baseStudents();

        // login do AD, e-mail, zobrazované jméno, cesta
        String[] zakData = {
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.DN.attribute()
        };

        HashMap<String, String> ldapQ = new HashMap<String, String>();

        // výzhozí hodnoty - uživatelský účet
        ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
        ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
        // příjmení
        ldapQ.put(EBakaLDAPAttributes.NAME_LAST.attribute(), this.getPrijmeni());
        // jméno
        ldapQ.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), this.getJmeno());

        Map info = ((Map<Integer, Map>) BakaADAuthenticator.getInstance().getInfoInOU(zakOU, ldapQ, zakData)).get(0);

        // záznam v LDAP existuje
        if (info != null && info.size() > 0) {

            if (info.get(EBakaLDAPAttributes.DN.attribute()).toString().contains("Trida-")
                && info.get(EBakaLDAPAttributes.DN.attribute()).toString().contains("Rocnik-")
            ) {

                // zařazení do třídy podle DN
                String tmpTrida = info.get(EBakaLDAPAttributes.DN.attribute()).toString().split(",")[1].split("=")[0].replace("Trida-", "");
                String tmpRocnik = info.get(EBakaLDAPAttributes.DN.attribute()).toString().split(",")[2].split("=")[0].replace("Rocnik-", "");

                this.ad_login = info.get(EBakaLDAPAttributes.LOGIN.attribute()).toString();
                this.ad_email = info.get(EBakaLDAPAttributes.MAIL.attribute()).toString();

                return true;
            } else {
                // TODO
                // zřejmě absolvent, nebo špatná třída
                return false;
            }
        }

        return false;
    }

    /**
     * Žák exituje v Active Directory jako platný uživatel.
     *
     * @return
     */
    public Boolean isLDAPrecord() {

        if (this.isLDAPrecord == null) {
            this.setIsLDAPrecord(this.findByData());
        }

        return this.isLDAPrecord;
    }

    /**
     * Žák existuje v Bakalářích jako platný záznam.
     *
     * @return
     */
    public Boolean isSQLrecord() {
        return this.isSQLrecord;
    }

    /**
     * Nastavení stavu platnosti záznamu žáka v SQL.
     *
     * @param isRecord žák je záznamem v SQL
     */
    public void setIsSQLrecord(boolean isRecord) {
        this.isSQLrecord = isRecord;
    }

    /**
     * Nastavení platnosti záznamu žáka v Active Directory.
     *
     * @param isRecord žáj je platným záznamem v Active Directory
     */
    public void setIsLDAPrecord(boolean isRecord) {
        this.isLDAPrecord = isRecord;
    }

    /**
     * Získá seznam skupin se zabezpečením Active Directory, jejichž je žák členem.
     *
     * @return pole názvů skupin ve tvaru DN
     */
    public String[] getADGroups() {

        // 0) uživatel existuje v LDAP
        // 1) výčet skupin

        if (!this.isLDAPrecord()) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nelze se dotázat na skupiny žáka, protože není platným záznamem v Active Directory.");
            }

            return null;
        }

        String[] data = {
                EBakaLDAPAttributes.MEMBER_OF.attribute()
        };

        Map<String, Object> user = BakaADAuthenticator.getInstance().getUserInfo(this.ad_login, data);

        if (user != null && user.size() > 0) {
            ArrayList groups = (ArrayList) user.get(EBakaLDAPAttributes.MEMBER_OF);
            if (groups.size() > 0) {
                String[] groupList = new String[groups.size()];

                for (int g = 0; g < groups.size(); g++) {
                    groupList[g] = groups.get(g).toString();
                }

            } else {
                return new String[]{};
            }
        }

        return null;
    }

    /**
     * Provede zápis všech změn přímo do SQL a AD.
     */
    private void writeChanges() {

        // 1) do SQL mail v doméně
        // 2) do AD

        // TODO SQL
        // TODO AD
    }

    /**
     * Zavedení nového žáka z Bakalářů do AD.
     */
    private void createLDAPrecord() {
        // TODO
        // cesta k OU
        // vytvoření uživatele
    }

    /**
     * Magická funkce pro synchronizaci Bakalářů a AD.
     *
     * Vstupní prioritu má záznam v SQL - získá se jméno, příjmení, třída a zákonný zástupce žáka.
     */
    public void sync() {

    }

    /**
     * Komplexní funkce převede účet žáka mezi ty, co již opustili školu.
     * Podmínky - je zaručena existence OU StudiumUkonceno/ABS_{SKOLNI_ROK} (ABS_2019_2020)
     *          - žák existuje v AD, ale v Bakalářích již ne
     *
     * Akce - uživatelský účet bude v AD uzamčen
     *      - uživatel se přesune do nově vzniklé OU
     *      - uživatel je vyřazen ze všech bezpečnostních skupin typu Rocnik-X, Trida-Y, Zaci
     *      (- je provedena kontrola zákonných zástupců)
     */
    public void makeAbsolvent() {

    }

    @Override
    public String toString() {
        // TODO
        return "Žák "+this.getPismenoTridy()+": ." + this.getDisplayName() + ". (Bakamail: " + this.getBakaEmail() + ")";
    }

    /**
     * Vytvoření instance žáka podle loginu v AD.
     *
     * @param ad_login
     * @return
     */
    public static Zak createFromLogin(String ad_login) {

        String[] ad_data = {
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.NAME_FIRST.attribute(),
                EBakaLDAPAttributes.NAME_LAST.attribute(),
                EBakaLDAPAttributes.DN.attribute(),
        };

        // 1) AD
        Map<String, Object> user = BakaADAuthenticator.getInstance().getUserInfo(ad_login, ad_data);

        if (user != null && user.size() > 0) {

            String jmeno = user.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString();
            String prijmeni = user.get(EBakaLDAPAttributes.NAME_LAST).toString();

            String trida = BakaUtils.tridaFromDN(user.get(EBakaLDAPAttributes.DN.attribute()).toString());

            if (trida == null) {
                // TODO err
                return null;
            }

            // 2) SQL
            String sql = "SELECT dbo.zaci.INTERN_KOD,dbo.zaci.C_TR_VYK,dbo.zaci.PRIJMENI,dbo.zaci.JMENO,dbo.zaci.TRIDA,dbo.zaci.E_MAIL," // data žáka
                    + "dbo.zaci_zzd.ID AS ZZ_KOD,dbo.zaci_zzd.PRIJMENI AS ZZ_PRIJMENI,dbo.zaci_zzd.JMENO AS ZZ_JMENO,dbo.zaci_zzd.TEL_MOBIL AS ZZ_TELEFON,dbo.zaci_zzd.E_MAIL AS ZZ_MAIL " // data ZZ
                    + "FROM dbo.zaci LEFT JOIN dbo.zaci_zzd ON (dbo.zaci_zzd.ID = (SELECT TOP 1 dbo.zaci_zzr.ID_ZZ FROM dbo.zaci_zzr WHERE dbo.zaci_zzr.INTERN_KOD = dbo.zaci.INTERN_KOD AND (dbo.zaci_zzr.JE_ZZ = '1' AND dbo.zaci_zzr.PRIMARNI = '1'))) " // detekce primárního ZZ
                    + "WHERE dbo.zaci.TRIDA LIKE '%.%' AND dbo.zaci.EVID_DO IS NULL " // žák existuje
                    + "AND dbo.zaci.JMENO LIKE = '" + jmeno + "%' "
                    + "AND dbo.zaci.PROJMENI LIKE = '" + prijmeni + "%' "
                    + "AND dbo.zaci.TRIDA = '" + trida + "'; "; // jedna konkrétní třída

            try {

                ResultSet rs = BakaSQL.getInstance().select(sql);

                if (rs.getFetchSize() == 1) {

                    while (rs.next()) {

                        ZakonnyZastupce zz;

                        if (rs.getString("ZZ_KOD") != null) {

                            zz = new ZakonnyZastupce(
                                    rs.getString("ZZ_KOD"),
                                    rs.getString("ZZ_JMENO"),
                                    rs.getString("ZZ_PRIJMENI"),
                                    rs.getString("ZZ_TELEFON"),
                                    rs.getString("ZZ_MAIL"),
                                    null
                            );
                        } else {
                            zz = new ZakonnyZastupce();
                        }

                        // String intern_kod, String e_mail, String jmeno, String prijmeni, String trida
                        Zak zak = new Zak(
                                rs.getString("INTERN_KOD"),
                                rs.getString("C_TR_VYK"),
                                rs.getString("E_MAIL"),
                                rs.getString("JMENO"),
                                rs.getString("PRIJMENI"),
                                rs.getString("TRIDA"),
                                zz
                        );

                        zak.setIsLDAPrecord(true); // LDAP ->
                        zak.setIsSQLrecord(true); // -> SQL

                        return zak;
                    }

                } else {
                    System.err.println("[ CHYBA ] Nejednoznačný nebo žádný výsledek při pokusu o konstrukci žáka z loginu.");

                    if (Settings.getInstance().beVerbose()) {
                        System.err.println("[ CHYBA ] Nalezeno " + rs.getFetchSize() + " výsledků, očekáván 1.");
                    }

                    if (Settings.getInstance().debugMode()) {
                        System.err.println("[ CHYBA ] Na SQL dotaz \n" + sql + "\nbylo nalezeno " + rs.getFetchSize() + " výsledků.");
                    }
                }

            } catch (Exception e) {
                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ CHYBA ] " + e.getMessage());
                }
                if (Settings.getInstance().debugMode()) {
                    e.printStackTrace(System.err);
                }
            }

        }

        return null;
    }

    // tmp - tvorba hesla
    public String getRPwd() {
        return BakaUtils.removeAccents(this.getPrijmeni().substring(0, 2)) + "." + BakaUtils.removeAccents(this.getJmeno().substring(0, 2)) + "." + this.getCTrVyk();
    }
}
