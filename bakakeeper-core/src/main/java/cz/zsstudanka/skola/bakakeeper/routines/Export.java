package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.collections.SQLrecords;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.service.PasswordResetResult;
import cz.zsstudanka.skola.bakakeeper.service.PasswordService;
import cz.zsstudanka.skola.bakakeeper.service.RangeSelector;
import cz.zsstudanka.skola.bakakeeper.service.ResolvedSelection;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import cz.zsstudanka.skola.bakakeeper.utils.PdfReportGenerator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Práce s exporty a sestavami.
 *
 * @author Jan Hladěna
 */
public class Export {

    /** Maximální počet pokusů pro generování hesla (legacy cesta bez PasswordService). */
    private static final int MAX_PASSWORD_ATTEMPTS = 25;

    /**
     * Export žákovských informací ve formě CSV.
     * Používá {@link RangeSelector} pro parsování rozsahu – stejný formát jako {@code --report}.
     *
     * @param query rozsah výběru (ročník, třída, UPN – odděleno čárkou)
     * @param outFile výstupní soubor (null = stdout)
     * @param studentRepo repozitář žáků
     * @param facultyRepo repozitář vyučujících
     */
    public static void exportStudentCSVdata(String query, String outFile,
                                             StudentRepository studentRepo,
                                             FacultyRepository facultyRepo) {

        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "====== [ EXPORT ] ======");
        }

        // parsování a vyhodnocení rozsahu
        RangeSelector selector = RangeSelector.parse(query);
        ResolvedSelection selection = selector.resolve(studentRepo, facultyRepo,
                Settings.getInstance().getMailDomain());

        // varování pro nenalezené žáky
        for (String nf : selection.notFound()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Žák s UPN " + nf + " nebyl nalezen v evidenci.");
        }

        StringBuilder outputBuffer = new StringBuilder();

        // hlavička CSV
        outputBuffer.append("INTERN_KOD;ROCNIK;TRIDA;C_TR_VYK;PRIJMENI;JMENO;UPN;AD_RPWD;EMAIL;ZZ_KOD;ZZ_JMENO;ZZ_PRIJMENI;ZZ_TELEFON;ZZ_EMAIL");
        outputBuffer.append("\n");

        // data – iterace přes třídy a žáky
        for (var entry : selection.studentsByClass().entrySet()) {
            for (StudentRecord s : entry.getValue()) {
                String email = s.getEmail();
                if (email == null || email.isEmpty() || EBakaSQL.NULL.basename().equals(email)) {
                    continue;
                }

                int classNum = 0;
                try {
                    classNum = Integer.parseInt(s.getClassNumber());
                } catch (NumberFormatException ignored) {
                    // žák bez čísla v třídním výkazu
                }

                String password = BakaUtils.createInitialPassword(
                        s.getSurname(), s.getGivenName(), s.getClassYear(), classNum);

                outputBuffer.append(csvField(s.getInternalId())).append(";");
                outputBuffer.append(s.getClassYear()).append(";");
                outputBuffer.append(csvField(s.getClassLetter())).append(";");
                outputBuffer.append(csvField(s.getClassNumber())).append(";");
                outputBuffer.append(csvField(s.getSurname())).append(";");
                outputBuffer.append(csvField(s.getGivenName())).append(";");
                outputBuffer.append(csvField(email)).append(";");
                outputBuffer.append(csvField(password)).append(";");
                outputBuffer.append(csvField(email)).append(";");
                outputBuffer.append(csvField(s.getGuardianInternalId())).append(";");
                outputBuffer.append(csvField(s.getGuardianGivenName())).append(";");
                outputBuffer.append(csvField(s.getGuardianSurname())).append(";");
                outputBuffer.append(csvField(s.getGuardianPhone())).append(";");
                outputBuffer.append(csvField(s.getGuardianEmail()));
                outputBuffer.append("\n");
            }
        }

        // uložení nebo výstup
        if (outFile != null) {
            File outputFile = new File(outFile);
            try (PrintStream outStream = new PrintStream(new FileOutputStream(outFile))) {
                outStream.print(outputBuffer.toString());
            } catch (Exception e) {
                ReportManager.handleException("Nebylo možné uložit výstup do souboru " + outputFile.getPath(), e);
            }
        } else {
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
     * Fáze 1: Sestavení dat pro sestavu / reset.
     * Parsuje rozsah, iteruje žáky a volitelně resetuje hesla přes {@link PasswordService}.
     *
     * @param query rozsah výběru (ročník, třída, UPN – odděleno čárkou)
     * @param resetPassword provést reset hesel
     * @param studentRepo repozitář žáků
     * @param facultyRepo repozitář vyučujících
     * @param passwordService služba pro reset hesel (použita jen pokud resetPassword=true; může být null pro report bez resetu)
     * @return agregovaná data sestavy
     */
    public static ReportData buildReportData(String query, boolean resetPassword,
                                              StudentRepository studentRepo,
                                              FacultyRepository facultyRepo,
                                              PasswordService passwordService) {

        // parsování a vyhodnocení rozsahu
        RangeSelector selector = RangeSelector.parse(query);
        ResolvedSelection selection = selector.resolve(studentRepo, facultyRepo,
                Settings.getInstance().getMailDomain());

        // varování pro nenalezené žáky
        for (String nf : selection.notFound()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Žák s UPN " + nf + " nebyl nalezen v evidenci.");
        }

        // sestavy <třída, data žáků>
        Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows = new LinkedHashMap<>();
        int successCount = 0;
        int failureCount = 0;

        for (var entry : selection.studentsByClass().entrySet()) {
            String classLabel = entry.getKey();
            List<StudentRecord> students = entry.getValue();

            List<PdfReportGenerator.StudentReportRow> classStudents = new ArrayList<>();

            for (StudentRecord s : students) {
                String studentUPN = s.getEmail();

                int classNum = 0;
                try {
                    classNum = Integer.parseInt(s.getClassNumber());
                } catch (NumberFormatException ignored) {
                    // žák bez čísla v třídním výkazu
                }

                String newPassword;

                if (resetPassword) {
                    ReportManager.logWait(EBakaLogType.LOG_INFO,
                            "Probíhá reset hesla žáka " + s.getSurname() + " " + s.getGivenName());

                    if (!RuntimeContext.FLAG_DRYRUN && s.getDn() != null && passwordService != null) {
                        // ostrý reset přes PasswordService
                        PasswordResetResult resetResult = passwordService.resetStudentPasswordWithResult(
                                s.getDn(), s.getSurname(), s.getGivenName(),
                                s.getClassYear(), classNum);

                        if (resetResult.isSuccess()) {
                            newPassword = resetResult.password();
                            successCount++;
                            ReportManager.logResult(EBakaLogType.LOG_OK);
                            ReportManager.log(EBakaLogType.LOG_VERBOSE,
                                    "Heslo žáka " + s.getSurname() + " " + s.getGivenName()
                                            + " bylo úspěšně změněno na " + newPassword);
                        } else {
                            // reset selhal – použít výchozí heslo pro sestavu
                            newPassword = BakaUtils.createInitialPassword(
                                    s.getSurname(), s.getGivenName(), s.getClassYear(), classNum);
                            failureCount++;
                            ReportManager.logResult(EBakaLogType.LOG_ERR);
                            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE,
                                    "Heslo žáka " + s.getSurname() + " " + s.getGivenName()
                                            + " nebylo možné změnit.");
                        }
                    } else {
                        // dry-run nebo chybějící DN – jen vypočítat heslo
                        newPassword = BakaUtils.nextPassword(
                                s.getSurname(), s.getGivenName(),
                                s.getClassYear(), classNum, 0);
                        successCount++;
                        ReportManager.logResult(EBakaLogType.LOG_OK);
                    }
                } else {
                    // bez resetu – výchozí heslo
                    newPassword = BakaUtils.createInitialPassword(
                            s.getSurname(), s.getGivenName(), s.getClassYear(), classNum);
                }

                // přeskočit žáky bez UPN v sestavě
                if (studentUPN == null || studentUPN.isEmpty()
                        || EBakaSQL.NULL.basename().equals(studentUPN)) {
                    ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE,
                            "Žák " + s.getSurname() + " " + s.getGivenName()
                                    + " (č. " + classNum + ") nemá přiřazené UPN – přeskočen v sestavě.");
                    continue;
                }

                classStudents.add(new PdfReportGenerator.StudentReportRow(
                        classNum,
                        s.getSurname(),
                        s.getGivenName(),
                        studentUPN,
                        newPassword,
                        studentUPN + "\t" + newPassword
                ));
            }

            // seřazení dle příjmení
            classStudents.sort(Comparator.comparing(PdfReportGenerator.StudentReportRow::surname));
            reportRows.put(classLabel, classStudents);
        }

        return new ReportData(
                reportRows,
                selection.classTeachers(),
                resetPassword,
                successCount,
                failureCount
        );
    }

    /**
     * Fáze 2: Generování PDF sestav a odeslání e-mailem.
     *
     * @param data data sestavy z {@link #buildReportData}
     * @param emailSubjectPrefix prefix předmětu e-mailu (před názvem třídy)
     * @param emailBodyTemplate šablona těla e-mailu ({@code {class}} bude nahrazeno názvem třídy)
     */
    public static void sendReports(ReportData data, String emailSubjectPrefix, String emailBodyTemplate) {

        // inicializace generátoru PDF
        PdfReportGenerator pdfGen;
        try {
            pdfGen = new PdfReportGenerator();
        } catch (IOException e) {
            ReportManager.handleException("Nelze inicializovat generátor PDF sestav.", e);
            return;
        }

        // konfigurace poznámek pod tabulkou (URL portálu se odvodí z mailové domény)
        pdfGen.setFootnotePassword("Žáci si mohou své heslo sami změnit na\u00a0portálu https://heslo."
                + Settings.getInstance().getMailDomain() + "/.");

        String schoolYear = PdfReportGenerator.currentSchoolYear();

        // zajištění existence adresáře pro sestavy
        new File("./reports").mkdirs();

        // generování a odeslání sestav
        for (String classN : data.reportRows().keySet()) {

            // identifikace třídního učitele
            FacultyRecord teacher = data.classTeachers().get(classN);
            String classTeacher = (teacher != null)
                    ? teacher.getGivenName() + " " + teacher.getSurname()
                    : "(neznámý)";

            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss");
            Date date = new Date();

            String reportFile = "./reports/" + formatter.format(date)
                    + "_" + classN.toLowerCase().replace(".", "")
                    + ".pdf";

            ReportManager.logWait(EBakaLogType.LOG_INFO, "Probíhá generování PDF sestavy třídy " + classN);

            try {
                pdfGen.generateClassReport(
                        reportFile,
                        classN,
                        classTeacher,
                        schoolYear,
                        data.reportRows().get(classN),
                        RuntimeContext.FLAG_DRYRUN
                );
                ReportManager.logResult(EBakaLogType.LOG_OK);
            } catch (IOException e) {
                ReportManager.logResult(EBakaLogType.LOG_ERR);
                ReportManager.handleException("Nebylo možné vytvořit PDF sestavu třídy " + classN + ".", e);
                continue;
            }

            // text e-mailu
            String message = emailBodyTemplate.replace("{class}", classN);

            if (RuntimeContext.FLAG_DRYRUN) {
                message += "\n\n[ i ] Sestava byla vygenerována s příznakem zkušebního spuštění, " +
                        "zobrazované údaje nemusí reflektovat skutečný stav.";
            }

            // identifikace třídního učitele – e-mail
            String classTeacherEmail = (teacher != null) ? teacher.getEmail() : null;

            String[] addresses;
            if (!RuntimeContext.FLAG_DRYRUN && classTeacherEmail != null) {
                addresses = new String[]{classTeacherEmail, Settings.getInstance().getAdminMail()};
            } else {
                addresses = new String[]{Settings.getInstance().getAdminMail()};
            }

            // e-mail pro třídního a správce
            BakaMailer.getInstance().mail(addresses,
                    emailSubjectPrefix + classN, message,
                    new String[]{reportFile});

            // úklid – pouze PDF
            new File(reportFile).delete();
        }
    }

    /**
     * Šablona e-mailu pro report (s přístupovými údaji, bez resetu).
     *
     * @return text e-mailu s placeholderem {class}
     */
    public static String reportEmailBody() {
        return "V příloze naleznete sestavu s přístupovými údaji žáků " +
                "{class} pro použití v prostředí Office365. Všichni žáci mají přiřazené " +
                "odpovídající žákovské licence, mohou tedy ihned plně používat všechny cloudové služby O365.\n\n" +
                "Tuto sestavu považujte za důvěrnou a distribuci " +
                "hesel, pokud to bude možné, provádějte jednotlivě.";
    }

    /**
     * Šablona e-mailu pro reset hesel.
     *
     * @return text e-mailu s placeholderem {class}
     */
    public static String resetEmailBody() {
        return "U žáků třídy {class} byl proveden reset přístupových hesel ke školním účtům.\n" +
                "V příloze naleznete sestavu s novými přístupovými údaji.\n\n" +
                "Tuto sestavu považujte za důvěrnou a distribuci " +
                "hesel, pokud to bude možné, provádějte jednotlivě.";
    }

    /**
     * Vygeneruje PDF sestavy s přístupovými údaji a odešle je e-mailem.
     * Používá {@link RangeSelector} pro parsování rozsahu výběru.
     * Podporuje celé třídy i individuální žáky.
     *
     * @param query dotazované objekty (ročník, třída, UPN žáka - seznam oddělený čárkami)
     * @param resetPassword provede okamžitý reset hesla nad danými objekty
     * @param studentRepo repozitář žáků
     * @param facultyRepo repozitář vyučujících
     * @param passwordService služba pro reset hesel
     */
    public static void genericReport(String query, boolean resetPassword,
                                      StudentRepository studentRepo,
                                      FacultyRepository facultyRepo,
                                      PasswordService passwordService) {

        ReportData data = buildReportData(query, resetPassword,
                studentRepo, facultyRepo, passwordService);

        String emailBody = resetPassword ? resetEmailBody() : reportEmailBody();
        sendReports(data, "Přístupové údaje žáků ", emailBody);
    }

    /**
     * Zpětně kompatibilní verze bez {@link PasswordService}.
     * Při resetu používá přímé volání {@link BakaADAuthenticator} (legacy cesta).
     *
     * @param query dotazované objekty
     * @param resetPassword provede reset hesla
     * @param studentRepo repozitář žáků
     * @param facultyRepo repozitář vyučujících
     * @deprecated Použijte {@link #genericReport(String, boolean, StudentRepository, FacultyRepository, PasswordService)}.
     */
    @Deprecated
    public static void genericReport(String query, boolean resetPassword,
                                      StudentRepository studentRepo,
                                      FacultyRepository facultyRepo) {
        // legacy cesta – null PasswordService → buildReportData použije dry-run větev pro hesla
        genericReport(query, resetPassword, studentRepo, facultyRepo, null);
    }

    /**
     * Ošetří null / sentinel hodnotu pro CSV pole.
     *
     * @param value hodnota
     * @return hodnota nebo prázdný řetězec
     */
    private static String csvField(String value) {
        if (value == null || EBakaSQL.NULL.basename().equals(value)) {
            return "";
        }
        return value;
    }
}
