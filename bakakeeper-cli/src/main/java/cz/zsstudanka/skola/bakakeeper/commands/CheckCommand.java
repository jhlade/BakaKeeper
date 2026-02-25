package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro kontrolu přístupu ke službám (AD, SQL, SMTP).
 *
 * @author Jan Hladěna
 */
@Command(name = "check", description = "Provede kontrolu nastavení programu a přístupu ke službám.")
public class CheckCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Override
    public Integer call() {
        app.applyGlobalFlags();
        app.loadSettings();

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování validity konfiguračních dat");
        if (!Settings.getInstance().isValid()) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na řadič Active Directory");
        if (BakaADAuthenticator.getInstance().isAuthenticated()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SQL Server");
        if (BakaSQL.getInstance().testSQL()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SMTP server");
        if (BakaMailer.getInstance().testSMTP()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }

        return 0;
    }
}
