package cz.zsstudanka.skola.bakakeeper.model.entities;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordLDAP;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IRecordSQL;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUser;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Model žáka. Žák je reprezentován jako aktivní žák v evidenci a jako LDAP záznam uživatele
 * v aktivních OU. Je vždy členem základní skupiny žáků a dále členem beżpečnostní skupiny
 * konkrétní třídy.
 *
 * @author Jan Hladěna
 */
public class Student implements IRecordLDAP, IRecordSQL, IUser {

    /** maximální počet pokusů o vytvoření nového účtu */
    private static final int MAX_LIMIT = 10;

    /** záznam je pouze částečný */
    private Boolean partial = false;

    private DataSQL dataSQL;
    private DataLDAP dataLDAP;

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
     * Ověření plnohodnotného záznamu.
     *
     * @return záznam není parciální
     */
    public Boolean isValid() {
        return !this.partial;
    }

    /**
     * Resetování žákovského hesla do původního tvaru
     *
     * @return úspěch operace
     */
    public Boolean resetPassword() {

        // neplatný účet
        if (this.partial || this.dataLDAP == null) {
            return false;
        }

        int attempt = 0;
        boolean passwordSet = false;

        while (!passwordSet) {

            // TODO prokolovat změnu hesla -> výstup v případě jiných než prvních pokusů

            String newPassword = BakaUtils.nextPassword(
                    this.dataSQL.get(EBakaSQL.F_GUA_SURNAME.basename()),
                    this.dataSQL.get(EBakaSQL.F_GUA_GIVENNAME.basename()),
                    Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename())),
                    Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_CLASS_ID.basename())),
                    attempt
            );

            // TODO koronavirus + politika 2021 -- možná nebude vhodné vyžadovat okamžitou změnu hesla
            //passwordSet = setPassword(newPassword, true);
            passwordSet = setPassword(newPassword, false);
            attempt++;
        }

        return passwordSet;
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

        boolean set = true;

        set &= BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                EBakaLDAPAttributes.PW_UNICODE,
                newPassword
        );

        // heslo nebylo možné nastavit
        if (!set) {
            return false;
        }

        if (forceChange) {
            set &=  BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_REQCHANGE.value());
        } else {
            set &=  BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(),
                    EBakaLDAPAttributes.PW_LASTSET,
                    EBakaLDAPAttributes.PW_LASTSET.value());
        }

        return set;
    }

    /**
     * Přesunutí aktivního žáka do jiné třídy. Přesune fyzicky objekt definovaný
     * dn do požadované OU, nastaví požadované členství ve skupinách zabezpečení
     * a změní atribut pracovního zařazení ("title") na označení třídy
     * ve tvaru {X}.{Y}.
     *
     * @param year ročník, očekává se číslo 1-9
     * @param letter písmeno třídy, očekává se A-E
     * @return úspěch operace
     */
    public Boolean moveToClass(Integer year, String letter) {

        // neplatný účet
        if (this.partial) {
            return false;
        }

        Boolean result = true;

        String newClassOU = "OU=Trida-" + letter.toUpperCase() + ",OU=Rocnik-" + year + "," + Settings.getInstance().getLDAP_baseStudents();
        String newClassSGdn = "CN=Zaci-Trida-" + year + letter.toUpperCase() + "," + Settings.getInstance().getLDAP_baseStudentGroups();
        String newTitle = year + "." + letter.toUpperCase();

        // změna bezpečnostní skupiny žáka
        // odebrání ze všech skupin
        BakaADAuthenticator.getInstance().removeObjectFromAllGroups(this.getDN());
        // přidání do základní skupiny žáků
        String baseGroup = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
        BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), baseGroup);
        // přidání do nové skupiny třídy
        BakaADAuthenticator.getInstance().addObjectToGroup(this.getDN(), newClassSGdn);

        // změna pracovního zařazení
        result &= BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(), EBakaLDAPAttributes.TITLE, newTitle);

        // konečný přesun do OU
        result &= BakaADAuthenticator.getInstance().moveObject(this.getDN(), newClassOU, false);

        return result;
    }

    /**
     * Zjednodušená metoda pro práci s řetězcovým označením cílové třídy.
     *
     * @param classYear ročník
     * @param classLetter písmeno třídy
     * @return výsledek operace
     */
    public Boolean moveToClass(String classYear, String classLetter) {
        return moveToClass(Integer.parseInt(classYear), classLetter);
    }

    /**
     * Zjednodušená metoda pro práci s řetězcovým literálem cílové třídy.
     * Neprovádí se kontrola vstupu.
     *
     * @param newClass označení třídy ve tvaru #.Y, např. "1.A"
     * @return výsledek operace
     */
    public Boolean moveToClass(String newClass) {
        return moveToClass(newClass.split(".")[0], newClass.split(".")[1]);
    }

    /**
     * Vytvoření nového LDAP účtu z dat získaných z evidence.
     *
     */
    protected void initializeAccount() {

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
        int dnAttempt = 0;
        Boolean dnOccupied;

        // počáteční název
        String cn = this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()) + " " + this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename());
        String dn = "CN=" + cn + "," + targetOU;

        do {
            dnAttempt++;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_LDAP, "Pokus č. " + dnAttempt + ": navrhuje se nové DN [" + dn + "].");
            }

            dnOccupied = BakaADAuthenticator.getInstance().checkDN(dn);

            if (dnOccupied) {
                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Název je obsazen. Bude vygenerován nový.");
                }

                dn = BakaUtils.nextDN(dn);
            }

            cn = BakaUtils.parseCN(dn);

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
        newData.put(EBakaLDAPAttributes.PW_UNICODE.attribute(), BakaUtils.createInitialPassword(this.dataSQL.get(EBakaSQL.F_STU_SURNAME.basename()), this.dataSQL.get(EBakaSQL.F_STU_GIVENNAME.basename()), Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename())), Integer.parseInt(this.dataSQL.get(EBakaSQL.F_STU_CLASS_ID.basename()))));

        BakaADAuthenticator.getInstance().createNewUser(cn, targetOU, newData);

        // zařazení do skupin
        String baseGRdn = "CN=Skupina-Zaci," + Settings.getInstance().getLDAP_baseGlobalGroups();
        String classSCdn = "CN=Zaci-Trida-" + this.dataSQL.get(EBakaSQL.F_STU_CLASS.basename()).replace(".", "") + "," + Settings.getInstance().getLDAP_baseStudentGroups();
        BakaADAuthenticator.getInstance().addObjectToGroup(dn, baseGRdn);
        BakaADAuthenticator.getInstance().addObjectToGroup(dn, classSCdn);

        this.dataLDAP = newData;
    }

    /**
     * Provedení kontroly a synchronizace dat z evidence do adresáře.
     * V případě změny jména žáka je provedena pouze změna údajů o příjmení, jménu
     * a o zobrazovaném jménu. CN objektu v LDAP adresáři zůstává původní, jak bylo
     * zavedeno při prvotní inicializaci.
     *
     * @param repair provést po kontrole opravy
     * @return výsledek synchronizace
     */
    public Boolean sync(Boolean repair) {

        if (this.partial) {

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Parciální účet žáka není možné kontrolovat.");
            }

            return null;
        }

        Boolean result = true;

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log(EBakaLogType.LOG_INFO, "Probíhá kontrola údajů žáka ("+ getSQLdata(EBakaSQL.F_STU_CLASS) +") "+ getSQLdata(EBakaSQL.F_STU_SURNAME) + " " + getSQLdata(EBakaSQL.F_STU_GIVENNAME) + ".");
        }

        // příjmení
        if (Settings.getInstance().beVerbose()) {
            ReportManager.logWait(EBakaLogType.LOG_TEST, "Příjmení žáka");
        }
        if (getLDAPdata(EBakaLDAPAttributes.NAME_LAST).equals(getSQLdata(EBakaSQL.F_STU_SURNAME))) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }

        } else {

            // TODO do budoucnosti - subrutina
            //  uložení starého záznamu jako sekundárního proxyAddresses
            //  vygenerování nového jako primárního

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + getSQLdata(EBakaSQL.F_STU_SURNAME) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.NAME_LAST) + "].");
            }

            if (repair) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu příjmení.");
                }

                result &= setLDAPdata(EBakaLDAPAttributes.NAME_LAST, getSQLdata(EBakaSQL.F_STU_SURNAME));
                result &= setLDAPdata(EBakaLDAPAttributes.NAME_DISPLAY, getSQLdata(EBakaSQL.F_STU_SURNAME) + " " + getSQLdata(EBakaSQL.F_STU_GIVENNAME));
            } else {
                result = false;
            }
        }

        // jméno
        if (Settings.getInstance().beVerbose()) {
            ReportManager.logWait(EBakaLogType.LOG_TEST, "Jméno žáka");
        }
        if (getLDAPdata(EBakaLDAPAttributes.NAME_FIRST).equals(getSQLdata(EBakaSQL.F_STU_GIVENNAME))) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }

        } else {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + getSQLdata(EBakaSQL.F_STU_GIVENNAME) + "], získaná hodnota [" + getLDAPdata(EBakaLDAPAttributes.NAME_FIRST) + "].");
            }

            if (repair) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu jména.");
                }

                result &= setLDAPdata(EBakaLDAPAttributes.NAME_FIRST, getSQLdata(EBakaSQL.F_STU_GIVENNAME));
                result &= setLDAPdata(EBakaLDAPAttributes.NAME_DISPLAY, getSQLdata(EBakaSQL.F_STU_SURNAME) + " " + getSQLdata(EBakaSQL.F_STU_GIVENNAME));
            } else {
                result = false;
            }
        }

        // ročník + třída
        if (Settings.getInstance().beVerbose()) {
            ReportManager.logWait(EBakaLogType.LOG_TEST, "Třída žáka");
        }
        if (BakaUtils.classStringFromDN(getDN()).equals(getSQLdata(EBakaSQL.F_STU_CLASS))) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }

        } else {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Očekáváno [" + getSQLdata(EBakaSQL.F_STU_CLASS) + "], získaná hodnota [" + BakaUtils.classStringFromDN(getDN()) + "].");
            }

            if (repair) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_INFO, "Proběhne pokus o opravu zařazení do třídy.");
                }

                result &= moveToClass(getSQLdata(EBakaSQL.F_STU_BK_CLASSYEAR), getSQLdata(EBakaSQL.F_STU_BK_CLASSLETTER));
            } else {
                result = false;
            }

        }

        // TODO - komplexní subrutina; přesun pod podrobný audit
        // UAC
        result &= checkUAC(repair);

        return result;
    }

    /**
     * Subrutina - kontrola a oprava UAC žákovského účtu.
     *
     * @param repair provedení opravy v případě neshody.
     * @return výsledek operace
     */
    public Boolean checkUAC(Boolean repair) {

        // TODO
        if (Settings.getInstance().beVerbose()) {
            ReportManager.log(EBakaLogType.LOG_TEST, "UAC uživatelského účtu");
        }

        boolean result = true;

        // 1) heslo nikdy nevyprší
        // (nonekvivalence)
        Boolean pwdNoExpire = !(Settings.getInstance().pwdNoExpire().contains(BakaUtils.classYearFromDn(this.getDN())) ^ EBakaUAC.DONT_EXPIRE_PASSWORD.checkFlag(this.getUAC()));

        if (!pwdNoExpire) {

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, Settings.getInstance().pwdNoExpire().contains(BakaUtils.classYearFromDn(this.getDN())) ? "Očekáván příznak [ DONT_EXPIRE_PASSWORD ]." : "Očekáván prázdný příznak [ DONT_EXPIRE_PASSWORD ].");
            }

            // TODO
            if (repair) { // || true) {

                if (Settings.getInstance().debugMode()) {
                    ReportManager.logWait(EBakaLogType.LOG_DEBUG, "Proběhne pokus o opravu");
                }

                boolean repNoExpire = BakaADAuthenticator.getInstance().replaceAttribute(
                        this.getDN(),
                        EBakaLDAPAttributes.UAC,
                        String.format("%d",
                                Settings.getInstance().pwdNoExpire().contains(BakaUtils.classYearFromDn(this.getDN()))
                                ? EBakaUAC.DONT_EXPIRE_PASSWORD.setFlag(this.getUAC()) : EBakaUAC.DONT_EXPIRE_PASSWORD.clearFlag(this.getUAC())
                        )
                );

                if (Settings.getInstance().debugMode()) {
                    if (repNoExpire) {
                        ReportManager.logResult(EBakaLogType.LOG_OK);
                    } else {
                        ReportManager.logResult(EBakaLogType.LOG_ERR_DEBUG);
                    }
                }

                result &= repNoExpire;

            } else {
                // TODO vyažadována oprava
                ReportManager.log(EBakaLogType.LOG_WARN, "UAC není ve shodě s nastavením, ale oprava nebyla vyžádána.");
            }

        } else {
            // TODO OK

        }


        // TODO AD ACE DACL
        // 2) uživatel nemůže měnit heslo


        if (result) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            }
        } else {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
            }
        }

        return result;
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
     * TODO podrobný audit
     * - párování
     * - skupiny
     * - UAC
     * - přihlášení
     * - mail + proxy
     * - vždy odeslat report
     * @return
     */
    public Boolean audit() {
        return null;
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
        result &= BakaADAuthenticator.getInstance().moveObject(this.getDN(), newAlumniOU, true, true);

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
     * Třída žáka ve tvaru X.Y
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

    /**
     * Získání UAC žákovského účtu.
     *
     * @return UAC žákovského účtu
     */
    public String getUAC() {
        if (this.partial) {
            if (this.dataLDAP == null) {
                return null;
            }
        }
        return this.dataLDAP.get(EBakaLDAPAttributes.UAC.attribute()).toString();
    }

    @Override
    public String getLDAPdata(EBakaLDAPAttributes attr) {

        if (this.dataLDAP != null) {
            return (this.dataLDAP.containsKey(attr.attribute())) ? this.dataLDAP.get(attr.attribute()).toString() : null;
        }

        return null;
    }

    @Override
    public Boolean setLDAPdata(EBakaLDAPAttributes attr, String value) {

        if (this.dataLDAP != null) {
            return BakaADAuthenticator.getInstance().replaceAttribute(getDN(), attr, value);
        }

        return false;
    }

    @Override
    public String getExtensionAttribute(Integer attrNum) {

        // TODO
        if (attrNum != 1) {
            return null;
        }

        if (this.partial) {
            if (this.dataLDAP == null) {
                return this.dataSQL.get(EBakaSQL.F_STU_ID.basename());
            }
        }

        return this.dataLDAP.get(EBakaLDAPAttributes.EXT01.attribute()).toString();
    }

    @Override
    public Boolean setExtensionAttribute(Integer attrNum, String value) {

        if (this.partial || this.dataLDAP == null) {
            return false;
        }

        // TODO
        if (attrNum != 1) {
            return false;
        }

        return BakaADAuthenticator.getInstance().replaceAttribute(this.getDN(), EBakaLDAPAttributes.EXT01, value);
    }

    @Override
    public String getInternalID() {
        return this.dataSQL.get(EBakaSQL.F_STU_ID.basename());
    }

    @Override
    public String getSQLdata(EBakaSQL field) {

        if (this.dataSQL != null) {
            return (this.dataSQL.get(field.basename()).equals(EBakaSQL.NULL)) ? null : this.dataSQL.get(field.basename());
        }

        return null;
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
