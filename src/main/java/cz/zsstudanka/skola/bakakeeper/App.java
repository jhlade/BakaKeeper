package cz.zsstudanka.skola.bakakeeper;

import cz.zsstudanka.skola.bakakeeper.components.HelpManager;
import cz.zsstudanka.skola.bakakeeper.components.KeyStoreManager;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.Manipulation;
import cz.zsstudanka.skola.bakakeeper.routines.Sync;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Nástroj pro synchronizaci záznamů z Bakalářů a informacemi v Active Directory.
 *
 * @author Jan Hladěna
 */
public class App {

    /** příznak vývoajářského režimu - neprobíhá zápis do ostrých dat evidence */
    public static Boolean FLAG_DEVEL = false;

    /** příznak pro nezapisování žádných ostrých dat */
    public static Boolean FLAG_DRYRUN = false;

    /** příznak inicializace */
    public static Boolean FLAG_INIT = false;

    /** globální heslo pro sezení */
    public static String PASSPHRASE = "";
    /** příznak podrobností */
    public static Boolean FLAG_VERBOSE = false;
    /** příznak ladění */
    public static Boolean FLAG_DEBUG = false;

    /**
     * Chod programu.
     *
     * @param args argumenty viz --help
     */
    public static void main(String[] args) {

        // argumenty programu
        final Map<String, List<String>> params = new HashMap<>();

        List<String> options = null;
        for (final String a : args) {
            // argumenty se dvěma pomlčkami - samostatné
            if (a.length() >= 3 && a.charAt(0) == '-' && a.charAt(1) == '-') {

                options = new ArrayList<>();
                params.put(a.substring(2), options);
                continue;
            }

            // argumenty s jednou pomlčkou - mohou vázat subargumenty
            if (a.charAt(0) == '-') {

                if (a.length() < 2) {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chyba v argumentu: " + a);
                    return;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Neplatné argumenty programu.");
                return;
            }
        }

        // spuštění bez parametrů
        if (params.size() == 0) {
            actionPrintRun();
        } else {

            // přepnutí do vývojového režimu
            if (params.containsKey("develmode")) {
                FLAG_DEVEL = true;
                // TODO změna hlášení
                ReportManager.log(EBakaLogType.LOG_DEVEL, "Je aktivní vývojový režim. Nebude se zapisovat do ostrých dat evidence.");
            }

            // TODO - nezapisování ostrých dat
            if (params.containsKey("dryrun")) {
                FLAG_DRYRUN = true;
                // TODO - hlášení + implementace
            }

            // společná nastavení - podrobný režim
            if (params.containsKey("verbose")) {
                FLAG_VERBOSE = true;
                Settings.getInstance().verbosity(FLAG_VERBOSE);
                ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Aktivován výstup podrobných informací.");
            }

            // společná nastavení - ladící režim
            if (params.containsKey("debug")) {

                // automatické přidání --verbose
                FLAG_VERBOSE = true;
                Settings.getInstance().verbosity(FLAG_VERBOSE);

                FLAG_DEBUG = true;
                Settings.getInstance().debug(FLAG_DEBUG);

                ReportManager.log(EBakaLogType.LOG_DEBUG, "Aktivován výstup ladících informací.");
            }

            if (params.containsKey("log")) {
                if (params.get("log").size() == 1) {
                    ReportManager.getInstance().setLogfile(params.get("log").get(0));
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -log. (Použití: -log protokol.log)");
                }
            }

            // společná nastavení - heslo ke konfiguraci
            if (params.containsKey("passphrase")) {
                if (params.get("passphrase").size() == 1) {
                    PASSPHRASE = params.get("passphrase").get(0);
                    Settings.getInstance().setPassphrase(PASSPHRASE);
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný parametr -passphrase.");
                    return;
                }
            }

            // nápověda
            if (params.containsKey("help")) {
                actionPrintHelp();
                return;
            }

            // verze
            if (params.containsKey("version")) {
                System.out.print(Version.getInstance().getInfo(true));
                return;
            }

            // inicializace nastavení
            if (params.containsKey("init")) {

                // probíhá inicializace - dočasně se důvěřuje všem předaným certifikátům
                App.FLAG_INIT = true;

                // inicializace se zadaným textovým souborem -f init.conf
                if (params.containsKey("f")) {

                    if (params.get("f").size() == 1) {
                        actionInitialize(params.get("f").get(0));
                    } else {
                        ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -f. (Použití: --init -f settings.conf)");
                    }

                    return;
                }

                // interaktivní inicializace nastavení
                if (params.containsKey("interactive")) {
                    actionInitialize();
                    return;
                }

                // inicializace s výchozím souborem ./settings.conf
                if (FLAG_VERBOSE) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá pokus o inicializaci s výchozím souborem settings.conf.");
                }
                actionInitialize("./settings.conf");
                return;
            } // init

            /* načtení již existujícího nastavení */
            Settings.getInstance().load();
            if (!Settings.getInstance().isValid()) {
                return;
            }

            // vývojový režim - propagace do globálního nastavení
            Settings.getInstance().setDevelMode(FLAG_DEVEL);

            /* jednotlivé rutiny */

            // CSV export tabulky všech žáků
            if (params.containsKey("export")) {
                if (params.containsKey("o")) {

                    if (params.get("o").size() == 1) {
                        Export.exportStudentCSVdata(params.get("o").get(0));
                    } else {
                        ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -o. (Použití: --export -o seznam.csv)");
                    }
                } else {
                    // výstup exportu do stdout
                    Export.exportStudentCSVdata(null);
                }

                return;
            } // export

            //  kontrola současného stavu
            if (params.containsKey("status")) {
                Sync.actionCheckSync();
                return;
            }

            // synchronizace
            if (params.containsKey("sync")) {
                Sync.actionSync();
                return;
            } // sync

            // bezpečnostní audit?
            if (params.containsKey("audit")) {
                // TODO kontrola assert security group list, OU, proxy(?)
                return;
            } // audit

            // kontrola nastavení přístupu ke službám
            if (params.containsKey("check")) {
                actionCheck();
                return;
            } // check

            // identifikace AD loginu
            if (params.containsKey("id")) {
                if (params.get("id").size() == 1) {
                    actionIdentify(params.get("id").get(0));
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -id. (Použití: -id novak.jan)");
                }
            } // id

            // reset hesla
            if (params.containsKey("reset")) {
                if (params.get("reset").size() == 1) {
                    actionResetPassword(params.get("reset").get(0));
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -reset. (Použití: -reset novak.jan)");
                }
            }

            // nastavení hesla
            if (params.containsKey("set")) {
                if (params.get("set").size() == 2) {
                    actionSetPassword(params.get("set").get(0), params.get("set").get(1));
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -set. (Použití: -set novak.jan Nove.Heslo)");
                }
            }

            // rychlá sestava
            if (params.containsKey("report")) {
                if (params.get("report").size() == 1) {
                    Export.genericReport(params.get("report").get(0), false);
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -report. (Použití: -report 1.A)");
                }
            }

            // rychlá sestava s resetem hesel
            if (params.containsKey("resetreport")) {
                if (params.get("resetreport").size() == 1) {
                    Export.genericReport(params.get("resetreport").get(0), true);
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -resetreport. (Použití: -resetreport 1.A)");
                }
            }

            // TODO vývojový test
            if (params.containsKey("test") && FLAG_DEVEL) {
                System.out.println("====== [ TEST ] ======");
                //Test.test_16();
                System.out.println("====== [ /TEST ] ======");
                return;
            } // test
        }
    }


    /**
     * Spuštění programu bez parametrů.
     *
     */
    public static void actionPrintRun() {
        Version appVersion = Version.getInstance();
        System.out.println(appVersion.getInfo());
        System.out.println("Spusťte program s parametrem --help pro nápovědu.\n");
    }

    /**
     * Vypíše nápovědu pro používání programu.
     *
     */
    public static void actionPrintHelp() {
        System.out.print(HelpManager.printHelp());
    }

    /**
     * Inicializace nastavení ze souboru.
     *
     * @param filename soubor s úvodním nastavením
     */
    public static void actionInitialize(String filename) {

        // načtení prvotní nebo přenesené konfigurace
        Settings.getInstance().load(filename);
        finalizeInit();
    }


    /**
     * Interaktivní režim inicializace nastavení.
     */
    public static void actionInitialize() {
        // interaktivní dotazování
        Settings.getInstance().interactivePrompt();
        finalizeInit();
    }

    /**
     * Finalizace inicializace nastavení.
     */
    private static void finalizeInit() {
        // ověření platnosti nastavení
        if (!Settings.getInstance().isValid()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné vytvořit platná nastavení.");
            return;
        }

        // uložení do datového souboru
        Settings.getInstance().save();

        // otestování validity uložených nastavení
        if (Settings.getInstance().isValid()) {

            // pokud se používá SSL, je třeba inicializovat úložiště klíčů
            if (Settings.getInstance().useSSL()) {
                if (KeyStoreManager.initialize()) {
                    ReportManager.log(EBakaLogType.LOG_OK, "Výchozí úložiště certifikátů bylo vytvořeno.");
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné vytvořit výchozí úložiště certifikátů.");
                }
            }

            if (PASSPHRASE.length() > 0) {
                ReportManager.log(EBakaLogType.LOG_OK, "Šifrovaný datový soubor s nastavením byl úspěšně vytvořen.");
            } else {
                ReportManager.log(EBakaLogType.LOG_OK, "Datový soubor s nastavením byl úspěšně vytvořen.");
            }

        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Vytvoření konfigurace se nezdařilo.");
        }
    }

    /**
     * Provedení kontroly uložených nastavení a ověření přístupových údajů
     * ke službám Active Directory, SQL Serveru a SMTP.
     */
    public static void actionCheck() {

        // 0) načtení nastavení
        Settings.getInstance().load();

        // 1) konfigurační soubor
        if (!checkConfig()) {
            //return;
        }

        // 2) spojení s Active Directory
        if (!checkLDAP()) {
            //return;
        }

        // 3) spojení s databázovým serverem
        if (!checkSQL()) {
            //return;
        }

        // 4) spojení s poštovním serverem
        checkSMTP();
    }

    /**
     * Kontrola konfiguračního souboru.
     *
     * @return výsledek kontroly konfiguračního souboru
     */
    private static Boolean checkConfig() {
        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování validity konfiguračních dat");

        if (!Settings.getInstance().isValid()) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);

            if (FLAG_VERBOSE) {
                ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Načtená nastavení nejsou platná.");
            }

            return false;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_OK);

            if (FLAG_VERBOSE) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Načtená nastavení nejsou platná.");
            }

            return true;
        }
    }

    /**
     * Kontrola přístupu k Active Directory
     *
     * @return výsledek spojení s Active Directory
     */
    private static Boolean checkLDAP() {
        ReportManager.logWait(EBakaLogType.LOG_TEST,"Testování spojení na řadič Active Directory");

        if (BakaADAuthenticator.getInstance().isAuthenticated()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            return true;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            return false;
        }
    }

    /**
     * Kontrola přístupu k SQL Serveru
     *
     * @return výsledek připojení k SQL Serveru
     */
    private static Boolean checkSQL() {
        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SQL Server");

        if (BakaSQL.getInstance().testSQL()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            return true;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            return false;
        }
    }

    /**
     * Kontrola přístupu k SMTP serveru
     *
     * @return výsledek připojení k SMTP serveru
     */
    private static Boolean checkSMTP() {
        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SMTP server");

        if (BakaMailer.getInstance().testSMTP()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            return true;
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            return false;
        }
    }

    /**
     * Identifikace účtu žáka.
     *
     * @param login UPN účtu
     *
     */
    public static void actionIdentify(String login) {
        Export.identify(login);
    }

    /**
     * TODO Reset hesla jednoho účtu
     *
     * @param login
     */
    public static void actionResetPassword(String login) {

        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o reset hesla účtu " + login);

        if (Manipulation.resetPassword(login)) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            // TODO + hlášení
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }


        // TODO - SAM/UPN/mail? --> UPN
        // 1) objekt musí být aktivní žák (existuje v AD a v Bakalářích), TODO možná i personál/pouze učitelé?
        // 2) provede se nastavení hesla do původní podoby (Pr.Jm.##yy)
        // 3) nastaví se flag nutnosti změny hesla při dalším přihlášení(? - spíš ne)
        // 4) reportovat - sestava třídnímu
        // 5) dost možná vyrobit akci + hint v --help
    }

    /**
     * Okamžité nastavení hesla účtu.
     *
     * @param login UPN
     * @param password nové heslo k přímému nastavení
     */
    public static void actionSetPassword(String login, String password) {
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o okamžité nastavení hesla účtu " + login);

        if (Manipulation.setPassword(login, password)) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
            // TODO + odeslání hlášení
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }
    }

    /**
     * Provedení kompletního bezpečnostního auditu.
     */
    public static void actionAudit() {
        // TODO - má to vůbec smysl?
    }

}