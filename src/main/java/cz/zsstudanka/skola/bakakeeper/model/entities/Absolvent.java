package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.collections.Absolventi;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Model absolventa.
 *
 * Absolvent je objekt uživatel Active Directory.
 * Pouze pro čtení.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Absolvent implements IUzivatelAD {

    /** lokální login absolventa */
    private String ad_login;

    /** primární e-mailová adresa na serveru Exchange */
    private String ad_mail;

    /** zobrazované jméno */
    private String displayName;

    /** školní rok ukončení */
    private String absYear;

    /** záznam absolventa je validní */
    private Boolean absValid;

    /** ladící zprávy o objektu */
    private ArrayList<String> debugMessages;

    /**
     * Konstruktor s vyhledáním podle loginu.
     *
     * @param login
     */
    public Absolvent(String login) {

        this.debugMessages = new ArrayList<String>();

        HashMap<String, String> ldapQ = new HashMap<String, String>();

        ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
        ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
        ldapQ.put(EBakaLDAPAttributes.LOGIN.attribute(), login);

        String[] info = new String[]{
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.DN.attribute(),
                EBakaLDAPAttributes.UAC.attribute()
        };

        Map getData = ((Map<Integer, Map>) BakaADAuthenticator.getInstance().getInfoInOU(Settings.getInstance().getLDAP_baseAlumni(), ldapQ, info)).get(0);

        this.ad_login = getData.get(EBakaLDAPAttributes.LOGIN.attribute()).toString();
        this.ad_mail = getData.get(EBakaLDAPAttributes.MAIL.attribute()).toString();
        this.displayName = getData.get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()).toString();
        this.setAbsYear(getData.get(EBakaLDAPAttributes.DN.attribute()).toString());

        if (getData.get(EBakaLDAPAttributes.DN.attribute()).toString().contains(Absolventi.OU_ABS)) {

            this.absValid = true;

            if (!EBakaUAC.ACCOUNTDISABLE.checkFlag(Integer.valueOf(getData.get(EBakaLDAPAttributes.UAC.attribute()).toString()))) {
                this.absValid = false;

                String msg2 = "Účet absolventa " + this.getDisplayName() + " není zablokován proti přihlášení.";
                this.debugMessages.add(msg2);

                if (Settings.getInstance().debugMode()) {
                    System.err.println("[ DEBUG ] " + msg2);
                }
            }

        } else {

            String msg1 = "Účet absolventa " + this.getDisplayName() + " není v organizační jednotce pro absolventy.";
            this.debugMessages.add(msg1);

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ DEBUG ] " + msg1);
            }

            this.absValid = false;
        }
    }

    /**
     * Obecný konstruktor absolventa.
     *
     * @param login přihlašovací jméno
     * @param mail e-mailová adresa v Active Directory
     * @param displayName zobrazované jméno
     * @param absDN plná cesta DN
     * @param userAccountControl stavová informace o uživatelském účtu
     */
    public Absolvent(String login, String mail, String displayName, String absDN, Integer userAccountControl) {
        this.ad_login = login;
        this.ad_mail = mail;
        this.displayName = displayName;

        // získání roku vyřazení
        this.setAbsYear(absDN);

        // úvodní hodnota
        this.absValid = true;
        this.debugMessages = new ArrayList<String>();

        // chybná OU
        if (!absDN.contains(Absolventi.OU_ABS)) {
            this.absValid = false;
            this.debugMessages.add("Účet absolventa " + displayName + " není správně zařazen v OU pro absolventy.");
        }

        // chybně nastavený účet
        if (!EBakaUAC.ACCOUNTDISABLE.checkFlag(userAccountControl)) {
            this.absValid = false;
            this.debugMessages.add("Účet absolventa " + displayName + " není uzamčen proti přihlášení.");
        }
    }

    /**
     * Vytvoření roku vyřazení na základě názvu poslední platné OU z celkového DN.
     *
     * Vstupní forma: CN=Novák Tomáš,OU=ABS_2019_2020,OU=StudiumUkonceno,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsskola,DC=local
     * Výstupní forma: 2019/2020
     *
     * @param stringOU rok vyřazení z evidence
     */
    public void setAbsYear(String stringOU) {
        this.absYear = stringOU.split(",")[1].split("=")[1].replace("ABS_", "").replace("_","/");
    }

    /**
     * Ověření platnosti záznamu absolventa.
     *
     * @return
     */
    public Boolean isValid() {
        return (this.absValid != null) ? this.absValid : false;
    }

    /**
     * Získání výpisu roku vyřazení z evidence.
     *
     * @return rok vyřazení
     */
    public String getAbsYear() {
        return this.absYear;
    }

    @Override
    public String getADLogin() {
        return this.ad_login;
    }

    @Override
    public String getADEmail() {
        return this.ad_mail;
    }

    @Override
    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public ArrayList<String> getDebugMessages() {
        return this.debugMessages;
    }

    @Override
    public String toString() {
        return "Absolvent:\t" + this.getDisplayName() + "\nVyřazení:\t" + this.getAbsYear() + "\nLogin:\t\t" + this.getADLogin() + "\nE-mail:\t\t" + this.getADEmail();
    }

    /**
     * Konstruktor absolventa z AD loginu.
     *
     * @param ad_login login
     * @return instance absolventa
     */
    public static Absolvent createFromLogin(String ad_login) {

        String[] info = {
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.DN.attribute(),
                EBakaLDAPAttributes.UAC.attribute()
        };

        Map<String, Object> user = null;//BakaADAuthenticator.getInstance().getUserInfo(ad_login, info);

        if (user != null && user.size() > 0) {

            return new Absolvent(
                    user.get(EBakaLDAPAttributes.LOGIN.attribute()).toString(),
                    user.get(EBakaLDAPAttributes.MAIL.attribute()).toString(),
                    user.get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()).toString(),
                    user.get(EBakaLDAPAttributes.DN.attribute()).toString(),
                    Integer.valueOf(user.get(EBakaLDAPAttributes.UAC.attribute()).toString())
            );

        }

        return null;
    }
}
