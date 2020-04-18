package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.entities.Absolvent;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IKolekceAD;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Absolventi - kolekce absolventů.
 * Pouze pro čtení.
 * Statická kolekce.
 *
 * Absolvent je objekt uživatel Active Directory.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Absolventi implements IKolekceAD {

    public static final String OU_ABS = Settings.getInstance().getLDAP_baseAlumni().split(",")[1].split("=")[1];

    private static Absolventi instance = null;

    /** iterátor nad absolventy */
    private Iterator iterator = null;

    /** mapování login, detaily */
    private Map<String, Absolvent> absolventi = null;

    /**
     * Absolventi jako globální singleton.
     *
     * @return kolekce absolventů.
     */
    public static Absolventi getInstance() {
        if (Absolventi.instance == null) {
            Absolventi.instance = new Absolventi();
        }

        return Absolventi.instance;
    }

    public Absolventi() {
        initialize();
    }

    private void initialize() {
        if (this.absolventi == null) {

            this.absolventi = new HashMap<String, Absolvent>();

            // vygenerování cesty k OU
            String findInOU = Settings.getInstance().getLDAP_baseAlumni();

            // login do AD, e-mail, zobrazované jméno, cesta DN, informace o účtu
            String[] absData = {
                    EBakaLDAPAttributes.LOGIN.attribute(),
                    EBakaLDAPAttributes.MAIL.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                    EBakaLDAPAttributes.DN.attribute(),
                    EBakaLDAPAttributes.UAC.attribute()
            };

            HashMap<String, String> ldapQ = new HashMap<String, String>();

            // výzhozí hodnoty - uživatelský účet
            ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
            ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());

            Map<Integer, Map<String, Object>> info = BakaADAuthenticator.getInstance().getInfoInOU(findInOU, ldapQ, absData);

            if (info != null && info.size() > 0) {

                for (int rec = 0; rec < info.size(); rec++) {

                    Absolvent tmpAbsolvent = new Absolvent(
                            info.get(rec).get(EBakaLDAPAttributes.LOGIN.attribute()).toString(),
                            info.get(rec).get(EBakaLDAPAttributes.MAIL.attribute()).toString(),
                            info.get(rec).get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()).toString(),
                            info.get(rec).get(EBakaLDAPAttributes.DN.attribute()).toString(),
                            Integer.valueOf(info.get(rec).get(EBakaLDAPAttributes.UAC.attribute()).toString())
                    );

                    if (Settings.getInstance().debugMode()) {
                        System.err.println("[ DEBUG ] Instance absolventa vytvořena.\n" + tmpAbsolvent.toString() + "\n");
                    }

                    this.absolventi.put(tmpAbsolvent.getADLogin(), tmpAbsolvent);
                } // for
            } // zísakná data

        } // absolventi/null

        return;
    }

    /**
     * Vyhledání kolizního absolventa podle loginu.
     *
     * @param login hledaný login
     * @return kolizní absolvent (nebo null)
     */
    @Override
    public IUzivatelAD findCollisions(String login) {

        // e-mail?
        if (login.contains("@")) {
            if (Settings.getInstance().debugMode()) {
                System.out.println("[ DEBUG ] V kolekci absolventů dochází k prohledávání podle e-mailu, výraz bude převeden na jméno.");
            }

            // jméno
            login = login.split("@")[0];

            // prohození jména a příjmení
            if (login.contains(".")) {
                login = login.split("\\.")[1] + "." + login.split("\\.")[0];
            }
        }

        if (this.absolventi.size() > 0 && this.absolventi.containsKey(login)) {
            return this.absolventi.get(login);
        }

        return null;
    }

    /**
     * Iterátor nad absolventy.
     *
     * @return
     */
    @Override
    public Iterator<IUzivatelAD> iterator() {
        if (this.iterator == null) {
            this.iterator = this.absolventi.entrySet().iterator();
        }

        return iterator;
    }

    /**
     * Další absolvent v pořadí.
     *
     * @return následující instance absolventa v iterátoru
     */
    public Absolvent next() {

        if (this.iterator == null) {
            resetIterator();
        }

        Map.Entry absolventElement = (Map.Entry) iterator.next();
        return this.absolventi.get(absolventElement.getKey());
    }

    public void resetIterator() {
        this.iterator = null;
        this.iterator = this.absolventi.entrySet().iterator();
    }

}
