package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.CliProgressListener;
import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.service.SyncResult;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Příkaz pro synchronizaci záznamů.
 *
 * @author Jan Hladěna
 */
@Command(name = "sync", description = "Provede synchronizaci záznamů z Bakalářů do Active Directory.")
public class SyncCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Option(names = "--dryrun", description = "Režim bez zápisu – změny se pouze navrhnou.")
    boolean dryrun;

    @Override
    public Integer call() {
        app.applyGlobalFlags();
        if (dryrun) {
            RuntimeContext.FLAG_DRYRUN = Boolean.TRUE;
        }

        ServiceFactory sf = app.createServiceFactory();

        boolean repair = !RuntimeContext.FLAG_DRYRUN;
        List<SyncResult> results = sf.getOrchestrator().runFullSync(
                repair, new CliProgressListener(RuntimeContext.FLAG_VERBOSE));
        App.printSummary(results);

        return 0;
    }
}
