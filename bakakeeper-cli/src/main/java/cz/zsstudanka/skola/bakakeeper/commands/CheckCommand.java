package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.service.CheckResult;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.List;
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

        ServiceFactory sf = app.createServiceFactory();
        List<CheckResult> results = sf.getCheckService().checkAll();

        boolean allOk = true;
        for (CheckResult result : results) {
            ReportManager.logWait(EBakaLogType.LOG_TEST,
                    "Testování: " + result.service());
            if (result.ok()) {
                ReportManager.logResult(EBakaLogType.LOG_OK);
            } else {
                ReportManager.logResult(EBakaLogType.LOG_ERR);
                allOk = false;
            }
        }

        return allOk ? 0 : 1;
    }
}
