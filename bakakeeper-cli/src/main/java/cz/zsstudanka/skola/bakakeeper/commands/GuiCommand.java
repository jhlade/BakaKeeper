package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import picocli.CommandLine.Command;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

/**
 * Subcommand pro spuštění grafického uživatelského rozhraní.
 * Spouští JavaFX Application přes reflexi, aby CLI modul nemusel
 * mít přímou compile-time závislost na JavaFX.
 */
@Command(name = "gui",
        description = "Spustit grafické uživatelské rozhraní.",
        mixinStandardHelpOptions = true)
public class GuiCommand implements Callable<Integer> {

    private static final String APP_CLASS = "cz.zsstudanka.skola.bakakeeper.gui.BakaKeeperApp";

    @Override
    public Integer call() {
        try {
            // ověřit dostupnost JavaFX na classpath/module path
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "JavaFX není dostupné v aktuálním JRE.");
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Pro spuštění GUI nainstalujte JRE s podporou JavaFX (např. Azul Zulu FX).");
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "  https://www.azul.com/downloads/?package=jdk-fx");
            return 1;
        }

        try {
            // spuštění Application.launch() přes reflexi
            Class<?> appClass = Class.forName(APP_CLASS);
            Class<?> fxAppClass = Class.forName("javafx.application.Application");
            Method launchMethod = fxAppClass.getMethod("launch", Class.class, String[].class);
            launchMethod.invoke(null, appClass, new String[]{});
            return 0;
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Chyba při spuštění GUI: " + cause.getMessage());
            ReportManager.handleException("Spuštění GUI selhalo", (Exception) cause);
            return 1;
        }
    }
}
