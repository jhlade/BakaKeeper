package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaEvents;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.*;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.*;

/**
 * Synchronizační rutiny.
 *
 * @author Jan Hladěna
 */
public class Sync {

    /** SQL data - evidence žáků */
    private SQLrecords catalog;

    /** SQL data - třídní učitelé z evidence */
    private SQLrecords faculty;

    /** LDAP data - adresář žáků */
    private LDAPrecords directory;

    /** LDAP data - vyřazení žáci */
    private LDAPrecords alumni;

    /** LDAP data - zaměstnanci */
    private LDAPrecords directoryFaculty;

    /** LDAP data - kontaky na zákonné zástupce */
    private LDAPrecords contacts;

    /** maximální limit počtu pokusů o vytvoření adresy */
    private static final int LIMIT = 10;


    /**
     * Výchozí konstruktor s naplněním dat.
     */
    public Sync() {
        reset();
    }

    /**
     * Inicializace synchronizačních rutin - naplnění daty.
     */
    private void reset() {
        // SQL
        this.loadCatalog();

        // LDAP
        this.loadDirectoryStudents();
        this.loadDirectoryAlumni();
        this.loadDirectoryFaculty();
        this.loadDirectoryContacts();

        // TODO 2020-05 vývoj
        devel();
    }

    /**
     * Načtení dat z evidence.
     */
    private void loadCatalog() {
        this.catalog = new SQLrecords(null, null);
        this.faculty = new SQLrecords(true);
    }

    /**
     * Načtení účtů žáků z adresáře.
     */
    private void loadDirectoryStudents() {
        this.directory = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
    }

    /**
     * Načtení vyřazených žákovských účtů z adresáře.
     */
    private void loadDirectoryAlumni() {
        this.alumni = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
    }

    /**
     * Načtení účtů zaměstnanců z adresáře.
     */
    private void loadDirectoryFaculty() {
        this.directoryFaculty = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
    }

    /**
     * Načtení seznamu existujících kontaktů z adresáře.
     */
    private void loadDirectoryContacts() {
        this.contacts = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);
    }

    /**
     * Pouze pro vývojové účely.
     * @deprecated
     */
    public void devel() {
        String ročník = "1";
        String třída = "E";

        DataSQL rowData;

        // 1.E
        // 1, Školáček Malý, DEV01
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "1");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Malý");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Školáček Plyšáček");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV01");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "skolacek.maly1@zs-studanka.cz");
        rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());
        rowData.put(EBakaSQL.F_GUA_BK_ID.basename(), "DEV01G");
        rowData.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Malá");
        rowData.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Julie");
        rowData.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "606123456");
        rowData.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "julie.kocic@joutsen.cz");
        this.catalog.addRecord("DEV01", rowData);

        // 2, Aaadam Testový, DEV02
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "2");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Testový");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV02");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "testovy.aaadam1@zs-studanka.cz");
        rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());
        rowData.put(EBakaSQL.F_GUA_BK_ID.basename(), "DEV02G");
        rowData.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Testový");
        rowData.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Patrik");
        rowData.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "606654321");
        rowData.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "patrik.testovy@joutsen.cz");
        this.catalog.addRecord("DEV02", rowData);

        // 11, Aaadam Nový, DEV04 - bude vytvořen a smazán
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "11");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Nový");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV04");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "novy.aaadam2@zs-studanka.cz");
        rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());
        rowData.put(EBakaSQL.F_GUA_BK_ID.basename(), "DEV04G");
        rowData.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Nová");
        rowData.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Marcela");
        rowData.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "123456789");
        rowData.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "macek.new@joutsen.cz");
        this.catalog.addRecord("DEV04", rowData);

        // 12, Aaadam Nový, DEV05 - shodné jméno
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "12");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Nový");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV05");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "novy.aaadam3@zs-studanka.cz");
        rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());
        rowData.put(EBakaSQL.F_GUA_BK_ID.basename(), "DEV05G");
        rowData.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Nový");
        rowData.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Kocour");
        rowData.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "255256250");
        rowData.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "mike666@joutsen.cz");
        this.catalog.addRecord("DEV05", rowData);

        // 27, Aaadam Expirovaný, DEV03
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "27");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), "1" + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), "1"); // 1.E
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Expirovaný");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV03");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "expirovany.aaadam1@zs-studanka.cz");
        rowData.put(EBakaSQL.BK_FLAG.basename(), EBakaSQL.LIT_FALSE.basename());
        rowData.put(EBakaSQL.F_GUA_BK_ID.basename(), "DEV03G");
        rowData.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Expirovaná");
        rowData.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Markéta");
        rowData.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "999324564");
        rowData.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "makyna.awesome@joutsen.cz");
        this.catalog.addRecord("DEV03", rowData);

        // Třídní učitel 1.E
        rowData = new DataSQL();//HashMap<String, String>();
        rowData.put(EBakaSQL.F_CLASS_LABEL.basename(), "1.E");
        rowData.put(EBakaSQL.F_FAC_SURNAME.basename(), "Hladěna");
        rowData.put(EBakaSQL.F_FAC_GIVENNAME.basename(), "Jan");
        rowData.put(EBakaSQL.F_FAC_EMAIL.basename(), "jan.hladena@zs-studanka.cz");
        rowData.put(EBakaSQL.F_FAC_ID.basename(), "DEVCT01");
        this.faculty.addRecord("DEVCT01", rowData);
    }

    /**
     * Kontrola a zavedení nových žáků - inicializace nového záznamu v Bakalářích.
     *
     * Nový žák = žák v evidenci bez platné školní e-mailové adresy (UPN). Pokud platná školní adresa
     * v poli dbo.zaci.E_MAIL neexistuje, bude po ověření její dostupnosti vytvořena a zapsána do evidence.
     * Existuje-li nespárovaný LDAP účet pro vytvořenou adresu, bude proveden pokus o spárování obou záznamů
     * na základě porovnání dostupných informací. Je-li splněno kritérium pro párování, interní kód žáka
     * z pole dbo.zaci.INTERN_KOD bude zapsán do LDAP atributu extensionAttribute1 porovnávaného záznamu.
     *
     * @param update zapsat novou e-mailovou adresu do evidence a provést párování
     */
    public void actionInit(boolean update) {

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log("Začíná prohledávání evidence - žáci bez platných školních e-mailových adres.");
        }

        // dočasné úložiště návrhů na novou adresu
        ArrayList<String> occupied = new ArrayList<>();

        // hlavní smyčka
        this.catalog.resetIterator();
        while (this.catalog.iterator().hasNext()) {

            String studentID = this.catalog.iterator().next();

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Proběhne zpracování žáka ID = [" + studentID + "].");
            }

            if (!this.catalog.get(studentID)
                    .get(EBakaSQL.F_STU_MAIL.basename())
                    .toLowerCase()
                    .contains(Settings.getInstance().getMailDomain())
            ) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Žák [" + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_CLASS.basename()) + ", č. " + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_CLASS_ID.basename()) + "]: " + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_SURNAME.basename()) + " "
                            + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_GIVENNAME.basename()) + ", ID = [" + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_ID.basename()) + "], nemá v evidenci vyplněnou platnou školní e-mailovou adresu.");
                }

                String proposed;
                Integer attempt = 0;
                Boolean inUse;

                do {
                    inUse = false;

                    // návrh nové adresy
                    proposed = BakaUtils.createUPNfromName(
                            this.catalog.get(studentID)
                                    .get(EBakaSQL.F_STU_SURNAME.basename()),
                            this.catalog.get(studentID)
                                    .get(EBakaSQL.F_STU_GIVENNAME.basename()),
                            Settings.getInstance().getMailDomain(),
                            attempt);

                    // inkrementace pokusu
                    attempt++;

                    if (Settings.getInstance().debugMode()) {
                        ReportManager.log(EBakaLogType.LOG_DEBUG, "Navrhuje se: " + proposed);
                    }

                    if (occupied.contains(proposed)) {
                        inUse = true;
                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_DEBUG, "Navrhovaná adresa je již obsazena předchozím pokusem.");
                        }
                        continue;
                    }

                    if (directoryFaculty.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_DEBUG, "Navrhovaná adresa je obsazena zaměstnancem.");
                        }
                        continue;
                    }

                    if (alumni.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_DEBUG, "Navrhovaná adresa je obsazena vyřazeným žákem.");
                        }
                        continue;
                    }

                    if (directory.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_DEBUG, "Navrhovaná adresa je obsazena aktivním žákem.");
                        }

                        // porovnání a provedení pokusu o párování záznamů
                        // adresa je použita, pokud nebylo možné provést párování
                        // párování = došlo k platnému zápisu do LDAP
                        inUse = !attemptToPair(studentID, proposed, update);
                    }

                    // hotovo, zápis dat
                    if (!inUse) {
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa [" + proposed + "] je k dispozici a bude ji možné zapsat do evidence.");
                        }

                        // provedení změn
                        if (update) {
                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_SQL, "Do transakční fronty budou přidána data [" + proposed + "] pro ID = [" + studentID + "]");
                            }

                            HashMap<EBakaSQL, String> data = new HashMap<>();
                            data.put(EBakaSQL.F_STU_MAIL, proposed);
                            catalog.addWriteData(studentID, data);

                            // TODO
                            // [1.B] č. 4, Novák Adam - novak.adam@zs-studanka.cz
                            ReportManager.getInstance().addEvent(EBakaEvents.CATALOG_INIT, "[" +
                                    catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + "] č. " +
                                    catalog.get(studentID).get(EBakaSQL.F_STU_CLASS_ID.basename()) + ", " +
                                    catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) + " " +
                                    catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) + " - " +
                                    proposed
                            );
                        } else {
                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_SQL, "Nebyl potvrzen zápis do ostrých dat, změny budou zahozeny.");
                            }
                        }
                    }

                } while (inUse && attempt <= LIMIT);

                // překročení limitu
                if (attempt >= LIMIT) {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Došlo k závažné chybě - byl překročen maximální limit návrhů nové e-mailové adresy.");
                }

            } // žák již má adresu v doméně školy
        } // smyčka

        // provedení zápisu do ostrých dat
        if (update) {
            if (catalog.writesRemaining() > 0) {

                // provedení zápisu změn do evidence
                if (catalog.commit()) {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Proběhl zápis dat do evidence.");
                    }

                } else {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Nebylo možné provést zápis dat do evidence.");
                    }
                }

            }

            // provedení zápisu změn do AD
            if (directory.writesRemaining() > 0) {
                if (directory.commit()) {

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Proběhl zápis dat do adresáře AD.");
                    }

                } else {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Nebylo možné provést zápis dat do adresáře AD.");
                    }
                }
            }
        } // provedení zápisu

    }

    /**
     * Provedení pokusu o párování žáka podle SQL záznamu s existujícím LDAP účtem.
     * Kritériem pro úspěšné párování je shodné jméno, příjmení a ročník žáka. Pokud je ho dosaženo,
     * LDAP záznamu bude upraven atribut extensionAttribute1 na hodnototu interního kódu žáka ze SQL.
     *
     * @param idSQL identifikátor žáka v SQL - musí vždy existovat
     * @param idLDAP odpovídající UPN žáka v LDAP - musí vždy existovat
     * @param update provést párování, pokud jsou podmínky splněny
     * @return výsledek operace
     */
    private Boolean attemptToPair(String idSQL, String idLDAP, Boolean update) {

        Boolean result = false;

        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "Proběhne pokus o párování žáka ID [" + idSQL + "] proti záznamu UPN [" + idLDAP + "].");
        }

        // ověření (ne)existence atributu EXT01
        if (!directory.get(idLDAP).containsKey(EBakaLDAPAttributes.EXT01.attribute())) {

            if (!update) {
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log(EBakaLogType.LOG_WARN, "Při pokusu o párování záznamů nebyl nalezen rozšířený atribut " +
                            "pro UPN [" + idLDAP + "], avšak jeho vytvoření nebylo vyžádáno.");
                }

                return false;
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Při pokusu o párování záznamů nebyl nalezen rozšířený atribut " +
                        "pro UPN [" + idLDAP + "] . Proběhne podrobná kontrola párovacích kritérií.");
            }

            // ověřovací kritéria
            float criteriaCount = 0;
            float criteriaMet   = 0;

            Boolean testSurname = directory.get(idLDAP).get(EBakaLDAPAttributes.NAME_LAST.attribute()).equals(catalog.get(idSQL).get(EBakaSQL.F_STU_SURNAME.basename()));

            criteriaCount += 1.0;
            criteriaMet += (testSurname) ? 1.0 : 0;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "->\tPříjmení:\t[ " + ((testSurname) ? "SHODA" : "ROZDÍL" ) + " ] ("+
                        catalog.get(idSQL).get(EBakaSQL.F_STU_SURNAME.basename())
                        +" / "+
                        directory.get(idLDAP).get(EBakaLDAPAttributes.NAME_LAST.attribute())
                        +")");
            }

            Boolean testName = directory.get(idLDAP).get(EBakaLDAPAttributes.NAME_FIRST.attribute()).equals(catalog.get(idSQL).get(EBakaSQL.F_STU_GIVENNAME.basename()));
            criteriaCount += 1.0;
            criteriaMet += (testName) ? 1.0 : 0;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "->\tJméno:\t\t[ " + ((testName) ? "SHODA" : "ROZDÍL" ) + " ] ("+
                        catalog.get(idSQL).get(EBakaSQL.F_STU_GIVENNAME.basename())
                        +" / "+
                        directory.get(idLDAP).get(EBakaLDAPAttributes.NAME_FIRST.attribute())
                        +")");
            }

            Boolean testYear = BakaUtils.classYearFromDn(directory.get(idLDAP).get(EBakaLDAPAttributes.DN.attribute()).toString()).toString().equals(catalog.get(idSQL).get(EBakaSQL.F_STU_BK_CLASSYEAR.basename()));
            criteriaCount += 1.0;
            criteriaMet += (testYear) ? 1 : 0;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "->\tRočník:\t\t[ " + ((testYear) ? "SHODA" : "ROZDÍL" ) + " ] ("+
                        BakaUtils.classYearFromDn(directory.get(idLDAP).get(EBakaLDAPAttributes.DN.attribute()).toString()).toString()
                        +" / "+
                        catalog.get(idSQL).get(EBakaSQL.F_STU_BK_CLASSYEAR.basename())
                        +")");
            }

            Boolean testLetter = BakaUtils.classLetterFromDn(directory.get(idLDAP).get(EBakaLDAPAttributes.DN.attribute()).toString()).equals(catalog.get(idSQL).get(EBakaSQL.F_STU_BK_CLASSLETTER.basename()));
            criteriaCount += 1;
            criteriaMet += (testLetter) ? 1 : 0;

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "->\tTřída:\t\t[ " + ((testLetter) ? "SHODA" : "ROZDÍL" ) + " ] ("+
                        BakaUtils.classLetterFromDn(directory.get(idLDAP).get(EBakaLDAPAttributes.DN.attribute()).toString())
                        +" / "+
                        catalog.get(idSQL).get(EBakaSQL.F_STU_BK_CLASSLETTER.basename())
                        +")");
            }

            float pairScore = (criteriaMet / criteriaCount);
            // návrh 2020-04-19: 0.75 || (>=0.5 && ročník)
            Boolean criteria = (pairScore >= 0.75 || (pairScore >= 0.5 && testYear));

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "=>\tSkóre " + String.format("%.2f", pairScore * 100) + " %, " +
                        "celkové kritérium párování [ " + ((criteria) ? "SPLNĚNO" : "NESPLNĚNO") + " ].");
            }

            if (criteria) {
                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Do transakční fronty budou přidána data [" + idSQL + "] pro UPN = [" + idLDAP + "]");
                }

                Map<EBakaLDAPAttributes, String> data = new HashMap<>();
                data.put(EBakaLDAPAttributes.EXT01, idSQL);
                directory.addWriteData(directory.get(idLDAP).get(EBakaLDAPAttributes.DN.attribute()).toString(), data);

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Bude provedeno párování záznamů pro žáka [" +
                                    this.catalog.get(idSQL).get(EBakaSQL.F_STU_CLASS.basename()) +
                                    ", č. " + this.catalog.get(idSQL).get(EBakaSQL.F_STU_CLASS_ID.basename()) +
                                    "]: " + this.catalog.get(idSQL).get(EBakaSQL.F_STU_SURNAME.basename()) +
                                    " " +
                                    this.catalog.get(idSQL).get(EBakaSQL.F_STU_GIVENNAME.basename()) +
                                    ", ID = [" + idSQL + "].");
                }

                result = true;
            } else {

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Párování pro UPN [" +
                            idLDAP +
                            "] nebude provedeno, nebyla splněna požadovaná kritéria.");
                }

                result = false;
            }

        } else {
            // atribnut EXT01 existuje
            // výsledek přímého porování
            result = directory.get(idLDAP)
                    .get(EBakaLDAPAttributes.EXT01.attribute())
                    .equals(idSQL);

            // výsledek porovnání
            if (Settings.getInstance().debugMode()) {
                if (result) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Byla nalezena shoda.");
                } else {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Záznamy reprezentují zcela různé žáky.");
                }
            }
        }

        // výsledek párování
        return result;
    }

    /**
     * Kontrola záznamů proti adresáři a ošetření záznamů, které nemají vzájemné protějšky.
     *
     * Pokud v LDAP existuje záznam neuvedený v evidenci, bude přesunut mezi vyřazené žáky a zablokován.
     * Pokud v evidenci existuje záznam bez účtu, bude mu vytvořen nový účet s výchozím heslem.
     *
     * @param update provést plánované změny
     */
    public void actionCheck(boolean update) {

        // kompletní znovunačtení konfigurace
        reset();
        /*
        // naplění struktur - reset iterátorů
        this.catalog.resetIterator();
        this.directory.resetIterator();
        */

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log("Začíná kontrola synchronizace evidence a adresáře.");
        }

        // hlavní smyčka přes evidenci
        while (this.catalog.iterator().hasNext()) {

            String studentID = this.catalog.iterator().next();

            if (Settings.getInstance().beVerbose()) {
                ReportManager.logWait(EBakaLogType.LOG_TEST, "Kontroluje se žák " +
                                this.catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) +
                                " " +
                                this.catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) +
                                " (" + this.catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + ")"
                        );
            }

            // chyba - neexistující adresa v doméně školy, nutná prvotní inicializace
            if (!this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename()).contains(Settings.getInstance().getMailDomain())) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR);
                }

                ReportManager.log(EBakaLogType.LOG_ERR, "Žák " +
                        this.catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) +
                        " " +
                        this.catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) +
                        " (" + this.catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + ")" +
                        " nemá v evidenci vyplněnou platnou školní adresu."
                );

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Získaný údaj byl [" + this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename()) + "].");
                }

                // TODO hlášení události

                continue;
            }

            // označení záznamů
            if (this.directory.get(this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename())) != null) {
                this.catalog.setFlag(studentID, true);
                this.directory.setFlag(this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename()), true);

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_OK);
                }
            } else {
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult(EBakaLogType.LOG_ERR);
                }

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "V adresáři neexistuje účet s UPN [" + this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename()) + "].");
                }
            }
        } // smyčka přes evidenci

        // kontrola lichých částí
        LinkedHashMap<String, DataSQL> catalogUnpaired = this.catalog.getSubsetWithFlag(false);
        LinkedHashMap<String, DataLDAP> directoryUnpaired = this.directory.getSubsetWithFlag(false);

        // evidence
        if (catalogUnpaired.size() > 0) {
            ReportManager.log("V evidenci byly nalezeny záznamy bez existujících uživatelských účtů (celkem " + catalogUnpaired.size() + ").");

            // vytvoření nových účtů
            Iterator<String> catalogUnpairedIterator = catalogUnpaired.keySet().iterator();
            while (catalogUnpairedIterator.hasNext()) {

                String unpairedID = catalogUnpairedIterator.next();

                if (update) {
                    Student account = RecordFactory.newStudent(catalogUnpaired.get(unpairedID));

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Bude vytvořen nový účet žáka [" + account.getStudentClass() + "] "
                                + account.getSurname()
                                + " " + account.getGivenName() + ".");
                    }

                    // TODO vytvoření události k hlášení
                } else {
                    Student account = RecordFactory.getStudentByPair(catalogUnpaired.get(unpairedID), null);

                }

            }

        } else {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_OK, "Všechny záznamy v evidenci mají platné účty.");
            }
        }

        // adresář
        if (directoryUnpaired.size() > 0) {
            ReportManager.log("V adresáři byly nalezeny neplatné záznamy (celkem " + directoryUnpaired.size() + ").");

            // přesun neplatných žáků do vyřazených účtů
            Iterator<String> directoryUnpairedIterator = directoryUnpaired.keySet().iterator();
            while (directoryUnpairedIterator.hasNext()) {

                String unpairedUPN = directoryUnpairedIterator.next();
                Student retired = RecordFactory.getStudentByPair(null, directoryUnpaired.get(unpairedUPN));

                if (update) {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Neplatný účet [" + retired.getStudentClass() + "] "
                                + retired.getSurname()
                                + " " + retired.getGivenName() + " bude vyřazen.");
                    }

                    Boolean retire = retired.retireAccount();

                    if (retire) {
                        ReportManager.log("Neplatný účet [" + retired.getStudentClass() + "] "
                                + retired.getSurname()
                                + " " + retired.getGivenName() + " byl vyřazen.");

                        // TODO vytvoření události k hlášení
                    } else {
                        ReportManager.log(EBakaLogType.LOG_ERR, "Neplatný účet [" + retired.getStudentClass() + "] "
                                + retired.getSurname()
                                + " " + retired.getGivenName() + " nebylo možné vyřadit.");

                        // TODO vytvoření události k hlášení
                    }

                } else {
                    ReportManager.log("Neplatný účet [" + retired.getStudentClass() + "] "
                            + retired.getSurname()
                            + " " + retired.getGivenName() + " by měl být vyřazen.");

                    // TODO vytvoření události k hlášení
                }
            }

        } else {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_OK, "Všechny platné účty mají odpovídající záznam v evidenci.");
            }
        }
    }

    /**
     * Podrobná kontrola dat
     * @param repair
     */
    public void checkData(boolean repair) {
        // kompletní znovunačtení konfigurace
        reset();

        while (this.catalog.iterator().hasNext()) {

            String studentID = this.catalog.iterator().next();

            // získání páru
            Student student = RecordFactory.getStudentByPair(
                    this.catalog.get(studentID),
                    this.directory.get(this.catalog.get(studentID).get(EBakaSQL.F_STU_MAIL.basename()))
            );

            if (student.isValid()) {
                Boolean test = student.check();

                if (!test) {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Některé údaje žáka (" +
                            this.catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) +
                            ") " +
                            this.catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) +
                            " " +
                            this.catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) +
                            " se liší od záznamu v evidenci."
                            );
                }

                // pokus o opravu
                if (!test && repair) {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.logWait(EBakaLogType.LOG_VERBOSE,"Bude proveden pokus o opravu údajů žáka (" +
                                this.catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) +
                                ") " +
                                this.catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) +
                                " " +
                                this.catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()));
                    }

                    Boolean attemptRepair = student.sync(repair);

                    if (attemptRepair) {
                        // záznam opraven
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                        }
                        // TODO hlášení
                    } else {
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_ERR_VERBOSE);
                        }
                        // chyba
                        ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné provést opravu údajů.");
                        // TODO hlášení
                    }

                }
            }

        }
    }

    /**
     * Kontrola a zavedení primárních zákonných zástupců žáků do globální adresáře.
     *
     * TODO
     *
     * smazání nespárovaných/nepotřebných
     *
     * @param attemptRepair provést opravy v datech
     */
    public void syncGuardian(Boolean attemptRepair) {

        // skupiny <rodič, <DN třída>>
        HashMap<String, ArrayList<String>> guardianDL = new HashMap<>();
        // nově vytvořené kontaky - nebudou se vytvářet znovu
        ArrayList<String> newlyCreated = new ArrayList<>();

        // znovunačtení po synchronizaci dat
        this.loadDirectoryContacts();
        this.loadCatalog();

        // 1) iterátor přes aktivní žáky v evidenci + ověření/tvorba kontaktů
        Iterator<String> students = this.catalog.iterator();
        while (students.hasNext()) {

            String studentID = students.next();

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log("Probíhá zpracování zákonného zástupce žáka (" +
                        catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + ") " +
                        catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) + " " +
                        catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) + ".");
            }

            // předběžná validace dat + ukončení zpracování
            Guardian partialGuardian = RecordFactory.getGuardianByPair(catalog.get(studentID), null);
            if (!partialGuardian.validateData()) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Žák ("+
                        catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + ") " +
                        catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) + " " +
                        catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) + " " +
                        "nemá v evidenci správně vyplněné údaje primárního zákonného zástupce.");
                // TODO hlášení pro třídního/ICT
                continue;
            }

            // vytvoření seznamu tříd
            if (!guardianDL.containsKey(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename()))) {
                guardianDL.put(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename()), new ArrayList<String>());
            }

            // vytvoření DN skupiny třídy
            String classDN = "CN=Rodice-Trida-" + (catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename())).replace(".", "") + "," + Settings.getInstance().getLDAP_baseDL();

            // kontrola - více dětí ve stejné třídě
            if (!guardianDL.get(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename())).contains(classDN)) {
                guardianDL.get(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename())).add(classDN);
            }

            // ověření existence kontaktu v adresáři
            if (this.contacts.get(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename())) != null) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Záznam existuje, proběhne kontrola údajů zákonného zástupce.");
                }

                // přidání příznaku zpracování
                this.contacts.setFlag(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename()), true);

                // instance záznamu
                Guardian guardian = RecordFactory.getGuardianByPair(catalog.get(studentID), this.contacts.get(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.field())));
                Boolean guardianSync = guardian.sync(attemptRepair);

                if (guardianSync) {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log(EBakaLogType.LOG_OK, "Kontrola údajů zákonného zástupce žáka proběhla v pořádku.");
                    }
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné provést synchronizaci záznamu zákonného zástupce žáka (" +
                            catalog.get(studentID).get(EBakaSQL.F_STU_CLASS.basename()) + ") " +
                            catalog.get(studentID).get(EBakaSQL.F_STU_SURNAME.basename()) + " " +
                            catalog.get(studentID).get(EBakaSQL.F_STU_GIVENNAME.basename()) + ".");
                }

            } else {

                // záznam byl již vytvořen v předchozím pokusu
                if (newlyCreated.contains(catalog.get(studentID).get(EBakaSQL.F_GUA_BK_ID.basename()))) {
                    continue;
                }

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Zákonný zástupce v adresáři neexistuje.");
                }

                // konec zpracování
                if (!attemptRepair) {
                    if (Settings.getInstance().debugMode()) {
                        ReportManager.log("Není vyžadována oprava, vytvoření nového kontaktu neproběhne.");
                    }

                    continue;
                }

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log("Proběhne vytvoření nového kontaktu.");
                }

                // vytvoření nového kontaktu
                Guardian guardian = RecordFactory.newGuardian(catalog.get(studentID));
                if (!newlyCreated.contains(guardian.getInternalID())) {
                    newlyCreated.add(guardian.getInternalID());
                }
                // TODO ?

            }
        }

        // 2) smazání neplatných kontaktů
        LinkedHashMap<String, DataLDAP> contactsToDelete = contacts.getSubsetWithFlag(false);
        if (contactsToDelete.size() > 0) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log("Byly nalezeny neplatné kontakty, proběhne jejich smazání.");
            }

            Iterator<String> contactsToDeleteIterator = contactsToDelete.keySet().iterator();
            while (contactsToDeleteIterator.hasNext()) {

                String guardianID = contactsToDeleteIterator.next();

                // parciální instance
                Guardian contactToDelete = RecordFactory.getGuardianByPair(null, contacts.get(guardianID));

                if (attemptRepair) {
                    // TODO
                    Boolean delete = contactToDelete.deleteContact();

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Byl vymazán neplatný kontakt " + contactToDelete.getSurname() + " " + contactToDelete.getGivenName() + ".");
                    }

                    // TODO hlášení
                } else {
                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Je třeba smazat neplatný záznam zákonného zástupce " + contactToDelete.getSurname() + " " + contactToDelete.getGivenName() + ".");
                    }
                    // TODO hlášení
                }
            }
        }

        // znovunačtení synchronizovaných dat
        this.loadDirectoryContacts();

        // 3) synchronizace distribučních skupin zákonných zástupců
        if (attemptRepair) {
            Iterator<String> guardianDLiterator = guardianDL.keySet().iterator();
            while (guardianDLiterator.hasNext()) {
                String guardianID = guardianDLiterator.next();

                // seřazení získaného seznamu
                Collections.sort(guardianDL.get(guardianID));

                LinkedHashMap<String, DataSQL> guardianDetails = catalog.getSubsetBy(EBakaSQL.F_GUA_BK_ID, guardianID);
                String studentID = "";
                if (guardianDetails.size() <= 0) {
                    continue;
                } else {
                    // pouze první výsledek
                    Iterator<String> guardianSelectionIterator = guardianDetails.keySet().iterator();
                    studentID = guardianSelectionIterator.next();
                }

                // parciální instance zákonného zástupce
                Guardian guardian = RecordFactory.getGuardianByPair(catalog.get(studentID), contacts.get(guardianID));
                Boolean updateDL = guardian.replaceDistributionLists(guardianDL.get(guardianID));
            }
        }
    }

    /**
     * Kontrola a synchronizace třídních učitelů a odpovídajících distribučních skupin.
     *
     * @param attemptRepair pokusit se o opravu v případě neshody
     */
    public void syncClassTeacher(boolean attemptRepair) {

        if (Settings.getInstance().beVerbose()) {
            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Začíná kontrola distribučních skupin třídních učitelů.");
        }

        // smyčka po ročnících
        for (char year = '1'; year <= '9'; year++) {

            // smyčka po třídách
            for (char classLetter = 'A'; classLetter <= 'E'; classLetter++) {

                String classLabel = year + "." + classLetter;
                String classDistributionListDN = "CN=Ucitele-Tridni-" + year + classLetter + "," + Settings.getInstance().getLDAP_baseDL();

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logWait(EBakaLogType.LOG_VERBOSE, "Probíhá zpracování třídy " + classLabel);
                }

                // distribuční data z adresáře
                ArrayList<String> classTeachersInDL = BakaADAuthenticator.getInstance().listDirectMembers(classDistributionListDN);

                // data z evidence - 0..1
                DataSQL classTeacher = this.faculty.getBy(EBakaSQL.F_CLASS_LABEL, classLabel);

                // v evidenci je prázdný výsledek = neexistující třída
                if (classTeacher == null) {

                    // požadavek - zcela prázdný distribuční seznam
                    if (classTeachersInDL.size() == 0) {
                        // OK
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                        }

                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_DEBUG, "Třída " + classLabel + " je bez označení třídního učitele, nebo neexistuje.");
                        }

                        // žádná další činnost není vyžadována
                        continue;
                    } else {
                        // CHYBA, je potřeba smazat všechny členy současného distribučního seznamu
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.logResult(EBakaLogType.LOG_ERR);
                        }

                        if (Settings.getInstance().debugMode()) {
                            ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Třída " + classLabel +
                                    " je bez označení třídního učitele, nebo neexistuje, avšak odpovídající distribuční seznam není prázdný.");
                        }

                        if (attemptRepair) {
                            if (Settings.getInstance().beVerbose()) {
                                ReportManager.logWait(EBakaLogType.LOG_VERBOSE, "Probíhá pokus o opravu třídy " + classLabel);
                            }

                            // celkový prces opravy
                            Boolean repair = true;

                            // smazat jednotlivé členy
                            for (String singleDN: classTeachersInDL) {

                                if (Settings.getInstance().debugMode()) {
                                    // uzavření předchozího řádku
                                    ReportManager.logResult(EBakaLogType.LOG_DEBUG);

                                    ReportManager.logWait(EBakaLogType.LOG_DEBUG, "Z distribučního seznamu [" + classDistributionListDN + "] se odstraňuje záznam [" + singleDN + "].");
                                }

                                Boolean removeSingle = BakaADAuthenticator.getInstance().removeObjectFromGroup(singleDN, classDistributionListDN);
                                repair &= removeSingle;

                                // TODO
                                if (removeSingle) {

                                } else {

                                }

                            }

                            if (Settings.getInstance().beVerbose()) {

                                if (Settings.getInstance().debugMode()) {
                                    // otevření řádku
                                    ReportManager.logWait(EBakaLogType.LOG_DEBUG, "--->");
                                }

                                if (repair) {
                                    ReportManager.logResult(EBakaLogType.LOG_OK);
                                } else {
                                    ReportManager.logResult(EBakaLogType.LOG_ERR);
                                }
                            }

                        } else {
                            // upozornit na chybu
                            ReportManager.log(EBakaLogType.LOG_WARN, "Je vyžadována oprava. Třída " + classLabel + " nemá definovaného " +
                                    "třídního učitele (nebo neexistuje), avšak odpovídající distribuční seznam není prázdný.");
                        }
                    }

                    // neexistující třída/bez třídního
                } else {
                    // TODO
                    // požadavek - jeden odpovídající záznam v distribučním seznamu


                }

                // odpovídající data z adresáře
                DataLDAP classTeacherDN = this.directoryFaculty.getBy(EBakaLDAPAttributes.MAIL, classTeacher.get(EBakaSQL.F_FAC_EMAIL.basename()));

                if (classTeacher != null) {

                } else {

                }

                /*
                if (Settings.getInstance().beVerbose()) {
                    ReportManager.logResult();
                }
                */


                // pokus
                /*
                DataSQL pokus = this.faculty.getBy(EBakaSQL.F_CLASS_LABEL, classLabel);
                if (pokus != null) {
                    System.out.println("Nalezeno: " + pokus);

                    // existuje LDAP?
                    DataLDAP pokus2 = this.directoryFaculty.getBy(EBakaLDAPAttributes.MAIL, pokus.get(EBakaSQL.F_FAC_EMAIL.basename()));
                    if (pokus2 != null) {
                        System.out.println("Nalezeno v LDAP: " + pokus2);
                    } else {
                        System.out.println("V LDAPu nic.");
                    }
                }
                */

            }

            // TODO !!!
            break;
        }
    }

    /**
     * Provedení synchronizace evidence a dresáře.
     * - Zavedení nových žáků TODO RM vytvoří report pro třídního
     * - Zablokování vyřazených žáků
     */
    public static void actionSync() {
        Sync checker = new Sync();
        checker.syncClassTeacher(true);
        checker.actionInit(true);
        checker.actionCheck(true);
        checker.checkData(true);
        checker.syncGuardian(true);
    }

    /**
     * Provedení kontroly stavu synchronizace.
     */
    public static void actionCheckSync() {
        Sync checker = new Sync();
        checker.syncClassTeacher(false);
        checker.actionInit(false);
        checker.actionCheck(false);
        checker.checkData(false);
        checker.syncGuardian(false);
    }

}
