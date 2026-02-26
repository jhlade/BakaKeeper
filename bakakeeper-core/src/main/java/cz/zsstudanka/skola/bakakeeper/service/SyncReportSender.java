package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Odesílá strukturované e-mailové hlášení po synchronizaci.
 * <ul>
 *   <li>Správci: souhrn všech změn a chyb</li>
 *   <li>Třídním učitelům: konkrétní chyby zástupců v jejich třídě</li>
 * </ul>
 *
 * @author Jan Hladěna
 */
public class SyncReportSender {

    private final AppConfig config;
    private final BakaMailer mailer;

    public SyncReportSender(AppConfig config, BakaMailer mailer) {
        this.config = config;
        this.mailer = mailer;
    }

    /**
     * Odešle souhrnný e-mail správci ICT.
     *
     * @param report výsledek synchronizace
     */
    public void sendAdminReport(SyncReport report) {
        StringBuilder body = new StringBuilder();
        body.append("Souhrn synchronizace BakaKeeper\n");
        body.append("===============================\n\n");

        body.append("Vytvořeno:    ").append(report.created()).append("\n");
        body.append("Aktualizováno: ").append(report.updated()).append("\n");
        body.append("Spárováno:    ").append(report.paired()).append("\n");
        body.append("Vyřazeno:     ").append(report.retired()).append("\n");
        body.append("Beze změny:   ").append(report.noChange()).append("\n");
        body.append("Chyby:        ").append(report.errors()).append("\n\n");

        // detaily chyb
        List<SyncResult> errors = report.results().stream()
                .filter(r -> r.getType() == SyncResult.Type.ERROR)
                .toList();

        if (!errors.isEmpty()) {
            body.append("Detaily chyb:\n");
            body.append("-------------\n");
            for (SyncResult err : errors) {
                body.append("  - ").append(err).append("\n");
            }
            body.append("\n");
        }

        // chyby zástupců
        if (!report.guardianErrors().isEmpty()) {
            body.append("Validační chyby zákonných zástupců (").append(report.guardianErrors().size()).append("):\n");
            body.append("------------------------------------------\n");
            for (GuardianValidationError gve : report.guardianErrors()) {
                body.append("  - ").append(gve).append("\n");
            }
        }

        mailer.mail(
                new String[]{config.getAdminMail()},
                "BakaKeeper – souhrn synchronizace"
                        + (report.isSuccess() ? "" : " [CHYBY]"),
                body.toString(),
                null
        );
    }

    /**
     * Odešle hlášení třídním učitelům – každý učitel dostane
     * pouze chyby zástupců ze své třídy.
     *
     * @param report výsledek synchronizace
     */
    public void sendTeacherNotifications(SyncReport report) {
        if (report.guardianErrors().isEmpty()) {
            return;
        }

        // seskupení chyb podle e-mailu třídního učitele
        Map<String, List<GuardianValidationError>> byTeacher = report.guardianErrors().stream()
                .filter(e -> e.teacherEmail() != null && !e.teacherEmail().isEmpty())
                .collect(Collectors.groupingBy(
                        GuardianValidationError::teacherEmail,
                        LinkedHashMap::new,
                        Collectors.toList()));

        for (var entry : byTeacher.entrySet()) {
            String teacherEmail = entry.getKey();
            List<GuardianValidationError> classErrors = entry.getValue();

            StringBuilder body = new StringBuilder();
            body.append("Při synchronizaci BakaKeeper byly zjištěny následující problémy\n");
            body.append("s údaji zákonných zástupců ve Vaší třídě:\n\n");

            for (GuardianValidationError gve : classErrors) {
                body.append("  - ").append(gve.studentName()).append(": ");
                body.append(formatErrorType(gve.type()));
                if (gve.detail() != null) {
                    body.append(" (").append(gve.detail()).append(")");
                }
                body.append("\n");
            }

            body.append("\nProsíme o kontrolu a opravu těchto údajů v evidenci Bakaláři.\n");

            mailer.mail(
                    new String[]{teacherEmail, config.getAdminMail()},
                    "BakaKeeper – chyby zástupců třídy " + classErrors.getFirst().className(),
                    body.toString(),
                    null
            );
        }
    }

    /**
     * Odešle všechna hlášení (správci + třídním).
     *
     * @param report výsledek synchronizace
     */
    public void sendAll(SyncReport report) {
        sendAdminReport(report);
        sendTeacherNotifications(report);
    }

    /**
     * Formátuje typ chyby do čitelné podoby.
     */
    private static String formatErrorType(GuardianValidationError.ErrorType type) {
        return switch (type) {
            case MISSING_PHONE -> "chybí telefonní číslo";
            case INVALID_PHONE -> "neplatný formát telefonu";
            case MISSING_EMAIL -> "chybí e-mailová adresa";
            case INVALID_EMAIL -> "neplatný formát e-mailu";
            case NO_PRIMARY_GUARDIAN -> "nemá primárního zákonného zástupce";
        };
    }
}
