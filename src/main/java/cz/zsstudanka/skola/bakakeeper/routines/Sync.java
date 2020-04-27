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

            //if (žák.getValue().get(EBakaSQL.F_STU_MAIL.basename()).toString().toLowerCase().contains(Settings.getInstance().getMailDomain())) {
            if (!this.catalog.get(studentID)
                    .get(EBakaSQL.F_STU_MAIL.basename())
                    .toLowerCase()
                    .contains(Settings.getInstance().getMailDomain())
            ) {

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Žák [" + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_CLASS.basename()) + ", č. " + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_CLASS_ID.basename()) + "]: " + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_SURNAME.basename()) + " "
                            + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_GIVENNAME.basename()) + " ID = [" + this.catalog.get(studentID)
                            .get(EBakaSQL.F_STU_ID.basename()) + "] nemá vyplněnou školní e-mailovou adresu.");
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

                    if (Settings.getInstance().beVerbose()) {
                        ReportManager.log("Navrhuje se: " + proposed);
                    }

                    if (occupied.contains(proposed)) {
                        inUse = true;
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa je již osazena předchozím pokusem.");
                        }
                        continue;
                    }

                    if (directoryFaculty.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa je osazena zaměstnancem.");
                        }
                        continue;
                    }

                    if (alumni.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa je osazena vyřazeným žákem.");
                        }
                        continue;
                    }

                    if (directory.get(proposed) != null) {
                        inUse = true;
                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa je osazena aktivním žákem.");
                        }

                        // TODO ! - kontrola + párování
                        // inUse = výsledek párování

                        continue;
                    }

                    // hotovo, zápis dat
                    if (!inUse) {

                        if (Settings.getInstance().beVerbose()) {
                            ReportManager.log("Navrhovaná adresa je k dispozici a bude ji možné zapsat do evidence.");
                        }

                        if (update) {

                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_DEBUG, "Do transakční fronty budou přidána data [" + proposed + "] pro ID = [" + studentID + "]");
                            }

                            HashMap<EBakaSQL, String> data = new HashMap<>();
                            data.put(EBakaSQL.F_STU_MAIL, proposed);
                            catalog.addWriteData(studentID, data);
                        } else {
                            if (Settings.getInstance().debugMode()) {
                                ReportManager.log(EBakaLogType.LOG_DEBUG, "Nebyl potvrzen zápis do ostrých dat, změny budou zahozeny.");
                            }
                        }
                    }

                } while (inUse || attempt > 10);

            } // žák již má adresu v doméně školy

        }

    }

    /**
     * TODO !
     *
     * @param idSQL
     * @param idLDAP
     * @param update
     * @return
     */
    private Boolean attemptToPair(String idSQL, String idLDAP, Boolean update) {
        return null;
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
