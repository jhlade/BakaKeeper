package cz.zsstudanka.skola.bakakeeper.commands;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.RuntimeContext;
import cz.zsstudanka.skola.bakakeeper.components.KeyStoreManager;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.util.concurrent.Callable;

/**
 * Příkaz pro inicializaci konfigurace.
 *
 * @author Jan Hladěna
 */
@Command(name = "init", description = "Inicializace parametrů programu ze souboru nebo interaktivně.")
public class InitCommand implements Callable<Integer> {

    @ParentCommand App app;

    @Option(names = {"-f", "--file"}, description = "Cesta k souboru s nastavením (YAML).")
    String configFile;

    @Option(names = "--interactive", description = "Interaktivní režim inicializace.")
    boolean interactive;

    @Override
    public Integer call() {
        app.applyGlobalFlags();
        RuntimeContext.FLAG_INIT = Boolean.TRUE;

        if (interactive) {
            Settings.getInstance().interactivePrompt();
        } else if (configFile != null) {
            Settings.getInstance().load(configFile);
        } else {
            if (RuntimeContext.FLAG_VERBOSE) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá pokus o inicializaci s výchozím souborem settings.yml.");
            }
            Settings.getInstance().load("./settings.yml");
        }

        return finalizeInit();
    }

    /** Dokončení inicializace – validace, uložení, SSL. */
    private int finalizeInit() {
        if (!Settings.getInstance().isValid()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné vytvořit platná nastavení.");
            return 1;
        }

        Settings.getInstance().save();

        if (Settings.getInstance().isValid()) {
            if (Settings.getInstance().useSSL()) {
                if (KeyStoreManager.initialize()) {
                    ReportManager.log(EBakaLogType.LOG_OK, "Výchozí úložiště certifikátů bylo vytvořeno.");
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné vytvořit výchozí úložiště certifikátů.");
                }
            }

            if (RuntimeContext.PASSPHRASE.length() > 0) {
                ReportManager.log(EBakaLogType.LOG_OK, "Šifrovaný datový soubor s nastavením byl úspěšně vytvořen.");
            } else {
                ReportManager.log(EBakaLogType.LOG_OK, "Datový soubor s nastavením byl úspěšně vytvořen.");
            }
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Vytvoření konfigurace se nezdařilo.");
            return 1;
        }

        return 0;
    }
}
