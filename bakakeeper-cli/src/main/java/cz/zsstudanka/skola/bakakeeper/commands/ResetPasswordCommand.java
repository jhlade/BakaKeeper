package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.ReportData;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro hromadný reset hesla žáků na výchozí hodnotu.
 * Používá range selector – stejný formát rozsahu jako {@code report} a {@code export}.
 *
 * @author Jan Hladěna
 */
@Command(name = "reset", description = "Provede reset hesla uživatelů na výchozí hodnotu.")
public class ResetPasswordCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Rozsah výběru (*, ročník, třída, UPN – oddělené čárkou).")
    String scope;

    @Option(names = "--report",
            description = "Po resetu odešle PDF sestavu s novými údaji třídnímu učiteli a správci.")
    boolean sendReport;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();

        // fáze 1: reset hesel (vždy)
        ReportData data = Export.buildReportData(
                scope, true,
                sf.getStudentRepo(), sf.getFacultyRepo(),
                sf.getPasswordService());

        // souhrn do konzole
        ReportManager.log(EBakaLogType.LOG_STDOUT,
                "Reset dokončen: " + data.successCount() + " úspěšných, "
                        + data.failureCount() + " chyb.");

        // fáze 2: volitelná sestava
        if (sendReport) {
            Export.sendReports(data, "Reset hesel – ", Export.resetEmailBody());
        }

        return data.failureCount() > 0 ? 1 : 0;
    }
}
