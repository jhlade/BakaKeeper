package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro CSV export údajů o žácích.
 *
 * @author Jan Hladěna
 */
@Command(name = "export", description = "Provede export údajů o žácích ve formátu CSV.")
public class ExportCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Rozsah výběru (*, ročník, třída, UPN – oddělené čárkou).")
    String scope;

    @Option(names = {"-o", "--output"}, description = "Výstupní soubor CSV (pokud není uveden, použije se stdout).")
    String outFile;

    @Override
    public Integer call() {
        app.applyGlobalFlags();

        ServiceFactory sf = app.createServiceFactory();
        Export.exportStudentCSVdata(scope, outFile, sf.getStudentRepo(), sf.getFacultyRepo());

        return 0;
    }
}
