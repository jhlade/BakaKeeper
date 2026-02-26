package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.service.PasswordResetResult;
import cz.zsstudanka.skola.bakakeeper.service.PasswordService;
import cz.zsstudanka.skola.bakakeeper.service.RangeSelector;
import cz.zsstudanka.skola.bakakeeper.service.ResolvedSelection;
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

    /**
     * Export žákovských informací ve formě CSV.
     * Používá {@link RangeSelector} pro parsování rozsahu – stejný formát jako {@code --report}.
     *
     * @param query rozsah výběru (ročník, třída, UPN – odděleno čárkou)
     * @param outFile výstupní soubor (null = stdout)
     * @param studentRepo repozitář žáků
     * @param facultyRepo repozitář vyučujících
     * @param config konfigurace aplikace
     */
    public static void exportStudentCSVdata(String query, String outFile,
                                             StudentRepository studentRepo,
                                             FacultyRepository facultyRepo,
                                             AppConfig config) {

        if (config.isDebug()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "====== [ EXPORT ] ======");
        }

        // parsování a vyhodnocení rozsahu
        RangeSelector selector = RangeSelector.parse(query);
        ResolvedSelection selection = selector.resolve(studentRepo, facultyRepo,
                config.getMailDomain());

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

        if (config.isDebug()) {
            ReportManager.log(EBakaLogType.LOG_DEBUG, "====== [ /EXPORT ] ======");
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
     * @param config konfigurace aplikace
     * @return agregovaná data sestavy
     */
    public static ReportData buildReportData(String query, boolean resetPassword,
                                              StudentRepository studentRepo,
                                              FacultyRepository facultyRepo,
                                              PasswordService passwordService,
                                              AppConfig config) {

        // parsování a vyhodnocení rozsahu
        RangeSelector selector = RangeSelector.parse(query);
        ResolvedSelection selection = selector.resolve(studentRepo, facultyRepo,
                config.getMailDomain());

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
     * @param config konfigurace aplikace
     * @param mailer poštovní služba
     */
    public static void sendReports(ReportData data, String emailSubjectPrefix, String emailBodyTemplate,
                                    AppConfig config, BakaMailer mailer) {

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
                + config.getMailDomain() + "/.");

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
                addresses = new String[]{classTeacherEmail, config.getAdminMail()};
            } else {
                addresses = new String[]{config.getAdminMail()};
            }

            // e-mail pro třídního a správce
            mailer.mail(addresses,
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
     * @param config konfigurace aplikace
     * @param mailer poštovní služba
     */
    public static void genericReport(String query, boolean resetPassword,
                                      StudentRepository studentRepo,
                                      FacultyRepository facultyRepo,
                                      PasswordService passwordService,
                                      AppConfig config, BakaMailer mailer) {

        ReportData data = buildReportData(query, resetPassword,
                studentRepo, facultyRepo, passwordService, config);

        String emailBody = resetPassword ? resetEmailBody() : reportEmailBody();
        sendReports(data, "Přístupové údaje žáků ", emailBody, config, mailer);
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
