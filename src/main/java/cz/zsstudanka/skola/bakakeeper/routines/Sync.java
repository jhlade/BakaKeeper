package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.Student;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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

    /** maximální limit počtu pokusů o výtvoření adresy */
    private static final int LIMIT = 10;

    /**
     * Inicializace synchronizačních rutin - naplnění daty.
     */
    public Sync() {
        this.catalog = new SQLrecords(null, null);
        this.faculty = null; // TODO
        this.directory = new LDAPrecords(Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);
        this.alumni = new LDAPrecords(Settings.getInstance().getLDAP_baseAlumni(), EBakaLDAPAttributes.OC_USER);
        this.directoryFaculty = new LDAPrecords(Settings.getInstance().getLDAP_baseFaculty(), EBakaLDAPAttributes.OC_USER);
        this.contacts = new LDAPrecords(Settings.getInstance().getLDAP_baseContacts(), EBakaLDAPAttributes.OC_CONTACT);
    }

    /**
     * Pro vývojové účely.
     * @deprecated
     */
    public void devel() {
        String ročník = "1";
        String třída = "E";

        Map<String, String> rowData;

        // 1.E
        // 1, Školáček Malý, DEV01
        rowData = new HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "1");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "NULL");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Malý");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Školáček");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV01");
        this.catalog.addRecord("DEV01", rowData);

        // 2, Aaadam Testový, DEV02
        rowData = new HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "2");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "NULL");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Testový");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV02");
        this.catalog.addRecord("DEV02", rowData);

        // 27, Aaadam Expirovaný, DEV03
        rowData = new HashMap<String, String>();
        rowData.put(EBakaSQL.F_STU_CLASS_ID.basename(), "27");
        rowData.put(EBakaSQL.F_STU_MAIL.basename(), "NULL");
        rowData.put(EBakaSQL.F_STU_CLASS.basename(), ročník + "." + třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), třída);
        rowData.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), ročník);
        rowData.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Aaadam");
        rowData.put(EBakaSQL.F_STU_SURNAME.basename(), "Expirovaný");
        rowData.put(EBakaSQL.F_STU_ID.basename(), "DEV03");
        this.catalog.addRecord("DEV03", rowData);
    }

    /**
     * Kontrola a zavedení nových žáků.
     * Nový žák = žák v evidenci bez platné školní e-mailové adresy (UPN).
     *
     * @param update zapsat novou e-mailovou adresu do evidence
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
                        } else {
                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_SQL, "Nebyl potvrzen zápis do ostrých dat, změny budou zahozeny.");
                            }
                        }
                    }

                } while (inUse || attempt > LIMIT);

                // TODO výsledek

            } // žák již má adresu v doméně školy


        }

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
        }

    }

    /**
     * Provedení pokusu o párování žáka podle SQL záznamu s existujícím LDAP účtem.
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
     * Kontrola záznamů proti adresáři a ošetření záznamů, které
     * nemají vzájemné protějšky.
     */
    public void actionCheck() {

    }

    /**
     * Provedení synchronizace evidence a dresáře.
     * - Zavedení nových žáků TODO RM vytvoří report pro třídního
     * - Zablokování vyřazených žáků
     * -
     */
    public void actionSync() {

    }

}
