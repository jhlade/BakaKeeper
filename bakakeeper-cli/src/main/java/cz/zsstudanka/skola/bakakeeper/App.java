package cz.zsstudanka.skola.bakakeeper;

import cz.zsstudanka.skola.bakakeeper.components.HelpManager;
import cz.zsstudanka.skola.bakakeeper.components.KeyStoreManager;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.collections.BakaInternalUserHistory;
import cz.zsstudanka.skola.bakakeeper.model.entities.BakaInternalUser;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaFacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaGuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaLDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaStudentRepository;
import cz.zsstudanka.skola.bakakeeper.routines.Export;
import cz.zsstudanka.skola.bakakeeper.service.*;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.settings.Version;

import java.util.*;

/**
 * Nástroj pro synchronizaci záznamů z Bakalářů a informacemi v Active Directory.
 *
 * @author Jan Hladěna
 */
public class App {

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
            if (a.length() >= 3 && a.charAt(0) == '-' && a.charAt(1) == '-') {
                options = new ArrayList<>();
                params.put(a.substring(2), options);
                continue;
            }

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
        if (params.isEmpty()) {
            actionPrintRun();
            return;
        }

        // --- Globální příznaky ---

        if (params.containsKey("develmode")) {
            RuntimeContext.FLAG_DEVEL = true;
            ReportManager.log(EBakaLogType.LOG_DEVEL, "Je aktivní vývojový režim. Nebude se zapisovat do ostrých dat evidence.");
        }

        if (params.containsKey("dryrun")) {
            RuntimeContext.FLAG_DRYRUN = true;
        }

        if (params.containsKey("verbose")) {
            RuntimeContext.FLAG_VERBOSE = true;
            Settings.getInstance().verbosity(RuntimeContext.FLAG_VERBOSE);
            ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Aktivován výstup podrobných informací.");
        }

        if (params.containsKey("debug")) {
            RuntimeContext.FLAG_VERBOSE = true;
            Settings.getInstance().verbosity(RuntimeContext.FLAG_VERBOSE);
            RuntimeContext.FLAG_DEBUG = true;
            Settings.getInstance().debug(RuntimeContext.FLAG_DEBUG);
            ReportManager.log(EBakaLogType.LOG_DEBUG, "Aktivován výstup ladících informací.");
        }

        if (params.containsKey("log")) {
            if (params.get("log").size() == 1) {
                ReportManager.getInstance().setLogfile(params.get("log").get(0));
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -log. (Použití: -log protokol.log)");
            }
        }

        if (params.containsKey("passphrase")) {
            if (params.get("passphrase").size() == 1) {
                RuntimeContext.PASSPHRASE = params.get("passphrase").get(0);
                Settings.getInstance().setPassphrase(RuntimeContext.PASSPHRASE);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný parametr -passphrase.");
                return;
            }
        }

        // --- Příkazy nevyžadující načtení konfigurace ---

        if (params.containsKey("help")) {
            actionPrintHelp();
            return;
        }

        if (params.containsKey("version")) {
            System.out.print(Version.getInstance().getInfo(true));
            return;
        }

        // --- Inicializace konfigurace ---
        if (params.containsKey("init")) {
            RuntimeContext.FLAG_INIT = true;

            if (params.containsKey("f")) {
                if (params.get("f").size() == 1) {
                    actionInitialize(params.get("f").get(0));
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -f. (Použití: --init -f settings.conf)");
                }
                return;
            }

            if (params.containsKey("interactive")) {
                actionInitialize();
                return;
            }

            if (RuntimeContext.FLAG_VERBOSE) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Probíhá pokus o inicializaci s výchozím souborem settings.conf.");
            }
            actionInitialize("./settings.conf");
            return;
        }

        // --- Načtení konfigurace ---
        Settings.getInstance().load();
        if (!Settings.getInstance().isValid()) {
            return;
        }
        Settings.getInstance().setDevelMode(RuntimeContext.FLAG_DEVEL);

        // --- Příkazy vyžadující konfiguraci ---

        // kontrola přístupu ke službám
        if (params.containsKey("check")) {
            actionCheck();
            return;
        }

        // CSV export
        if (params.containsKey("export")) {
            if (params.containsKey("o") && params.get("o").size() == 1) {
                Export.exportStudentCSVdata(params.get("o").get(0));
            } else if (!params.containsKey("o")) {
                Export.exportStudentCSVdata(null);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -o. (Použití: --export -o seznam.csv)");
            }
            return;
        }

        // rychlá sestava
        if (params.containsKey("report")) {
            if (params.get("report").size() == 1) {
                Export.genericReport(params.get("report").get(0), false);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -report. (Použití: -report 1.A)");
            }
            return;
        }

        // rychlá sestava s resetem hesel
        if (params.containsKey("resetreport")) {
            if (params.get("resetreport").size() == 1) {
                Export.genericReport(params.get("resetreport").get(0), true);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -resetreport. (Použití: -resetreport 1.A)");
            }
            return;
        }

        // identifikace AD loginu
        if (params.containsKey("id")) {
            if (params.get("id").size() == 1) {
                Export.identify(params.get("id").get(0));
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -id. (Použití: -id novak.jan)");
            }
            return;
        }

        // interní uživatelé
        if (params.containsKey("internaldb")) {
            handleInternalDb(params.get("internaldb"));
            return;
        }

        // --- Příkazy využívající novou service vrstvu (DI) ---
        AppConfig config = Settings.getInstance();
        SyncProgressListener listener = new CliProgressListener(RuntimeContext.FLAG_VERBOSE);

        // sestavení DI grafu
        BakaADAuthenticator ldapConnector = BakaADAuthenticator.getInstance();
        BakaSQL sqlConnector = BakaSQL.getInstance();

        StudentRepository studentRepo = new BakaStudentRepository(sqlConnector);
        LDAPUserRepository ldapUserRepo = new BakaLDAPUserRepository(ldapConnector);
        GuardianRepository guardianRepo = new BakaGuardianRepository(ldapConnector);
        FacultyRepository facultyRepo = new BakaFacultyRepository(sqlConnector);

        PasswordService passwordService = new PasswordServiceImpl(ldapUserRepo);
        PairingService pairingService = new PairingServiceImpl(ldapUserRepo);
        StudentService studentService = new StudentServiceImpl(
                config, studentRepo, ldapUserRepo, passwordService, pairingService);
        GuardianService guardianService = new GuardianServiceImpl(config, guardianRepo, ldapUserRepo);
        FacultyService facultyService = new FacultyServiceImpl(config, ldapUserRepo);
        RuleService ruleService = new RuleServiceImpl(ldapUserRepo);

        SyncOrchestrator orchestrator = new SyncOrchestrator(
                config, studentRepo, ldapUserRepo, facultyRepo, guardianRepo,
                studentService, facultyService, guardianService, ruleService);

        // kontrola současného stavu
        if (params.containsKey("status")) {
            List<SyncResult> results = orchestrator.runFullSync(false, listener);
            printSummary(results);
            return;
        }

        // synchronizace
        if (params.containsKey("sync")) {
            boolean repair = !RuntimeContext.FLAG_DRYRUN;
            List<SyncResult> results = orchestrator.runFullSync(repair, listener);
            printSummary(results);
            return;
        }

        // reset hesla
        if (params.containsKey("reset")) {
            if (params.get("reset").size() == 1) {
                actionResetPassword(params.get("reset").get(0), config, ldapUserRepo, passwordService, studentRepo);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -reset. (Použití: -reset novak.jan)");
            }
            return;
        }

        // nastavení hesla
        if (params.containsKey("set")) {
            if (params.get("set").size() == 2) {
                actionSetPassword(params.get("set").get(0), params.get("set").get(1),
                        config, ldapUserRepo, passwordService);
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -set. (Použití: -set novak.jan Nove.Heslo)");
            }
            return;
        }

        ReportManager.log(EBakaLogType.LOG_ERR, "Neznámý příkaz. Spusťte s --help pro nápovědu.");
    }

    // ===========================
    // Jednoduché příkazy
    // ===========================

    /** Spuštění bez parametrů. */
    private static void actionPrintRun() {
        Version appVersion = Version.getInstance();
        System.out.println(appVersion.getInfo());
        System.out.println("Spusťte program s parametrem --help pro nápovědu.\n");
    }

    /** Nápověda. */
    private static void actionPrintHelp() {
        System.out.print(HelpManager.printHelp());
    }

    // ===========================
    // Inicializace konfigurace
    // ===========================

    /** Inicializace ze souboru. */
    private static void actionInitialize(String filename) {
        Settings.getInstance().load(filename);
        finalizeInit();
    }

    /** Interaktivní inicializace. */
    private static void actionInitialize() {
        Settings.getInstance().interactivePrompt();
        finalizeInit();
    }

    /** Dokončení inicializace – validace, uložení, SSL. */
    private static void finalizeInit() {
        if (!Settings.getInstance().isValid()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné vytvořit platná nastavení.");
            return;
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
        }
    }

    // ===========================
    // Kontrola konektivity
    // ===========================

    /** Kontrola přístupu ke všem službám. */
    private static void actionCheck() {
        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování validity konfiguračních dat");
        if (!Settings.getInstance().isValid()) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na řadič Active Directory");
        if (BakaADAuthenticator.getInstance().isAuthenticated()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SQL Server");
        if (BakaSQL.getInstance().testSQL()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }

        ReportManager.logWait(EBakaLogType.LOG_TEST, "Testování spojení na SMTP server");
        if (BakaMailer.getInstance().testSMTP()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }
    }

    // ===========================
    // Operace s hesly (DI)
    // ===========================

    /** Reset hesla žáka podle UPN. */
    private static void actionResetPassword(String login, AppConfig config,
                                             LDAPUserRepository ldapUserRepo,
                                             PasswordService passwordService,
                                             StudentRepository studentRepo) {
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o reset hesla účtu " + login);

        // vyhledat žáka v LDAP
        String upn = login.contains("@") ? login : login + "@" + config.getMailDomain();
        StudentRecord student = ldapUserRepo.findByUPN(config.getLdapBaseStudents(), upn);

        if (student == null || student.getDn() == null) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, "Účet " + upn + " nenalezen v AD.");
            return;
        }

        // získat classId z SQL evidence
        Integer classId = 0;
        StudentRecord sqlStudent = studentRepo.findByInternalId(student.getInternalId());
        if (sqlStudent != null && sqlStudent.getClassNumber() != null) {
            try { classId = Integer.parseInt(sqlStudent.getClassNumber()); } catch (NumberFormatException ignored) {}
        }

        SyncResult result = passwordService.resetStudentPassword(
                student.getDn(), student.getSurname(), student.getGivenName(),
                student.getClassYear(), classId);

        if (result.isSuccess()) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, result.getDescription());
        }
    }

    /** Okamžité nastavení hesla účtu. */
    private static void actionSetPassword(String login, String password, AppConfig config,
                                           LDAPUserRepository ldapUserRepo,
                                           PasswordService passwordService) {
        ReportManager.logWait(EBakaLogType.LOG_STDOUT, "Probíhá pokus o okamžité nastavení hesla účtu " + login);

        String upn = login.contains("@") ? login : login + "@" + config.getMailDomain();
        StudentRecord student = ldapUserRepo.findByUPN(config.getLdapBaseStudents(), upn);

        if (student == null || student.getDn() == null) {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
            ReportManager.log(EBakaLogType.LOG_ERR, "Účet " + upn + " nenalezen v AD.");
            return;
        }

        if (passwordService.setPassword(student.getDn(), password, false)) {
            ReportManager.logResult(EBakaLogType.LOG_OK);
        } else {
            ReportManager.logResult(EBakaLogType.LOG_ERR);
        }
    }

    // ===========================
    // Interní uživatelé
    // ===========================

    /** Správa interních uživatelů. */
    private static void handleInternalDb(List<String> args) {
        if (args.size() == 3 && "restore".equals(args.get(0))) {
            BakaInternalUserHistory.getInstance().restore(args.get(1), Integer.parseInt(args.get(2)));
        } else if (args.size() == 2) {
            if ("list".equals(args.get(0))) {
                Map<Date, BakaInternalUser> data = BakaInternalUserHistory.getInstance().list(args.get(1));
                if (data != null) {
                    BakaInternalUser current = new BakaInternalUser(args.get(1));
                    int i = 0;
                    for (Map.Entry<Date, BakaInternalUser> entry : data.entrySet()) {
                        String mark = (entry.getValue().compareTo(current) == 0) ? "*" : "";
                        ReportManager.log("[ " + i + " ]" + mark + "\t[" + entry.getKey() + "] : " + entry.getValue().getLogin());
                        i++;
                    }
                } else {
                    ReportManager.log("Pro zadaného uživatele neexistují žádné zálohy.");
                }
            } else if ("backup".equals(args.get(0))) {
                BakaInternalUserHistory.getInstance().backup(args.get(1));
            }
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR, "Chybně zadaný argument -internaldb. (Použití: -internaldb (list|backup|restore) login [index])");
        }
    }

    // ===========================
    // Souhrn výsledků
    // ===========================

    /** Vypíše souhrn synchronizace. */
    private static void printSummary(List<SyncResult> results) {
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
}
