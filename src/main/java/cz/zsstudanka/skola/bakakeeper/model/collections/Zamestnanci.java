package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IKolekceAD;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.model.entities.Zamestnanec;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Zaměstnanci - kolekce zaměstnanců.
 *
 * Pouze pro čtení.
 * Statická kolekce.
 *
 * Zaměstnanec je objekt uživatel Active Directory.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Zamestnanci implements IKolekceAD {

    /** instance seznamu zaměstnanců */
    private static Zamestnanci instance = null;

    /** mapa zaměstnanců, klíčem je primární e-mailová adresa */
    private Map<String, Zamestnanec> zamestnanci;

    /** iterátor */
    private Iterator iterator = null;


    /**
     * Konstruktor vytvoří automaticky všechny zaměstnance.
     */
    public Zamestnanci() {
        initialize();
    }

    /**
     * Inicializace seznamu zaměstnanců.
     */
    private void initialize() {

        // pokud je již seznam naplněn, nic se nevytváří
        if (zamestnanci != null) return;

        this.zamestnanci = new HashMap<String, Zamestnanec>();

        // vygenerování cesty k OU
        String findInOU = Settings.getInstance().getLDAP_baseFaculty();

        // login do AD, e-mail, zobrazované jméno
        String[] zamData = {
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.UAC.attribute()
        };

        HashMap<String, String> ldapQ = new HashMap<String, String>();

        // výzhozí hodnoty - uživatelský účet
        ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
        ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());

        // výsledek dotazu na LDAP
        Map<Integer, Map<String, Object>> info = BakaADAuthenticator.getInstance().getInfoInOU(findInOU, ldapQ, zamData);

        // zpracování dotazu
        if (info != null && info.size() > 0) {

            // zpracování záznamu
            for (int rec = 0; rec < info.size(); rec++) {

                // tvorba instance zaměstnance
                Zamestnanec tmpZamestnanec = new Zamestnanec(
                        info.get(rec).get(EBakaLDAPAttributes.LOGIN.attribute()).toString(),
                        (info.get(rec).containsKey(EBakaLDAPAttributes.MAIL.attribute())) ? info.get(rec).get(EBakaLDAPAttributes.MAIL.attribute()).toString() : "NULL",
                        info.get(rec).get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()).toString()
                );

                // kontrola tvaru přihlašovacího jména + záznam do zpráv
                if (!tmpZamestnanec.getADLogin().equals(BakaUtils.create4p2(tmpZamestnanec.getDisplayName()))) {
                    tmpZamestnanec.getDebugMessages().add("Uživatelksý účet zaměstance není ve tvaru 4+2.");
                }

                // kontrola vyplnění primární e-mailové adresy
                if (!info.get(rec).containsKey(EBakaLDAPAttributes.MAIL.attribute())) {
                    tmpZamestnanec.getDebugMessages().add("Zaměstnanec nemá nastavený platný e-mail.");
                }

                // kontrola uzamčení účtu
                if (EBakaUAC.ACCOUNTDISABLE.checkFlag(
                                Integer.valueOf(
                                        info.get(rec).get(EBakaLDAPAttributes.UAC.attribute()).toString()
                                )
                        )
                ) {
                    tmpZamestnanec.getDebugMessages().add("Uživatelský účet zaměstnance je uzamčen.");
                }

                if (Settings.getInstance().debugMode()) {
                    System.err.println("[ DEBUG ] Instance zaměstnance vytvořena.\n" + tmpZamestnanec.toString() + "\n");
                }

                // přidání instance do kolekce
                this.zamestnanci.put(tmpZamestnanec.getADEmail(), tmpZamestnanec);
            }
        } // TODO nenalezeno?
    }

    /**
     * Seznam zaměstnanců jako singleton.
     *
     * @return Zaměstnanci
     */
    public static Zamestnanci getInstance() {
        if (Zamestnanci.instance == null) {
            Zamestnanci.instance = new Zamestnanci();
        }

        return Zamestnanci.instance;
    }

    /**
     * Zjistí kolize zaměstnance s požadovanými údaji žáka.
     *
     * @param login
     * @return objekt kolizního Zaměstnance, nebo null
     */
    public IUzivatelAD findCollisions(String login) {

        if (this.zamestnanci.size() > 0) {

            // podle loginu - nepravděpodobná shoda
            if (this.zamestnanci.containsKey(login)) {
                return this.zamestnanci.get(login);
            }

            // podle jména v UPN tvaru
            if (this.zamestnanci.containsKey(login.split("@")[0])) {
                return this.zamestnanci.get(login.split("@")[0]);
            }

            // speciální stav - podle mailu
            return getByEmail(BakaUtils.createFullMail(login));
        }

        return null;
    }

    /**
     * Vyhledání zaměstance podle e-mailu.
     *
     * @param mail e-mailová adresa
     * @return zaměstnanec (nebo null)
     */
    public Zamestnanec getByEmail(String mail) {
        Iterator<Map.Entry<String, Zamestnanec>> internalIterator = this.zamestnanci.entrySet().iterator();
        while (internalIterator.hasNext()) {
            Zamestnanec tmpZam = ((Map.Entry<String, Zamestnanec>) internalIterator.next()).getValue();

            // pokud e-mail neexistuje, pokračuje se dalším záznamem
            if (tmpZam.getADEmail() == null) {
                continue;
            }

            // ověření shody mailu
            if (tmpZam.getADEmail().equals(mail)) {
                return tmpZam;
            }

        }

        return null;
    }

    @Override
    public String toString() {
        return this.toString(true);
    }

    /**
     * Upravená metoda pro přímý export do CSV.
     *
     * @param asCSV exportovat jako CSV
     * @return textový záznam
     */
    public String toString(Boolean asCSV) {
        StringBuilder seznam = new StringBuilder();

        String separator = "\t";

        if (asCSV) {
            separator = ";";
            // hlavička CSV
            seznam.append("JMENO;LOGIN;EMAIL");
        } else {
            seznam.append("Jméno\tLogin v AD\tPrimární e-mail");
        }
        seznam.append("\n");

        Iterator zamestanciIterator = this.zamestnanci.entrySet().iterator();
        while (zamestanciIterator.hasNext()) {
            Map.Entry zamestnanecElement = (Map.Entry) zamestanciIterator.next();
            seznam.append(this.zamestnanci.get((zamestnanecElement).getKey()).getDisplayName());
            seznam.append(separator);
            seznam.append(this.zamestnanci.get((zamestnanecElement).getKey()).getADLogin());
            seznam.append(separator);
            seznam.append(this.zamestnanci.get((zamestnanecElement).getKey()).getADEmail());
            seznam.append("\n");
        }

        return seznam.toString();
    }

    @Override
    public Iterator<IUzivatelAD> iterator() {
        if (this.iterator == null) {
            resetIterator();
        }

        return this.iterator;
    }

    public void resetIterator() {
        this.iterator = null;
        this.iterator = this.zamestnanci.entrySet().iterator();
    }

    /**
     * Další zaměstnanec v pořadí.
     *
     * @return instance zaměstnance.
     */
    public Zamestnanec next() {

        if (this.iterator == null) {
            resetIterator();
        }

        Map.Entry zamestnanecElement = (Map.Entry) iterator.next();
        return this.zamestnanci.get(zamestnanecElement.getKey());

    }

    /**
     * Počet zaměstnanců v kolekci.
     *
     * @return počet
     */
    public Integer count() {
        return this.zamestnanci.entrySet().size();
    }

}
