package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaADAuthenticator;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaSQL;
import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.GuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.LDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaFacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaGuardianRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaLDAPUserRepository;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaStudentRepository;

/**
 * Centrální factory pro sestavení DI grafu aplikace.
 * Vytvoří všechny repozitáře, služby a orchestrátor z jednoho místa.
 *
 * <p>Produkční kód volá {@link #ServiceFactory(AppConfig)}, který vytvoří
 * konektory přes singletony {@link BakaADAuthenticator} a {@link BakaSQL}.
 * Testy mohou použít {@link #ServiceFactory(AppConfig, LDAPConnector, SQLConnector)}
 * pro injektování mock konektorů.</p>
 *
 * @author Jan Hladěna
 */
public class ServiceFactory {

    private final AppConfig config;

    // repozitáře
    private final StudentRepository studentRepo;
    private final FacultyRepository facultyRepo;
    private final LDAPUserRepository ldapUserRepo;
    private final GuardianRepository guardianRepo;

    // služby
    private final StructureService structureService;
    private final PasswordService passwordService;
    private final AccountService accountService;
    private final PairingService pairingService;
    private final StudentService studentService;
    private final GuardianService guardianService;
    private final FacultyService facultyService;
    private final RuleService ruleService;
    private final IdentifyService identifyService;

    // orchestrátor
    private final SyncOrchestrator orchestrator;

    /**
     * Produkční konstruktor – vytvoří konektory z globálních singletonů.
     *
     * @param config konfigurace aplikace
     */
    public ServiceFactory(AppConfig config) {
        this(config, BakaADAuthenticator.getInstance(), BakaSQL.getInstance());
    }

    /**
     * Testovací konstruktor – přijme hotové konektory.
     *
     * @param config konfigurace aplikace
     * @param ldap   LDAP konektor
     * @param sql    SQL konektor
     */
    public ServiceFactory(AppConfig config, LDAPConnector ldap, SQLConnector sql) {
        this.config = config;

        // repozitáře
        this.studentRepo = new BakaStudentRepository(sql);
        this.facultyRepo = new BakaFacultyRepository(sql);
        this.ldapUserRepo = new BakaLDAPUserRepository(ldap);
        this.guardianRepo = new BakaGuardianRepository(ldap);

        // služby
        this.structureService = new StructureServiceImpl(config, ldap);
        this.passwordService = new PasswordServiceImpl(ldapUserRepo);
        this.accountService = new AccountServiceImpl(ldapUserRepo);
        this.pairingService = new PairingServiceImpl(ldapUserRepo);
        this.studentService = new StudentServiceImpl(
                config, studentRepo, ldapUserRepo, passwordService, pairingService);
        this.guardianService = new GuardianServiceImpl(config, guardianRepo, ldapUserRepo);
        this.facultyService = new FacultyServiceImpl(config, ldapUserRepo);
        this.ruleService = new RuleServiceImpl(ldapUserRepo);
        this.identifyService = new IdentifyServiceImpl(config, studentRepo, facultyRepo, ldapUserRepo);

        // orchestrátor
        this.orchestrator = new SyncOrchestrator(
                config, studentRepo, ldapUserRepo, facultyRepo, guardianRepo,
                structureService, studentService, facultyService, guardianService, ruleService);
    }

    public AppConfig getConfig() { return config; }

    public StudentRepository getStudentRepo() { return studentRepo; }
    public FacultyRepository getFacultyRepo() { return facultyRepo; }
    public LDAPUserRepository getLdapUserRepo() { return ldapUserRepo; }
    public GuardianRepository getGuardianRepo() { return guardianRepo; }

    public StructureService getStructureService() { return structureService; }
    public PasswordService getPasswordService() { return passwordService; }
    public AccountService getAccountService() { return accountService; }
    public PairingService getPairingService() { return pairingService; }
    public StudentService getStudentService() { return studentService; }
    public GuardianService getGuardianService() { return guardianService; }
    public FacultyService getFacultyService() { return facultyService; }
    public RuleService getRuleService() { return ruleService; }
    public IdentifyService getIdentifyService() { return identifyService; }

    public SyncOrchestrator getOrchestrator() { return orchestrator; }
}
