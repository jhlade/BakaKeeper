package cz.zsstudanka.skola.bakakeeper;

import cz.zsstudanka.skola.bakakeeper.commands.*;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.service.ServiceFactory;
import cz.zsstudanka.skola.bakakeeper.service.SyncResult;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Nástroj pro synchronizaci záznamů z Bakalářů a informacemi v Active Directory.
 *
 * @author Jan Hladěna
 */
@Command(name = "bakakeeper",
        mixinStandardHelpOptions = true,
        versionProvider = App.BakaVersionProvider.class,
        description = "Synchronizační nástroj evidence žáků v programu Bakaláři s uživatelskými účty v Active Directory.",
        footer = {
                "",
                "Příklady:",
                "  bakakeeper check -p heslo           Kontrola konektivity",
                "  bakakeeper sync --verbose            Synchronizace s podrobným výstupem",
                "  bakakeeper report 5.A                Sestava přihlašovacích údajů",
                "  bakakeeper reset 5.A --report        Reset hesel a sestava třídy",
                "  bakakeeper reset *                   Reset hesel celé školy",
                "  bakakeeper suspend 5.A               Zakázání účtů třídy",
                "  bakakeeper unsuspend 5.A             Povolení účtů třídy",
                "  bakakeeper export 5 -o seznam.csv    CSV export ročníku",
                "  bakakeeper init -f settings.yml      Inicializace ze souboru"
        },
        subcommands = {
                CheckCommand.class,
                SyncCommand.class,
                StatusCommand.class,
                InitCommand.class,
                ReportCommand.class,
                ExportCommand.class,
                IdentifyCommand.class,
                ResetPasswordCommand.class,
                SetPasswordCommand.class,
                SuspendCommand.class,
                UnsuspendCommand.class,
                InternalDbCommand.class
        })
public class App implements Callable<Integer> {

    @Option(names = "--verbose", description = "Aktivovat podrobný výstup.", scope = ScopeType.INHERIT)
    boolean verbose;

    @Option(names = "--debug", description = "Aktivovat ladící výstup.", scope = ScopeType.INHERIT)
    boolean debug;

    @Option(names = "--develmode", description = "Vývojový režim – neprobíhá zápis do ostrých dat.", scope = ScopeType.INHERIT)
    boolean develMode;

    @Option(names = {"-p", "--passphrase"}, description = "Heslo ke konfiguraci.", scope = ScopeType.INHERIT)
    String passphrase;

    @Option(names = {"-l", "--log"}, description = "Soubor pro zápis protokolu.", scope = ScopeType.INHERIT)
    String logFile;

    /** Stav: globální flagy byly aplikovány. */
    private boolean flagsApplied = false;

    /** Stav: konfigurace byla načtena. */
    private boolean settingsLoaded = false;

    /** Cache ServiceFactory instance. */
    private ServiceFactory serviceFactory;

    @Override
    public Integer call() {
        // spuštění bez subcommandu → vypsat info
        Version appVersion = Version.getInstance();
        System.out.println(appVersion.getInfo());
        System.out.println("Spusťte program s parametrem --help pro nápovědu.\n");
        return 0;
    }

    /**
     * Aplikuje globální příznaky (verbose, debug, develmode, passphrase, log).
     * Bezpečné volat vícekrát – aplikuje se jen jednou.
     */
    public void applyGlobalFlags() {
        if (flagsApplied) return;
        flagsApplied = true;

        if (develMode) {
            RuntimeContext.FLAG_DEVEL = Boolean.TRUE;
            ReportManager.log(EBakaLogType.LOG_DEVEL, "Je aktivní vývojový režim. Nebude se zapisovat do ostrých dat evidence.");
        }

        if (verbose || debug) {
            RuntimeContext.FLAG_VERBOSE = Boolean.TRUE;
            Settings.getInstance().verbosity(RuntimeContext.FLAG_VERBOSE);
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Aktivován výstup podrobných informací.");
        }

        if (debug) {
            RuntimeContext.FLAG_DEBUG = Boolean.TRUE;
            Settings.getInstance().debug(RuntimeContext.FLAG_DEBUG);
            ReportManager.log(EBakaLogType.LOG_DEBUG, "Aktivován výstup ladících informací.");
        }

        if (logFile != null) {
            ReportManager.getInstance().setLogfile(logFile);
        }

        if (passphrase != null) {
            RuntimeContext.PASSPHRASE = passphrase;
            Settings.getInstance().setPassphrase(RuntimeContext.PASSPHRASE);
        }
    }

    /**
     * Načte a validuje konfiguraci.
     * Pro příkazy, které nepotřebují ServiceFactory (check, id, internaldb).
     */
    public void loadSettings() {
        if (settingsLoaded) return;
        settingsLoaded = true;

        Settings.getInstance().load();
        if (!Settings.getInstance().isValid()) {
            throw new CommandLine.ExecutionException(
                    new CommandLine(this), "Konfigurační data nejsou platná.");
        }
        Settings.getInstance().setDevelMode(RuntimeContext.FLAG_DEVEL);
    }

    /**
     * Vytvoří ServiceFactory (lazy, volají subcommandy).
     * Obsahuje načtení konfigurace + sestavení DI grafu.
     *
     * @return instance ServiceFactory
     */
    public ServiceFactory createServiceFactory() {
        if (serviceFactory != null) return serviceFactory;

        loadSettings();
        serviceFactory = new ServiceFactory(Settings.getInstance());
        return serviceFactory;
    }

    /**
     * Vypíše souhrn výsledků synchronizace.
     *
     * @param results seznam výsledků
     */
    public static void printSummary(List<SyncResult> results) {
        long created = results.stream().filter(r -> r.getType() == SyncResult.Type.CREATED).count();
        long updated = results.stream().filter(r -> r.getType() == SyncResult.Type.UPDATED).count();
        long retired = results.stream().filter(r -> r.getType() == SyncResult.Type.RETIRED).count();
        long paired = results.stream().filter(r -> r.getType() == SyncResult.Type.PAIRED).count();
        long errors = results.stream().filter(r -> r.getType() == SyncResult.Type.ERROR).count();
        long noChange = results.stream().filter(r -> r.getType() == SyncResult.Type.NO_CHANGE).count();

        ReportManager.log(EBakaLogType.LOG_STDOUT, "--- Souhrn ---");
        if (created > 0) ReportManager.log(EBakaLogType.LOG_STDOUT, "  Vytvořeno: " + created);
        if (updated > 0) ReportManager.log(EBakaLogType.LOG_STDOUT, "  Aktualizováno: " + updated);
        if (retired > 0) ReportManager.log(EBakaLogType.LOG_STDOUT, "  Vyřazeno: " + retired);
        if (paired > 0) ReportManager.log(EBakaLogType.LOG_STDOUT, "  Spárováno: " + paired);
        if (noChange > 0) ReportManager.log(EBakaLogType.LOG_STDOUT, "  Beze změny: " + noChange);
        if (errors > 0) ReportManager.log(EBakaLogType.LOG_ERR, "  Chyby: " + errors);
    }

    /**
     * Chod programu.
     *
     * @param args argumenty příkazového řádku
     */
    public static void main(String[] args) {
        // Vynutit UTF-8 na stdout/stderr – Windows CMD jinak používá OEM code page (cp852/cp437)
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));

        int exitCode = new CommandLine(new App())
                .setExecutionExceptionHandler((ex, cmd, parseResult) -> {
                    ReportManager.log(EBakaLogType.LOG_ERR, ex.getMessage());
                    return 1;
                })
                .execute(args);
        System.exit(exitCode);
    }

    /** Picocli version provider – deleguje na Version singleton. */
    static class BakaVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() {
            return new String[] { Version.getInstance().getInfo(true) };
        }
    }
}
