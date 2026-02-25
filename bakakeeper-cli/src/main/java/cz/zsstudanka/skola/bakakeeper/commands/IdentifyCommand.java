package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro identifikaci AD účtu.
 *
 * @author Jan Hladěna
 */
@Command(name = "id", description = "Identifikace účtu uživatele v Active Directory.")
public class IdentifyCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Parameters(index = "0", description = "Uživatelské jméno (login).")
    String login;

    @Override
    public Integer call() {
        app.applyGlobalFlags();
        app.loadSettings();

        Export.identify(login);

        return 0;
    }
}
