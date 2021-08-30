package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Práce s exporty.
 *
 * @author Jan Hladěna
 */
public class Export {

    /*
    public static void exportStudentCSVdata(String outFile) {

        Settings.getInstance().load();

        StringBuilder outputBuffer = new StringBuilder();
        // TODO (zkontrolovat) header
        outputBuffer.append("INTERN_KOD;ROCNIK;TRIDA;C_TR_VYK;PRIJMENI;JMENO;AD_LOGIN;AD_EMAIL;AD_RPWD;EMAIL;ZZ_KOD;ZZ_JMENO;ZZ_PRIJMENI;ZZ_TELEFON;ZZ_EMAIL");
        outputBuffer.append("\n");

        // připojení k SQL serveru
        BakaSQL.getInstance().connect();

        for (int rocnik = 1; rocnik <= 9; rocnik++) {
            for (char trida = 'A'; trida <= 'E'; trida++) {

                // aktuální třída
                Trida tmpTrida = new Trida(rocnik, trida);
                tmpTrida.populate();

                if (Settings.getInstance().beVerbose()) {
                    System.err.println("[ INFO ] Exportuje se třída " + tmpTrida.getDisplayName() + ".");
                }

                // iterace
                while (tmpTrida.getZaci().iterator().hasNext()) {

                    Zak tmpZak = tmpTrida.getZaci().next();

                    outputBuffer.append("\"" + tmpZak.getIntern_kod() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getRocnik() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getPismenoTridy() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getCTrVyk() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getPrijmeni() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getJmeno() + "\"");
                    outputBuffer.append(";");

                    if (tmpZak.findByData()) {
                        // AD LOGIN
                        outputBuffer.append("\"" + tmpZak.getADLogin() + "\"");
                        outputBuffer.append(";");
                        // AD EMAIL
                        outputBuffer.append("\"" + tmpZak.getADEmail() + "\"");
                        outputBuffer.append(";");
                    } else {
                        // AD LOGIN
                        outputBuffer.append("\"" + "NULL" + "\"");
                        outputBuffer.append(";");
                        // AD EMAIL
                        outputBuffer.append("\"" + "NULL" + "\"");
                        outputBuffer.append(";");
                    }

                    outputBuffer.append("\"" + tmpZak.getRPwd() + "\"");
                    outputBuffer.append(";");

                    outputBuffer.append("\"" + tmpZak.getBakaEmail() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getZZ_kod() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getJmeno() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getPrijmeni() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getTelefon() + "\"");
                    outputBuffer.append(";");
                    outputBuffer.append("\"" + tmpZak.getZakonnyZastupce().getEmail() + "\"");
                    outputBuffer.append("\n");
                }
            }
        }



    }*/

    /**
     * Export žákovských informací ve formě CSV.
     *
     * Pole obsahují
     * INTERN_KOD  - interní ID žáka z Bakalářů
     * ROCNIK      - číslo ročníku z Bakalářů
     * TRIDA       - písmeno třídy z Bakalářů
     * PRIJMENI    - příjméní žáka z Bakalářů
     * JMENO       - jméno žáka z Bakalářů
     * UPN         - přihlašovací jméno žáka z LDAP
     * AD_RPWD     - výchozí heslo pro reset
     * EMAIL       - e-mail žáka z Bakalářů
     * ZZ_KOD      - interní ID primárního zákonného zástupce žáka z Bakalářů
     * ZZ_JMENO    - jméno primárního zákonného zástupce žáka z Bakalářů
     * ZZ_PRIJMENI - příjmení primárního zákonného zástupce žáka z Bakalářů
     * ZZ_TELEFON  - telefon primárního zákonného zástupce žáka z Bakalářů
     * ZZ_EMAIL    - e-mail primárního zákonného zástupce žáka z Bakalářů
     *
     * @param outFile výstupní soubor
     */
    public static void exportStudentCSVdata(String outFile) {

        Settings.getInstance().load();

        // rutina exportu
        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "====== [ EXPORT ] ======");
        }

        StringBuilder outputBuffer = new StringBuilder();

        // header
        outputBuffer.append("INTERN_KOD;ROCNIK;TRIDA;C_TR_VYK;PRIJMENI;JMENO;UPN;AD_RPWD;EMAIL;ZZ_KOD;ZZ_JMENO;ZZ_PRIJMENI;ZZ_TELEFON;ZZ_EMAIL");
        outputBuffer.append("\n");

        // TODO - naplnění bufferu daty


        // uložení nebo výstup
        if (outFile != null) {
            File outputFile = new File(outFile);

            try {
                PrintStream outStream = new PrintStream(new FileOutputStream(outFile));
                outStream.println(outputBuffer.toString());
                outStream.close();
            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné uložit výstup do souboru " + outputFile.getPath(), e);
            }

        } else {
            // stdout
            ReportManager.log(EBakaLogType.LOG_STDOUT, outputBuffer.toString());
        }

        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "====== [ /EXPORT ] ======");
        }
    }

    /**
     * Identifikace účtu.
     *
     * @param upn UPN účtu
     */
    public static void identify(String upn) {
        // získání aktivního účtu
        Map<Integer, Map<String, String>> data = BakaADAuthenticator.getInstance().getUserInfo(upn.toLowerCase(Locale.ROOT) + "@" + Settings.getInstance().getMailDomain(), Settings.getInstance().getLDAP_base());

        if (data.size() == 1) {

            ReportManager.log(EBakaLogType.LOG_STDOUT, "Celé jméno: \t\t" + data.get(0).get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()));
            ReportManager.log(EBakaLogType.LOG_STDOUT, "E-mailová adresa: \t" + data.get(0).get(EBakaLDAPAttributes.MAIL.attribute()));

            Date current = new Date();
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");

            Date logonDate = new Date((Long.parseLong(data.get(0).get(EBakaLDAPAttributes.LAST_LOGON.attribute())) / 10000L) - + 11644473600000L);
            long logonDiff = (current.getTime() - logonDate.getTime()) / 1000 / 3600 / 24;
            ReportManager.log(EBakaLogType.LOG_STDOUT, "Poslední přihlášení: \t" + dateFormat.format(logonDate) + " (" + ((logonDiff > 10000) ? "nikdy" : "před " + logonDiff + " dny") + ")");

            Date pwdDate = new Date((Long.parseLong(data.get(0).get(EBakaLDAPAttributes.PW_LASTSET.attribute())) / 10000L) - + 11644473600000L);
            long pwdDiff = (current.getTime() - pwdDate.getTime()) / 1000 / 3600 / 24;
            ReportManager.log(EBakaLogType.LOG_STDOUT, "Poslední změna hesla: \t" + dateFormat.format(pwdDate) + " ("+ ((pwdDiff > 10000) ? "nikdy" : "před " + pwdDiff + " dny") + ")");

            ReportManager.log(EBakaLogType.LOG_STDOUT, "Typ účtu: \t\t" + ((data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseStudents())) ? "žák" : ((data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseTeachers()) ? "učitel" : "zaměstnanec"))));

            ArrayList<String> userGroups = BakaADAuthenticator.getInstance().listMembership(data.get(0).get(EBakaLDAPAttributes.DN.attribute()));

            if (userGroups.size() > 0) {

                StringBuilder groups = new StringBuilder();

                Iterator<String> groupIterator = userGroups.iterator();
                while (groupIterator.hasNext()) {
                    groups.append(BakaUtils.parseCN(groupIterator.next()));
                    if (groupIterator.hasNext()) {
                        groups.append(", ");
                    }
                }

                ReportManager.log(EBakaLogType.LOG_STDOUT, "Skupiny: \t\t" + "(" + userGroups.size() + ") " + groups.toString());
            }

            // vyřazený žák
            if (data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseStudents()) && data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseAlumni())) {
                ReportManager.log(EBakaLogType.LOG_STDOUT, "Účet vyřazen: \t\t" + BakaUtils.parseLastOU(data.get(0).get(EBakaLDAPAttributes.DN.attribute())));
            }

            // žák
            if (data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseStudents()) && !data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseAlumni())) {

                // parciální načtení z evidence
                SQLrecords catalog = new SQLrecords(BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())), BakaUtils.classLetterFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())));
                DataSQL studentRecord = catalog.getBy(EBakaSQL.F_STU_MAIL, data.get(0).get(EBakaLDAPAttributes.MAIL.attribute()));

                // třídní učitelé
                SQLrecords faculty = new SQLrecords(true);
                DataSQL classTeacher = faculty.getBy(EBakaSQL.F_CLASS_LABEL, BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())) + "." + BakaUtils.classLetterFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())));

                // třída (1..1) (třídní, 1..1)
                ReportManager.log(EBakaLogType.LOG_STDOUT, "Třída: \t\t\t" + BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())) + "." + BakaUtils.classLetterFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())) + " (" + classTeacher.get(EBakaSQL.F_FAC_SURNAME.basename()) + " " + classTeacher.get(EBakaSQL.F_FAC_GIVENNAME.basename()) + ")");

                // výchozí heslo v tomto roce
                ReportManager.log(EBakaLogType.LOG_STDOUT, "Výchozí heslo žáka: \t" + BakaUtils.createInitialPassword(data.get(0).get(EBakaLDAPAttributes.NAME_LAST.attribute()),
                        data.get(0).get(EBakaLDAPAttributes.NAME_FIRST.attribute()),
                        BakaUtils.classYearFromDn(data.get(0).get(EBakaLDAPAttributes.DN.attribute())),
                        Integer.parseInt( studentRecord.get(EBakaSQL.F_STU_CLASS_ID.basename()))) );

                // zákonný zástupce
                ReportManager.log(EBakaLogType.LOG_STDOUT, "Zákonný zástupce:\t" + studentRecord.get(EBakaSQL.F_GUA_BK_SURNAME.basename()) +
                        " " + studentRecord.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()) +
                        " (" + studentRecord.get(EBakaSQL.F_GUA_BK_MAIL.basename()) + ", " + studentRecord.get(EBakaSQL.F_GUA_BK_MOBILE.basename()) +
                        ")");
            }

            // učitel
            if (data.get(0).get(EBakaLDAPAttributes.DN.attribute()).contains(Settings.getInstance().getLDAP_baseTeachers())) {
                // třídnictví (0..N)
            }

        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Uživatel se zadaným UPN nebyl nalezen.");
        }
    }

}
