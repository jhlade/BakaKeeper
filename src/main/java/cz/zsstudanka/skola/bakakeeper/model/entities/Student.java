package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

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

    private Map<String, String> dataSQL;
    private Map<EBakaLDAPAttributes, String> dataLDAP;

    // TODO metoda reset/prepare? Pr.Jm.##C_TR_VYK

    /**
     * Konstruktor z páru hrubých SQL a LDAP dat.
     *
     * @param dataSQL hrubá data z evidence
     * @param dataLDAP hrubá data z adresáře
     */
    protected Student(Map<String, String> dataSQL, Map<EBakaLDAPAttributes, String> dataLDAP) {
        this.dataSQL = dataSQL;
        this.dataLDAP = dataLDAP;
    }

    /**
     * Resetování žákovského hesla do původního tvaru
     * Pr.Jm.##
     *
     * @return úspěch operace
     */
    public Boolean resetPassword() {

        // nové heslo = Pr.Jm.##
        String newPassword = BakaUtils.removeAccents(this.dataSQL.get(EBakaSQL.F_GUA_SURNAME.basename())
                .substring(0, 2))
                + "."
                + BakaUtils.removeAccents(this.dataSQL.get(EBakaSQL.F_GUA_GIVENNAME.basename())
                .substring(0, 2))
                + "."
                + String.format("%2d", Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_CLASS_ID.basename())));

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

        if (forceChange) {
            return BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_REQCHANGE.value());
        } else {
            return BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_LASTSET.value());
        }

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
        String newClassSGdn = "CN=Zaci-Trida-" + year + letter.toUpperCase() + "," + Settings.getInstance().getLDAP_baseStudentGroups();
        String newTitle = year + "." + letter.toUpperCase();

        // přesun do OU
        BakaADAuthenticator.getInstance().moveObject(this.getDN(), newClassOU, false);

        // změna bezpečnostní skupiny žáka
        // odebrání ze všech skupin
        BakaADAuthenticator.getInstance().removeObjectFromAllGroups(this.getDN());
        // přidání do základní skupiny žáků
        String baseGroup = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
        BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), baseGroup);
        // přidání do nové skupiny třídy
        BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), newClassSGdn);

        // změna pracovního zařazení
        BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(), EBakaLDAPAttributes.TITLE, newTitle);

        // TODO - ověření výsledku
        return true;
    }

    @Override
    public String getDN() {
        return this.dataLDAP.get(EBakaLDAPAttributes.DN.attribute());
    }

    @Override
    public String getExtensionAttribute(Integer attrNum) {
        return this.dataLDAP.get(EBakaLDAPAttributes.EXT01.attribute());
    }

    // TODO
    @Override
    public Boolean setExtensionAttribute(Integer attrNum, String value) {
        return null;
    }

    @Override
    public String getInternalID() {
        return this.dataSQL.get(EBakaSQL.F_STU_ID.basename());
    }
}
