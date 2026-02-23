package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.collections.LDAPrecords;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.model.entities.Student;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.io.File;
import java.io.FileNotFoundException;
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

    /** Maximální počet pokusů pro generování hesla. */
    private static final int MAX_PASSWORD_ATTEMPTS = 25;

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

            Date logonDate = new Date((Long.parseLong("10000") / 10000L) - +11644473600000L);
            try {
                logonDate = new Date((Long.parseLong(data.get(0).get(EBakaLDAPAttributes.LAST_LOGON.attribute())) / 10000L) - +11644473600000L);
            } catch (NumberFormatException e) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Nebylo nalezeno žádné předchozí přihlášení uživatele.");
            }
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

    /**
     * Odešle LuaLaTeXem generované PDF se současným stavem dat.
     *
     * @param query dotazované objekty (ročník, třída, UPN žáka - seznam oddělený čárkami)
     * @param resetPassword provede okamžitý reset hesla nad danými objekty
     */
    public static void genericReport(String query, boolean resetPassword) {

        // zpracovávané celé třídy
        ArrayList<String> classes = new ArrayList<>();

        // samostatní žáci
        ArrayList<String> students = new ArrayList<>();

        // zpracování dotazu
        String[] queries = query.split(",");

        for (String q : queries) {

            // vše
            if (q.length() == 1 && q.equals("*")) {
                for (int yr = 1; yr <= 9; yr++) {
                    for (char letter = 'A'; letter <= 'E'; letter++) {
                        if (!classes.contains(yr + "." + letter)) {
                            classes.add(yr + "." + letter);
                        }
                    }
                }
                break;
            }

            // ročník
            if (q.length() == 1 && q.matches("[1-9]")) {
                for (char letter = 'A'; letter <= 'E'; letter++) {
                    classes.add(q + "." + letter);
                }
            }

            // třída
            if (q.length() == 3 && q.matches("[1-9]\\.[a-eA-E]") ) {
                if (!classes.contains(q.toUpperCase())) {
                    classes.add(q.toUpperCase());
                }
            }

            // jeden žák
            if (q.length() > 3 && q.contains(".")) {
                if (!students.contains(q.toLowerCase())) {
                    students.add(q.toLowerCase());
                }
            }
        }

        // abecední seřazení seznamů
        Collections.sort(classes);
        Collections.sort(students);

        // sestavy <třída, TeX data>
        Map<String, String> reports = new HashMap<>();
        // třídní učitelé
        SQLrecords classTeachers = new SQLrecords(true);

        // celé třídy
        for (int mYr = 1; mYr <= 9; mYr++) {
            for (char mLetter = 'A'; mLetter <= 'E'; mLetter++) {

                // generovaná třída není na seznamu požadavků
                if (!classes.contains(mYr + "." + mLetter)) {
                    continue;
                }

                // třída v evidenci neexistuje
                if (classTeachers.getBy(EBakaSQL.F_CLASS_LABEL, mYr + "." + mLetter) == null) {
                    continue;
                }


                // SQL data třídy
                SQLrecords classData = new SQLrecords(mYr, String.valueOf(mLetter));
                // LDAP data třídy
                LDAPrecords dataAD = new LDAPrecords("OU=Trida-"+String.valueOf(mLetter)+",OU=Rocnik-"+String.valueOf(mYr)+"," + Settings.getInstance().getLDAP_baseStudents(), EBakaLDAPAttributes.OC_USER);

                // identifikace třídního učitele - jméno
                String classTeacher = classTeachers.getBy(EBakaSQL.F_CLASS_LABEL, mYr + "." + mLetter).get(EBakaSQL.F_FAC_GIVENNAME.basename()) + " " + classTeachers.getBy(EBakaSQL.F_CLASS_LABEL, mYr + "." + mLetter).get(EBakaSQL.F_FAC_SURNAME.basename());

                StringBuilder classReportData = new StringBuilder();
                String template = "__CLASS_ID__ & __SURNAME__ & __GIVENNAME__ & \\texttt{__UPN__} & \\texttt{__PWD__} & \\qrcode{__QR__} \\\\ \\hline\n";

                // získání dat žáků ze třídy - iterace nad evidencí
                while (classData.iterator().hasNext()) {

                    Map<String, String> classStudent = classData.get(classData.iterator().next());

                    // data
                    String bakaID = classStudent.get(EBakaSQL.F_STU_ID.basename());
                    Integer classNum = Integer.parseInt(classStudent.get(EBakaSQL.F_STU_CLASS_ID.basename()));
                    String studentSurname = classStudent.get(EBakaSQL.F_STU_SURNAME.basename());
                    String studentName = classStudent.get(EBakaSQL.F_STU_GIVENNAME.basename());
                    String studentUPN = classStudent.get(EBakaSQL.F_STU_MAIL.basename());

                    DataSQL sqlStudent = classData.get(bakaID);
                    DataLDAP ldapStudent = dataAD.get(studentUPN);
                    Student studentObject = new Student(sqlStudent, ldapStudent);

                    // výchozí heslo žáka
                    String newPassword = BakaUtils.createInitialPassword(
                            studentSurname, // příjmení
                            studentName, // jméno
                            mYr, // ročník
                            classNum // číslo v třídním výkazu
                    );

                    // resetování hesla
                    if (resetPassword) {

                        ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá reset hesla žáka " + studentSurname + " " + studentName);

                        int attempt = 0;
                        boolean passwordSet = false;

                        while (!passwordSet && attempt < MAX_PASSWORD_ATTEMPTS) {

                            newPassword = BakaUtils.nextPassword(
                                    studentSurname, // příjmení
                                    studentName, // jméno
                                    mYr, // ročník
                                    classNum, // číslo v třídním výkazu
                                    attempt // pokus
                            );

                            if (!RuntimeContext.FLAG_DRYRUN) {
                                passwordSet = studentObject.setPassword(newPassword, false);
                            } else {
                                passwordSet = true;
                                // TODO RM
                            }
                            attempt++;
                        }

                        if (passwordSet) {
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                            ReportManager.log(EBakaLogType.LOG_VERBOSE, "Heslo žáka " + studentSurname + " " + studentName + " bylo úspěšně změněno na " + newPassword);
                        } else {
                            ReportManager.logResult(EBakaLogType.LOG_ERR);
                            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Heslo žáka " + studentSurname + " " + studentName + " nebylo možné změnit ani po " + attempt + " pokusech.");
                        }
                    }

                    // zápis do tabulky v sestavě
                    classReportData.append(template
                            .replace("__CLASS_ID__", classNum.toString())
                            .replace("__SURNAME__", studentSurname)
                            .replace("__GIVENNAME__", studentName)
                            .replace("__UPN__", studentUPN)
                            .replace("__PWD__", newPassword)
                            .replace("__QR__", studentUPN + "\t" + newPassword)
                    );
                }

                // vytvoření TeX sestavy
                reports.put(mYr + "." + mLetter, Export.latexReportTemplate(mYr + "." + mLetter, classTeacher, classReportData.toString()));

            } // celá třída

        } // ročník

        // TODO jednotliví žáci zvlášť
        //
        //


        // uložení, vytvoření a odeslání sestav
        Iterator<String> tex = reports.keySet().iterator();
        while (tex.hasNext()) {

            String classN = tex.next();

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date();

            String reportFile = "./reports/" + formatter.format(date)
                    + "_" + classN.toLowerCase().replace(".", "")
                    + ".tex";

            try (PrintStream out = new PrintStream(new FileOutputStream(reportFile))) {
                out.print(reports.get(classN));
            } catch (FileNotFoundException e) {
                // TODO
                ReportManager.handleException("Nebylo možné vytvořit sestavu.", e);
            }

            ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá generování PDF sestavy třídy " + classN);

            // pdf lualatex
            try {
                Process p = Runtime.getRuntime().exec("/usr/bin/lualatex " + reportFile);
                p.waitFor();
            } catch (Exception x) {
                ReportManager.logResult(EBakaLogType.LOG_ERR);
                ReportManager.handleException("Proces LuaLaTeX nebyl spuštěn.", x);
            }

            ReportManager.logResult(EBakaLogType.LOG_OK);


            String message = "V příloze naleznete sestavu s novými přístupovými údaji žáků " +
                    classN + " pro použití v prostředí Office365. Všichni žáci mají přiřazené " +
                    "odpovídající žákovské licence, mohou tedy ihned plně používat všechny cloudové služby O365.\n\n" +
                    "Tuto sestavu považujte za důvěrnou a distribuci " +
                    "hesel, pokud to bude možné, provádějte jednotlivě.\n\n";

            if (RuntimeContext.FLAG_DRYRUN) {
                message += "[ i ] Sestava byla vygenerována s příznakem zkušebního spuštění, zobrazované údaje nemusí reflektovat skutečný stav.";
            }

            // identifiakce třídního učitele - e-mail
            String classTeacherEmail = classTeachers.getBy(EBakaSQL.F_CLASS_LABEL, classN).get(EBakaSQL.F_FAC_EMAIL.basename());

            String[] addresses;
            if (!RuntimeContext.FLAG_DRYRUN) {
                // e-mail pro třídního učitele a správce systému
                addresses = new String[]{classTeacherEmail, Settings.getInstance().getAdminMail()};
            } else {
                // pouze správcovský e-mail
                addresses = new String[]{Settings.getInstance().getAdminMail()};
            }

            // e-mail pro třídního a správce
            BakaMailer.getInstance().mail(addresses,
                    "Přístupové údaje žáků " + classN, message,
                    new String[]{"./" + BakaUtils.fileBaseName(reportFile.replace(".tex", ".pdf"))});

            // úklid
            File pdf = new File("./" + BakaUtils.fileBaseName(reportFile.replace(".tex", ".pdf")));
            File aux = new File("./" + BakaUtils.fileBaseName(reportFile.replace(".tex", ".aux")));
            File log = new File("./" + BakaUtils.fileBaseName(reportFile.replace(".tex", ".log")));
            pdf.delete();
            aux.delete();
            log.delete();
            File texfile = new File("./reports/" + BakaUtils.fileBaseName(reportFile));
            texfile.delete();
        }

    }

    /**
     * TODO - LaTeXová šablona sestavy
     *
     * @param classID třída
     * @param classTeacherName jméno třídního učitele
     * @param internalData vnitřní data - tabulka s žáky
     * @return TeX - sestava
     */
    public static String latexReportTemplate(String classID, String classTeacherName, String internalData) {

        // tvorba sestavy
        StringBuilder report = new StringBuilder();
        report.append("\\documentclass[10pt]{article}\n\n");
        report.append("\\usepackage[czech]{babel}\n");
        report.append("\\usepackage[utf8]{inputenc}\n");
        report.append("\\usepackage{tabularx}\n\n");
        report.append("\\usepackage{geometry}\n");
        report.append("\\geometry{a4paper,total={170mm,257mm},left=20mm,top=20mm}\n");
        report.append("\\def\\arraystretch{1.5}%\n\n");
        report.append("\\usepackage{fancyhdr}\n");
        report.append("\\usepackage{qrcode}\n");
        report.append("\\pagestyle{fancy}\n\n");

        if (RuntimeContext.FLAG_DRYRUN) {
            report.append("\\usepackage{xcolor}\n\n");
        }

        report.append("\\begin{document}\n");
        report.append("\\qrset{height=.55cm}%\n\n");

        report.append("\\fancyhf{}\n");
        report.append("\\lhead{" + classID + "}\n");
        report.append("\\rhead{" + classTeacherName + "}\n");
        report.append("\\lfoot{" + Settings.getInstance().systemInfoTag() + "}\n");
        report.append("\\rfoot{" + Version.getInstance().getTag() + "}\n");

        if (RuntimeContext.FLAG_DRYRUN) {
            report.append("\\chead{{\\color{red}\\textbf{ZKUŠEBNÍ SESTAVA}}}\n");
        }

        report.append("\\noindent\n");
        report.append("\\Large{Třída " + classID + "}\n\n");
        report.append("\\begin{table}[htbp]\n\n");
        report.append("\\centering\n");
        report.append("\\begin{tabularx}{\\textwidth}{| c | X | X | r | c | c |}\n");
        report.append("\\hline\n");
        report.append("\\bf{Č.} & \\bf{Příjmení} & \\bf{Jméno} & \\bf{UPN}  & \\bf{Heslo} & {~} \\\\ \\hline \\hline\n");

        // data
        report.append(internalData);

        report.append("\\end{tabularx}\n\n");
        report.append("\\end{table}\n\n");
        report.append("\\noindent\n");
        report.append("UPN = \\textit{User Principal Name}, slouží jako přihlašovací jméno do~služeb\n");
        report.append("Office~365 a~zároveň jako platný tvar e-mailové adresy.\\par\n");
        report.append("~\\par\n\n");
        report.append("\\noindent\n");
        report.append("Žáci si mohou své heslo sami změnit na~portálu https://heslo.zs-studanka.cz/.\\par\n\n");
        report.append("\\end{document}\n");


        return report.toString();
    }

}
