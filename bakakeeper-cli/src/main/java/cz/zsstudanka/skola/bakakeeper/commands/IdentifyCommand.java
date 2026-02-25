package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.service.IdentifyResult;
import cz.zsstudanka.skola.bakakeeper.service.IdentifyService;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Příkaz pro identifikaci AD účtu.
 * Podporuje dotazy na třídy (souhrn), ročníky, všechny (*) i individuální uživatele.
 *
 * @author Jan Hladěna
 */
@Command(name = "id", description = "Identifikace účtu uživatele v Active Directory.")
public class IdentifyCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Dotaz: login, třída (5.A), ročník (5), vše (*) nebo kombinace čárkou.")
    String query;

    /** Windows FILETIME epoch offset (100ns ticks od 1. 1. 1601 do 1. 1. 1970). */
    private static final long FILETIME_EPOCH_DIFF = 11644473600000L;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        IdentifyService identifyService = sf.getIdentifyService();

        List<IdentifyResult> results = identifyService.identify(query);

        boolean hasError = false;
        for (IdentifyResult result : results) {
            switch (result) {
                case IdentifyResult.ClassSummaryResult summary -> printClassSummary(summary);
                case IdentifyResult.IndividualDetailResult detail -> printIndividualDetail(detail);
                case IdentifyResult.NotFoundResult notFound -> {
                    ReportManager.log(EBakaLogType.LOG_ERR,
                            "Nenalezeno: " + notFound.query() + " – " + notFound.message());
                    hasError = true;
                }
            }
        }

        return hasError ? 1 : 0;
    }

    /**
     * Vypíše souhrn tříd.
     */
    private void printClassSummary(IdentifyResult.ClassSummaryResult summary) {
        ReportManager.log(EBakaLogType.LOG_STDOUT, "--- Souhrn tříd ---");

        for (IdentifyResult.ClassInfo ci : summary.classes()) {
            String teacher = (ci.teacherName() != null)
                    ? ci.teacherName() + " (" + ci.teacherEmail() + ")"
                    : "–";
            ReportManager.log(EBakaLogType.LOG_STDOUT,
                    "  " + ci.classLabel() + " \t" + ci.studentCount() + " žáků \tTřídní: " + teacher);
        }

        ReportManager.log(EBakaLogType.LOG_STDOUT, "  Celkem: " + summary.totalCount() + " žáků");
    }

    /**
     * Vypíše detail jednotlivého uživatele.
     */
    private void printIndividualDetail(IdentifyResult.IndividualDetailResult d) {
        ReportManager.log(EBakaLogType.LOG_STDOUT, "Celé jméno: \t\t" + d.displayName());
        ReportManager.log(EBakaLogType.LOG_STDOUT, "E-mailová adresa: \t" + d.email());

        // poslední přihlášení
        ReportManager.log(EBakaLogType.LOG_STDOUT, "Poslední přihlášení: \t" + formatFileTime(d.lastLogon()));

        // poslední změna hesla
        ReportManager.log(EBakaLogType.LOG_STDOUT, "Poslední změna hesla: \t" + formatFileTime(d.pwdLastSet()));

        // typ účtu
        ReportManager.log(EBakaLogType.LOG_STDOUT, "Typ účtu: \t\t" + d.accountType());

        // skupiny
        if (!d.groups().isEmpty()) {
            ReportManager.log(EBakaLogType.LOG_STDOUT,
                    "Skupiny: \t\t(" + d.groups().size() + ") " + String.join(", ", d.groups()));
        }

        // aliasy – pouze sekundární smtp: záznamy (lowercase prefix = alias v Exchange)
        List<String> aliases = d.proxyAddresses().stream()
                .filter(p -> p.startsWith("smtp:"))
                .map(p -> p.substring(5))
                .toList();
        if (!aliases.isEmpty()) {
            ReportManager.log(EBakaLogType.LOG_STDOUT,
                    "Aliasy: \t\t(" + aliases.size() + ") " + String.join(", ", aliases));
        }

        // třída + třídní učitel (pro žáky)
        if (d.className() != null) {
            String teacherInfo = (d.teacherName() != null)
                    ? d.teacherName() + " (" + d.teacherEmail() + ")"
                    : "–";
            ReportManager.log(EBakaLogType.LOG_STDOUT, "Třída: \t\t\t" + d.className() + " (" + teacherInfo + ")");
        }

        // výchozí heslo
        if (d.defaultPassword() != null) {
            ReportManager.log(EBakaLogType.LOG_STDOUT, "Výchozí heslo žáka: \t" + d.defaultPassword());
        }

        // zákonný zástupce
        if (d.guardianName() != null) {
            ReportManager.log(EBakaLogType.LOG_STDOUT,
                    "Zákonný zástupce:\t" + d.guardianName()
                            + " (" + nullSafe(d.guardianEmail()) + ", " + nullSafe(d.guardianPhone()) + ")");
        }

        // DN (verbose)
        ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "DN: " + d.dn());
        ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "UAC: " + d.uac());
    }

    /**
     * Konvertuje Windows FILETIME ticks na čitelný datum s info o stáří.
     */
    private String formatFileTime(Long ticks) {
        if (ticks == null || ticks == 0) {
            return "nikdy";
        }

        Date date = new Date((ticks / 10000L) - FILETIME_EPOCH_DIFF);
        long daysAgo = (System.currentTimeMillis() - date.getTime()) / 1000 / 3600 / 24;

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
        return dateFormat.format(date) + " (" + (daysAgo > 10000 ? "nikdy" : "před " + daysAgo + " dny") + ")";
    }

    private String nullSafe(String value) {
        return (value != null) ? value : "–";
    }
}
