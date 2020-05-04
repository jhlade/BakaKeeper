package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Žák. Je reprezentován jako aktivní žák v evidenci a jako LDAP záznam uživatele (512)
 * v aktivních OU. Je členem základní skupiny žáků a členem skupiny konkrétní třídy.
 *
 * implements IRecordLDAP, IRecordSQL
 *
 * @author Jan Hladěna
 */
public class Student implements IRecordLDAP, IRecordSQL {

    private DataSQL dataSQL;
    private DataLDAP dataLDAP;

    private Boolean partial = false;

    /**
     * Konstruktor z páru hrubých SQL a LDAP dat.
     *
     * @param dataSQL hrubá data z evidence
     * @param dataLDAP hrubá data z adresáře
     */
    protected Student(DataSQL dataSQL, DataLDAP dataLDAP) {

        // parciální konstrukce pro pozdější zpracování
        if (dataSQL == null  || dataLDAP == null) {
            this.partial = true;
        }

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

        // neplatný účet
        if (this.partial) {
            return false;
        }

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

        // neplatný účet
        if (this.partial) {
            return false;
        }

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

        // neplatný účet
        if (this.partial) {
            return false;
        }

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

    /**
     * Vytvoření nového LDAP účtu z dat získaných z evidence.
     *
     */
    protected void init() {

        // pouze parciální data
        if (!this.partial || this.dataLDAP != null) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Inicializace nového účtu je možná pouze z parciálních SQL dat.");
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Bylo předáno " + this.dataLDAP.size() + " atributů.");
            }

            return;
        }

        // základní údaje
        String targetOU = "OU=Trida-" +
                this.dataSQL.get(EBakaSQL.F_STU_BK_CLASSLETTER.basename())
                + ",OU=Rocnik-" +
                this.dataSQL.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename())
                + "," + Settings.getInstance().getLDAP_baseStudents();

        // ověření unikátního DN + smyčka na generování nového DN
        final int MAX_LIMIT = 10;
        int dnAttempt = 0;
        Boolean dnOccupied;
        String cn;
        String dn;

        do {
            dnAttempt++;

            String dnUniq;

            if (dnAttempt == 1) {
                dnUniq = "";
            } else {
                dnUniq = " " + String.format("%02d", dnAttempt - 1);
            }

            // "CN=Novák Adam 1"
            cn = this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename())
                    + " "
                    + this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename())
                    + dnUniq;
            dn = "CN=" + cn + "," + targetOU;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_LDAP, "Pokus č. " + dnAttempt + ": navrhuje se DN [" + dn + "].");
            }

            dnOccupied = BakaADAuthenticator.getInstance().checkDN(dn);

            if (dnOccupied) {
                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Název je obsazen.");
                }
            }

        } while (dnOccupied && dnAttempt <= MAX_LIMIT);

        // překročení limitu
        if (dnAttempt >= MAX_LIMIT) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k závažné chybě - byl překročen maximální limit návrhů nového unikátního jména v adresáři.");
        }

        DataLDAP newData = new DataLDAP();
        newData.put(EBakaLDAPAttributes.CN.attribute(), cn);
        newData.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()) + " " + this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename()));
        newData.put(EBakaLDAPAttributes.NAME_LAST.attribute(), this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()));
        newData.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename()));
        newData.put(EBakaLDAPAttributes.EXT01.attribute(), this.dataSQL.get(EBakaSQL.F_STU_ID.basename()));
        newData.put(EBakaLDAPAttributes.TITLE.attribute(), this.dataSQL.get(EBakaSQL.F_STU_CLASS.basename()));
        newData.put(EBakaLDAPAttributes.MAIL.attribute(), this.dataSQL.get(EBakaSQL.F_STU_MAIL.basename()));

        newData.put(EBakaLDAPAttributes.UPN.attribute(), this.dataSQL.get(EBakaSQL.F_STU_MAIL.basename()));
        newData.put(EBakaLDAPAttributes.UID.attribute(), this.dataSQL.get(EBakaSQL.F_STU_MAIL.basename()));
        newData.put(EBakaLDAPAttributes.LOGIN.attribute(), BakaUtils.createSAMloginFromUPNbase(
                this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()),
                this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename()),
                this.dataSQL.get(EBakaSQL.F_STU_MAIL.basename())
        ));

        newData.put(EBakaLDAPAttributes.UAC.attribute(), EBakaUAC.NORMAL_ACCOUNT.toString());
        newData.put(EBakaLDAPAttributes.PW_UNICODE.attribute(), BakaUtils.createInitialPassword(this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()), this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename()), Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_CLASS_ID.basename()))));

        BakaADAuthenticator.getInstance().createNewUser(cn, targetOU, newData);

        // zařazení do skupin
        String baseGRdn = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
        String classSCdn = "CN=Zaci-Trida-" + this.dataSQL.get(EBakaSQL.F_STU_CLASS.basename()).replace(".", "") + "," + Settings.getInstance().getLDAP_baseStudentGroups();
        BakaADAuthenticator.getInstance().addObjectToGroup(dn, baseGRdn);
        BakaADAuthenticator.getInstance().addObjectToGroup(dn, classSCdn);
    }

    /**
     * Provedení kontroly a synchronizace dat z evidence do adresáře.
     *
     * TODO
     * @param repair provést po kontrole opravy
     * @return výsledek synchronizace
     */
    public Boolean sync(Boolean repair) {
        return null;
    }

    /**
     * Kontrola údajů žáka bez vynucení synchronizace.
     *
     * @return výsledek kontroly údajů žáka
     */
    public Boolean check() {
        return sync(false);
    }

    /**
     * Přesunutí parciálního účtu mezi vyřazené.
     *
     * Provede se uzamčení uživatelského účtu, nastavení atributu "title" na novou hodnotu "ABS {rok}",
     * odebrání ze všech současných bezpečnostních a distribučních skupin a fyzické přesunutí účtu
     * do organizační jednotky vyřazených žáků v aktuálním kalendářním roce,
     * např. OU=2020,OU=StudiumUkonceno,OU=Zaci,OU=Uzivatele,OU=Skola,DC=zsstu,DC=local.
     *
     * @return výsledek operace
     */
    public Boolean retireAccount() {

        // pouze nekompletní účet
        if (!this.partial) {
            return false;
        }

        Boolean result = true;

        // 1a) odebrání pracovního zařazení a nastavení "ABS {yyyy}"
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy");

        result &= BakaADAuthenticator.getInstance().replaceAttribute(
                this.getDN(),
                EBakaLDAPAttributes.TITLE,
                "ABS " + formatter.format(new Date())
        );

        // 1b) uzamčení účtu
        result &= BakaADAuthenticator.getInstance().replaceAttribute(
                this.getDN(),
                EBakaLDAPAttributes.UAC,
                String.format("%d", EBakaUAC.ACCOUNTDISABLE.value()|EBakaUAC.PASSWORD_EXPIRED.value())
        );

        // 2) odebrání ze skupin
        BakaADAuthenticator.getInstance().removeObjectFromAllGroups(this.getDN());

        // 3) přesunutí účtu do absolventské OU
        String newAlumniOU = "OU=" + formatter.format(new Date()) + "," + Settings.getInstance().getLDAP_baseAlumni();
        BakaADAuthenticator.getInstance().moveObject(this.getDN(), newAlumniOU, true);

        return result;
    }

    /**
     * Příjmení žáka.
     *
     * @return příjmení žáka
     */
    public String getSurname() {
        if (this.dataSQL == null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.NAME_LAST.attribute()).toString();
        }

        return this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename());
    }

    /**
     * Jméno žáka.
     *
     * @return jméno žáka
     */
    public String getGivenName() {
        if (this.dataSQL == null) {
            return this.dataLDAP.get(EBakaLDAPAttributes.NAME_FIRST.attribute()).toString();
        }

        return this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename());
    }

    /**
     * Třída žáka
     *
     * @return třída žáka
     */
    public String getStudentClass() {
        if (this.dataSQL == null) {
            return BakaUtils.classYearFromDn(this.getDN()).toString() + "." + BakaUtils.classLetterFromDn(this.getDN());
        }

        return this.dataSQL.get(EBakaSQL.F_STU_CLASS.basename());
    }

    @Override
    public String getDN() {

        if (this.partial) {
            if (this.dataLDAP == null) {
                return null;
            }
        }
        return this.dataLDAP.get(EBakaLDAPAttributes.DN.attribute()).toString();
    }

    // TODO
    @Override
    public String getExtensionAttribute(Integer attrNum) {

        if (this.partial) {
            if (this.dataLDAP == null) {
                return this.dataSQL.get(EBakaSQL.F_STU_ID.basename());
            }
        }

        return this.dataLDAP.get(EBakaLDAPAttributes.EXT01.attribute()).toString();
    }

    // TODO
    @Override
    public Boolean setExtensionAttribute(Integer attrNum, String value) {

        if (this.partial) {
            return null;
        }

        return null;
    }

    @Override
    public String getInternalID() {
        return this.dataSQL.get(EBakaSQL.F_STU_ID.basename());
    }

    @Override
    public String toString() {
        StringBuilder student = new StringBuilder();
        student.append("=== STUDENT ===\n");

        if (this.partial) {
            student.append("[i] Žákovský účet s parciálními daty.\n");
        }

        student.append(this.getSurname() + " " + this.getGivenName() + "\n");
        student.append(this.getStudentClass() + "\n");

        student.append("=== /STUDENT ===\n");

        return student.toString();
    }
}
