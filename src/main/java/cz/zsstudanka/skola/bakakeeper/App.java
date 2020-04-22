package cz.zsstudanka.skola.bakakeeper;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.routines.Structure;
import cz.zsstudanka.skola.bakakeeper.routines.Test;
import cz.zsstudanka.skola.bakakeeper.components.HelpManager;
import cz.zsstudanka.skola.bakakeeper.components.KeyStoreManager;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import java.util.*;

/**
 * Nástroj pro synchronizaci záznamů z Bakalářů a informacemi v Active Directory.
 *
 * @author Jan Hladěna
 */
public class App {

    // TODO vývojářský režim - nebude se zapisovat do ostrých dat
    /** příznak vývoajářského režimu - neprobíhá zápis do ostrých dat */
    public static Boolean FLAG_DEVEL = false;

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

            // argumenty s jednou pomlčkou - mohou vázat sugargumenty
            if (a.charAt(0) == '-') {

                if (a.length() < 2) {
                    System.err.println("Chyba v argumentu: " + a);
                    return;
                }

                options = new ArrayList<>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Neplatné argumenty programu.");
                return;
            }
        }

        // spuštění bez parametrů
        if (params.size() == 0) {
            actionPrintRun();
        } else {

            // společná nastavení - podrobný režim
            if (params.containsKey("verbose")) {

                FLAG_VERBOSE = true;
                Settings.getInstance().verbosity(FLAG_VERBOSE);

                System.out.println("[ INFO ] Aktivován výstup podrobných informací.");
            }

            // společná nastavení - ladící režim
            if (params.containsKey("debug")) {

                FLAG_VERBOSE = true;
                Settings.getInstance().verbosity(FLAG_VERBOSE);

                FLAG_DEBUG = true;
                Settings.getInstance().debug(FLAG_DEBUG);

                System.out.println("[ DEBUG ] Aktivován výstup ladících informací.");
            }

            if (params.containsKey("log")) {
                if (params.get("log").size() == 1) {
                    ReportManager.getInstance().setLogfile(params.get("log").get(0));
                } else {
                    System.err.println("[ CHYBA ] Chybně zadaný argument -log. (Použití: -log protokol.log)");
                }
            }

            // společná nastavení - heslo ke konfiguraci
            if (params.containsKey("passphrase")) {
                if (params.get("passphrase").size() == 1) {
                    PASSPHRASE = params.get("passphrase").get(0);

                    Settings.getInstance().setPassphrase(PASSPHRASE);

                } else {
                    System.err.println("Chybně zadaný parametr -passphrase.");
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
                System.out.print(Version.getInstance().getInfo());
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
                        System.err.println("[ CHYBA ] Chybně zadaný argument -f. (Použití: --init -f settings.conf)");
                    }

                    return;
                }

                // interaktivní inicializace nastavení
                if (params.containsKey("interactive")) {
                    actionInitialize();
                    return;
                }

                // inicialziace s výchozím souborem ./settings.conf
                if (FLAG_VERBOSE) {
                    System.err.println("[ INFO ] Probíhá pokus o inicializaci s výchozím souborem settings.conf.");
                }
                actionInitialize("./settings.conf");
                return;
            } // init

            // // //
            Settings.getInstance().load();
            if (!Settings.getInstance().isValid()) {
                return;
            }

            // vývojářský režim
            Settings.getInstance().setDevelMode(FLAG_DEVEL);
            // // //

            // CSV export tabulky žáků
            if (params.containsKey("export")) {
                if (params.containsKey("o")) {

                    if (params.get("o").size() == 1) {
                        Export.exportStudentCSVdata(params.get("o").get(0));
                    } else {
                        System.err.println("[ CHYBA ] Chybně zadaný argument -o. (Použití: --export -o seznam.csv)");
                    }
                } else {
                    // výstup exportu do stdout
                    Export.exportStudentCSVdata(null);
                }

                return;
            } // export

            // synchronizace
            if (params.containsKey("sync")) {
                // TODO sync
                return;
            } // sync

            // bezpečnostní audit
            if (params.containsKey("audit")) {
                // TODO kontrola assert security group list, OU, proxy
                return;
            } // audit

            // kontrola nastavení
            if (params.containsKey("check")) {
                actionCheck();
                return;
            } // check

            // identifikace AD loginu
            if (params.containsKey("id")) {
                if (params.get("id").size() == 1) {
                    actionIdentify(params.get("id").get(0));
                } else {
                    System.err.println("[ CHYBA ] Chybně zadaný argument -id. (Použití: -id novak.jan)");
                }
            } // id

            // reset hesla
            if (params.containsKey("reset")) {
                if (params.get("reset").size() == 1) {
                    actionResetPassword(params.get("reset").get(0));
                } else {
                    System.err.println("[ CHYBA ] Chybně zadaný argument -reset. (Použití: -reset novak.jan)");
                }
            }


            // vývojový test
            if (params.containsKey("test")) {
                Test.test_01();
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

        // ověření platnosti
        if (!Settings.getInstance().isValid()) {
            if (FLAG_VERBOSE) {
                System.err.println("[ CHYBA ] Nebylo možné vytvořit platná nastavení.");
            }
            return;
        }

        // uložení do datového souboru
        Settings.getInstance().save();

        // otestování validity uložených nastavení
        if (Settings.getInstance().isValid()) {

            // pokud se používá SSL, je třeba inicializovat úložiště klíčů
            if (Settings.getInstance().useSSL()) {
                if (KeyStoreManager.initialize()) {
                    System.out.println("[ OK ] Výchozí úložiště certifikátů bylo vytvořeno.");
                } else {
                    System.err.println("[ CHYBA ] Nebylo možné vytvořit výchozí úložiště certifikátů.");
                }
            }

            if (PASSPHRASE.length() > 0) {
                System.out.println("[ OK ] Šifrovaný datový soubor s nastavením byl úspěšně vytvořen.");
            } else {
                System.out.println("[ OK ] Datový soubor s nastavením byl úspěšně vytvořen.");
            }

        } else {
            System.err.println("[ CHYBA ] Vytvoření konfigurace se nezdařilo.");
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
            return;
        }

        // 2) spojení s Active Directory
        if (!checkLDAP()) {
            return;
        }

        // 3) spojení s databázovým serverem
        if (!checkSQL()) {
            return;
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
        System.out.print("Testování validity konfiguračních dat... ");

        if (!Settings.getInstance().isValid()) {
            System.out.println("\t[ CHYBA ]");

            if (FLAG_VERBOSE) {
                System.err.println("[ CHYBA ] Načtená nastavení nejsou platná.");
            }

            return false;
        } else {
            System.out.println("\t[ OK ]");

            if (FLAG_VERBOSE) {
                System.err.println("[ INFO ] Načtená nastavení jsou platná.");
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
        System.out.print("Testování spojení na řadič Active Directory... ");

        if (BakaADAuthenticator.getInstance().isAuthenticated()) {
            System.out.println("\t[ OK ]");
            return true;
        } else {
            System.out.println("\t[ CHYBA ]");
            return false;
        }
    }

    /**
     * Kontrola přístupu k SQL Serveru
     *
     * @return výsledek připojení k SQL Serveru
     */
    private static Boolean checkSQL() {
        System.out.print("Testování spojení na SQL Server... ");

        if (BakaSQL.getInstance().testSQL()) {
            System.out.println("\t\t[ OK ]");
            return true;
        } else {
            System.out.println("\t\t[ CHYBA ]");
            return false;
        }
    }

    /**
     * Kontrola přístupu k SMTP serveru
     *
     * @return výsledek připojení k SMTP serveru
     */
    private static Boolean checkSMTP() {
        System.out.print("Testování spojení na SMTP server... ");

        if (BakaMailer.getInstance().testSMTP()) {
            System.out.println("\t\t[ OK ]");
            return true;
        } else {
            System.out.println("\t\t[ CHYBA ]");
            return false;
        }
    }


    /**
     * Kontrola základní hierarchické struktury v Active Directory.
     * V případě chyb bude proveden pokus o opravu.
     *
     * @param repair pokusit se provést automatickou opravu/rekonstrukci struktury
     * @return výsledek kontroly struktury
     *
     * @deprecated
     */
    private static Boolean checkAndRepairStructure(Boolean repair) {
        return Structure.checkAndRepairADStructure(repair);
    }

    /**
     * Kontrola a oprava struktury.
     *
     * @return stav kontroly a opravy struktury
     */
    private Boolean repairStructure() {
        return checkAndRepairStructure(true);
    }

    /**
     * Identifikace účtu v obou systémech.
     *
     */
    public static void actionIdentify(String login) {
        // TODO
        // vyhledat v AD, určit typ, pokud žák - údaje z Bakalářů (+existenci kontaktu na ZZ)
    }

    /**
     * Reset hesla žáka
     *
     * @param login
     */
    public static void actionResetPassword(String login) {
        // TODO
        // 1) objekt musí být aktivní žák (existuje v AD a v Bakalářích)
        // 2) provede se nastavení hesla do původní podoby (PrJm.1234)
        // 3) nastaví se flag nutnosti změny hesla při dalším přihlášení
        // 4) dost možná vyrobit akci + hint v --help
    }

    /**
     * Kontrola stavu synchronizace.
     */
    public static void actionCheckSync() {
        // TODO
    }

    /**
     * Kontrola a provedení synchronizace.
     *
     * @param forceSync provádět mimo kontrolu i synchronizaci
     */
    public static void actionSync(boolean forceSync) {
        // TODO

        /*
        *  1) od devítek - výběr z baka po třídách
        *  2) identifikace žáků bez školního mailu
        *  3) (POKUD NE) vygenerování mailu podle pravidel jmen + kontrola do výšky od zam/devítek (interní seznam)
        *  4) (OPRAVA) uložení školního mailu do Baka
        *  5) identifikace LDAP podle jména na základě mailu (doplní se objekty)
        *  6) (POKUD NE) vytvoření nového účtu žáka v souladu s parametry
        *  7) (POKUD ANO) kontrola parametrů účtu
        *  8) (POKUD ANO) kontrola náležitosti do OU a skupin
        *  9) (POKUD CHYBA) oprava náležitosti do OU a skupin
        * 10) Zpětná kontrola z LDAPu do Baka -> neexistující účty se přesunou do ukončených
        *     a nastaví se jako uzamčené
        *
        *  2020-04-02 Povinně nejvyšší direct objekt - Skupina-Zaci pro O365.
        * */
    }

    /**
     * Provedení kompletního bezpečnostního auditu.
     */
    public static void actionAudit() {
        // TODO - má to vůbec smysl?
    }

}
