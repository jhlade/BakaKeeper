package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.CliProgressListener;
import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.service.SyncReport;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro kontrolu stavu synchronizace (read-only).
 *
 * @author Jan Hladěna
 */
@Command(name = "status", description = "Provede kontrolu stavu synchronizace (bez zápisu).")
public class StatusCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        SyncReport report = sf.getOrchestrator().runFullSync(
                false, new CliProgressListener(RuntimeContext.FLAG_VERBOSE));
        App.printSummary(report.results());

        return 0;
    }
}
