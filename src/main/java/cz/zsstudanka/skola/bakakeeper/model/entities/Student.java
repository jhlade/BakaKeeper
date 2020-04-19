package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.io.UnsupportedEncodingException;
import java.util.Map;

/**
 * Žák. Je reprezentován jako aktivní žák v evidenci a jako LDAP záznam uživatele (512)
 * v aktivních OU. Je členem základní skupiny žáků a členem skupiny konkrétní třídy.
 *
 * implements IRecordLDAP, IRecordSQL
 *
 * @author Jan Hladěna
 */
public class Student implements IRecordLDAP, IRecordSQL {

    /** INTERN_KOD */
    private String internalID;

    // C_TR_VYK
    private Integer classRegNumber;

    /** JMENO */
    private String givenName;
    /** PRIJMENI */
    private String surname;

    // E_MAIL

    private Map<String, Object> rawAttributes;

    // metoda reset/prepare? Pr.Jm.##C_TR_VYK

    /**
     * Primární konstruktor aktivního žáka.
     *
     * @param upn UserPrincipalName = primární e-mail v Bakalářích
     */
    public Student(String upn) {

    }

    /**
     * Dočasný konstruktor z procházení.
     *
     * @param rawAttributes
     */
    public Student(Map<String, Object> rawAttributes) {
        this.rawAttributes = rawAttributes;
    }

    /**
     * Resetování žákovského hesla do původního tvaru
     * Pr.Jm.##
     *
     * @return úspěch operace
     */
    public Boolean resetPassword() {

        // nové heslo = Pr.Jm.##
        String newPassword = BakaUtils.removeAccents(this.surname.substring(0, 2))
                + "."
                + BakaUtils.removeAccents(this.givenName.substring(0, 2))
                + "."
                + String.format("%2d", this.classRegNumber);

        return setPassword(newPassword, true);
    }

    /**
     * Okamžité nastavení hesla.
     *
     * @param newPassword nové heslo
     * @param forceChange vyžadovat změnu hesla
     * @return úspěch operace
     */
    public Boolean setPassword(String newPassword, Boolean forceChange) {

        // unicodePassword
        try {
            BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_UNICODE,
                    ("\"" + newPassword + "\"").getBytes("UTF-16LE")
            );
        } catch (UnsupportedEncodingException e) {
            // TODO
            //e.printStackTrace();
        }

        if (forceChange) {
            BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_REQCHANGE.value());
        } else {
            BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_LASTSET.value());
        }

        return true;
    }

    /**
     * Přesunutí aktivního žáka do jiné třídy. Přesune fyzicky objekt definovaný
     * dn do požadované OU, nastaví požadované člensktví ve skupinách
     * a změní atribut "title" na {X}.{Y}.
     *
     * @param year ročník, očekává se číslo 1-9
     * @param letter písmeno třídy, očekává se A-E
     * @return
     */
    public Boolean moveToClass(Integer year, String letter) {

        String newClassOU = "OU=Trida-" + letter.toUpperCase() + ",OU=Rocnik-" + year + "," + Settings.getInstance().getLDAP_baseStudents();
        String newClassSG = "Zaci-Trida-" + year + letter.toUpperCase();
        String newTitle = year + "." + letter.toUpperCase();

        // TODO přesun do OU

        // TODO změna skupiny

        // změna pracovního zařazení
        BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(), EBakaLDAPAttributes.TITLE, newTitle);

        // TODO
        return true;
    }

    /**
     * Přesune účet do OU=StudiumUkonceno,OU={YYYY},
     * odebere ze všech skupin, zablokuje ho a nastaví odpovídající atribut "title"
     * na "Abs. {YYYY}".
     *
     * @return úspěch operace
     */
    public Boolean retireAccount() {
        return true;
    }

    // TODO
    @Override
    public String getDN() {
        return null;
    }

    // TODO
    @Override
    public String getExtensionAttribute(Integer attrNum) {
        return null;
    }

    // TODO
    @Override
    public Boolean setExtensionAttribute(Integer attrNum, String value) {
        return null;
    }

    @Override
    public String getInternalID() {
        return null;
    }
}
