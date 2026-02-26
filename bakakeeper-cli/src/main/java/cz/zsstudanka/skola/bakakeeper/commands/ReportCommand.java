package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro generování PDF sestav přihlašovacích údajů.
 * Volitelně resetuje hesla ({@code --reset}).
 *
 * @author Jan Hladěna
 */
@Command(name = "report", description = "Odešle PDF sestavu přihlašovacích údajů třídnímu učiteli a správci.")
public class ReportCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Rozsah výběru (*, ročník, třída, UPN – oddělené čárkou).")
    String scope;

    @Option(names = "--reset", description = "Provede reset hesel zvolených uživatelů. " +
            "Preferovaná varianta: 'bakakeeper reset <scope> --report'.")
    boolean resetPassword;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        if (resetPassword) {
            ReportManager.log(EBakaLogType.LOG_INFO,
                    "Upozornění: přepínač --reset je zastaralý. "
                  + "Použijte 'bakakeeper reset " + scope + " --report'.");
        }

        ServiceFactory sf = app.createServiceFactory();
        Export.genericReport(scope, resetPassword,
                sf.getStudentRepo(), sf.getFacultyRepo(),
                sf.getPasswordService(), sf.getConfig(), BakaMailer.getInstance());

        return 0;
    }
}
