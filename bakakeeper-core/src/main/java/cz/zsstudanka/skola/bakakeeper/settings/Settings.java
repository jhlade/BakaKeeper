package cz.zsstudanka.skola.bakakeeper.settings;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.config.EncryptedConfigLoader;
import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.config.YamlAppConfig;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Singleton přístup ke konfiguraci – bridge pro stávající kód.
 * Interně deleguje na {@link YamlAppConfig} (YAML formát).
 *
 * Tato třída bude odstraněna v Fázi 6 refaktoru, až bude veškerý
 * kód přepnut na přímé použití {@link AppConfig}.
 *
 * @author Jan Hladěna
 */
public class Settings implements AppConfig {

    /** singleton instance */
    private static Settings instance = null;

    /** interní YAML konfigurace */
    private YamlAppConfig config;

    /** šifrovací heslo */
    private char[] PASSPHRASE = null;

    /** runtime příznaky */
    private boolean beVerbose = false;
    private boolean debugMode = false;
    private boolean develMode = false;

    /** výchozí soubory */
    private static final String DEFAULT_DATA_FILE = "./settings.dat";
    private static final String DEFAULT_YAML_FILE = "./settings.yml";

    /** název šablony v resources */
    private static final String TEMPLATE_RESOURCE = "/settings.yml";

    /** výchozí JKS soubor */
    public final String DEFAULT_JKS_FILE = "./ssl.jks";

    /**
     * Singleton přístup.
     *
     * @return instance nastavení
     */
    public static Settings getInstance() {
        if (instance == null) {
            instance = new Settings();
        }
        return instance;
    }

    /**
     * Načtení konfigurace z výchozího souboru (.dat nebo .yml).
     */
    public void load() {
        File datFile = new File(DEFAULT_DATA_FILE);
        if (datFile.isFile()) {
            if (beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí datový soubor nalezen.");
            }
            load(DEFAULT_DATA_FILE);
            return;
        }

        File ymlFile = new File(DEFAULT_YAML_FILE);
        if (ymlFile.isFile()) {
            if (beVerbose) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Výchozí YAML konfigurační soubor nalezen.");
            }
            load(DEFAULT_YAML_FILE);
            return;
        }

        // žádný config – zkopírovat šablonu z resources
        if (copyTemplate()) {
            ReportManager.log(EBakaLogType.LOG_OK,
                    "Šablona konfigurace byla zkopírována do " + DEFAULT_YAML_FILE + ". Upravte ji a spusťte --init.");
        } else {
            ReportManager.log(EBakaLogType.LOG_ERR,
                    "Nebyl nalezen žádný konfigurační soubor. Proveďte inicializaci s parametrem --init.");
        }
    }

    /**
     * Zkopíruje šablonu konfigurace z resources do pracovního adresáře,
     * pokud dosud neexistuje.
     *
     * @return true pokud byl soubor úspěšně zkopírován nebo již existuje
     */
    public boolean copyTemplate() {
        File target = new File(DEFAULT_YAML_FILE);
        if (target.isFile()) return true;

        try (InputStream is = getClass().getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (is == null) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Šablona konfigurace nebyla nalezena v resources.");
                return false;
            }
            try (FileOutputStream fos = new FileOutputStream(target)) {
                is.transferTo(fos);
            }
            return true;
        } catch (IOException e) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Nebylo možné zkopírovat šablonu konfigurace: " + e.getMessage());
            return false;
        }
    }

    /**
     * Načtení konfigurace ze zadaného souboru.
     *
     * @param filename cesta k souboru (.dat nebo .yml)
     */
    public void load(String filename) {
        File file = new File(filename);
        if (!file.isFile()) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Konfigurační soubor nebyl nalezen: " + filename);
            return;
        }

        try {
            if (filename.endsWith(".dat")) {
                this.config = EncryptedConfigLoader.load(file, PASSPHRASE);
            } else {
                this.config = EncryptedConfigLoader.loadPlain(file);
            }
            syncRuntimeFlags();
        } catch (Exception e) {
            if (debugMode) {
                ReportManager.exceptionMessage(e);
            }
            if (useEncryption()) {
                ReportManager.log(EBakaLogType.LOG_ERR,
                        "Chyba při čtení konfigurace. Možná nesprávné heslo?");
            } else {
                ReportManager.log(EBakaLogType.LOG_ERR,
                        "Chyba při čtení konfigurace: " + e.getMessage());
            }
        }
    }

    /**
     * Uloží konfiguraci do výchozího datového souboru.
     */
    public void save() {
        save(DEFAULT_DATA_FILE);
    }

    /**
     * Uloží konfiguraci do souboru.
     *
     * @param filename výstupní soubor (.dat nebo .yml)
     */
    public void save(String filename) {
        if (config == null) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Žádná konfigurace k uložení.");
            return;
        }

        try {
            File file = new File(filename);
            if (filename.endsWith(".dat")) {
                EncryptedConfigLoader.save(config, file, PASSPHRASE);
            } else {
                EncryptedConfigLoader.savePlain(config, file);
            }
        } catch (Exception e) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Chyba při ukládání konfigurace: " + filename);
            if (beVerbose) {
                ReportManager.exceptionMessage(e);
            }
        }
    }

    /**
     * Nastaví heslo pro šifrování/dešifrování.
     *
     * @param passphrase heslo
     */
    public void setPassphrase(String passphrase) {
        this.PASSPHRASE = passphrase.toCharArray();
    }

    /**
     * Interaktivní zadávání konfigurace (CLI wizard).
     */
    public void interactivePrompt() {
        InputStream templateIs = getClass().getResourceAsStream(TEMPLATE_RESOURCE);
        if (templateIs == null) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Šablona konfigurace nebyla nalezena.");
            return;
        }

        // načteme šablonu jako výchozí hodnoty
        YamlAppConfig template = new YamlAppConfig(templateIs);
        Console console = System.console();
        if (console == null) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Konzolový vstup není dostupný.");
            return;
        }

        Map<String, Object> result = new LinkedHashMap<>();

        // LDAP
        Map<String, Object> ldap = new LinkedHashMap<>();
        ldap.put("domain", promptString(console, "Lokální doména AD", template.getLdapDomain()));
        ldap.put("server", promptString(console, "FQDN řadiče domény AD", template.getLdapServer()));
        ldap.put("ssl", promptBoolean(console, "Používat SSL pro komunikaci s AD", template.isLdapSsl()));
        ldap.put("base", promptString(console, "Základní OU uživatelů", template.getLdapBase()));
        ldap.put("students", promptString(console, "OU žáků", template.getLdapBaseStudents()));
        ldap.put("alumni", promptString(console, "OU vyřazených žáků", template.getLdapBaseAlumni()));
        ldap.put("faculty", promptString(console, "OU zaměstnanců", template.getLdapBaseFaculty()));
        ldap.put("teachers", promptString(console, "OU učitelů", template.getLdapBaseTeachers()));
        ldap.put("management", promptString(console, "OU vedení školy", template.getLdapBaseManagement()));
        ldap.put("student_groups", promptString(console, "OU skupin žáků", template.getLdapBaseStudentGroups()));
        ldap.put("global_groups", promptString(console, "OU globálních skupin", template.getLdapBaseGlobalGroups()));
        ldap.put("distribution_lists", promptString(console, "OU distribučních skupin", template.getLdapBaseDistributionLists()));
        ldap.put("contacts", promptString(console, "OU kontaktů", template.getLdapBaseContacts()));
        result.put("ldap", ldap);

        // SQL
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("host", promptString(console, "SQL Server (jméno/IP)", template.getSqlHost()));
        sql.put("database", promptString(console, "Název databáze Bakaláři", template.getSqlDatabase()));
        sql.put("method", promptString(console, "Metoda připojení (ntlm/kerberos)", template.getSqlConnectionMethod()));
        result.put("sql", sql);

        // Mail
        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("domain", promptString(console, "Externí e-mailová doména", template.getMailDomain()));
        mail.put("smtp_host", promptString(console, "SMTP server", template.getSmtpHost()));
        mail.put("admin", promptString(console, "E-mail správce ICT", template.getAdminMail()));
        result.put("mail", mail);

        // Credentials
        Map<String, Object> creds = new LinkedHashMap<>();
        creds.put("user", promptString(console, "Uživatel (Domain Administrator)", template.getUser()));
        String pass = new String(console.readPassword("Heslo (nebude zobrazeno): "));
        creds.put("password", pass);
        result.put("credentials", creds);

        // Policies – převezmeme výchozí
        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("ext_mail_allowed", new ArrayList<>(template.getExtMailAllowed()));
        policies.put("pwd_no_change", new ArrayList<>(template.getPwdNoChange()));
        policies.put("pwd_no_expire", new ArrayList<>(template.getPwdNoExpire()));
        result.put("policies", policies);

        result.put("rules", List.of());

        this.config = new YamlAppConfig(result);
        syncRuntimeFlags();
    }

    /**
     * Okamžitá změna nastavení (pro vývoj/testy).
     */
    public void override(String param, String value) {
        // TODO: implementovat pro YAML strukturu (potřeba v budoucích fázích)
    }

    // ===========================
    // AppConfig implementace
    // ===========================

    @Override public String getLdapDomain() { return delegate().getLdapDomain(); }
    @Override public String getLdapServer() { return delegate().getLdapServer(); }
    @Override public String getLdapHost() { return delegate().getLdapHost(); }
    @Override public String getLdapFqdn() { return delegate().getLdapFqdn(); }
    @Override public boolean isLdapSsl() { return delegate().isLdapSsl(); }
    @Override public String getLdapBase() { return delegate().getLdapBase(); }
    @Override public String getLdapBaseStudents() { return delegate().getLdapBaseStudents(); }
    @Override public String getLdapBaseAlumni() { return delegate().getLdapBaseAlumni(); }
    @Override public String getLdapBaseFaculty() { return delegate().getLdapBaseFaculty(); }
    @Override public String getLdapBaseTeachers() { return delegate().getLdapBaseTeachers(); }
    @Override public String getLdapBaseManagement() { return delegate().getLdapBaseManagement(); }
    @Override public String getLdapBaseStudentGroups() { return delegate().getLdapBaseStudentGroups(); }
    @Override public String getLdapBaseGlobalGroups() { return delegate().getLdapBaseGlobalGroups(); }
    @Override public String getLdapBaseDistributionLists() { return delegate().getLdapBaseDistributionLists(); }
    @Override public String getLdapBaseContacts() { return delegate().getLdapBaseContacts(); }
    @Override public String getUser() { return delegate().getUser(); }
    @Override public String getPass() { return delegate().getPass(); }
    @Override public String getSqlHost() { return delegate().getSqlHost(); }
    @Override public String getSqlDatabase() { return delegate().getSqlDatabase(); }
    @Override public String getSqlConnectionMethod() { return delegate().getSqlConnectionMethod(); }
    @Override public boolean isSqlNtlm() { return delegate().isSqlNtlm(); }
    @Override public boolean isSqlKerberos() { return delegate().isSqlKerberos(); }
    @Override public String getMailDomain() { return delegate().getMailDomain(); }
    @Override public String getSmtpHost() { return delegate().getSmtpHost(); }
    @Override public String getAdminMail() { return delegate().getAdminMail(); }
    @Override public List<Integer> getPwdNoChange() { return delegate().getPwdNoChange(); }
    @Override public List<Integer> getPwdNoExpire() { return delegate().getPwdNoExpire(); }
    @Override public List<Integer> getExtMailAllowed() { return delegate().getExtMailAllowed(); }
    @Override public List<SyncRule> getRules() { return delegate().getRules(); }

    // porty
    @Override public int getLdapPort() { return delegate().getLdapPort(); }
    @Override public int getSqlPort() { return delegate().getSqlPort(); }
    @Override public int getSmtpPort() { return delegate().getSmtpPort(); }

    // per-service credentials
    @Override public String getLdapUser() { return delegate().getLdapUser(); }
    @Override public String getLdapPass() { return delegate().getLdapPass(); }
    @Override public String getSqlUser() { return delegate().getSqlUser(); }
    @Override public String getSqlPass() { return delegate().getSqlPass(); }
    @Override public String getSmtpUser() { return delegate().getSmtpUser(); }
    @Override public String getSmtpPass() { return delegate().getSmtpPass(); }

    // SMTP volby
    @Override public boolean isSmtpAuth() { return delegate().isSmtpAuth(); }
    @Override public boolean isSmtpStarttls() { return delegate().isSmtpStarttls(); }

    @Override
    public boolean isValid() {
        return config != null && config.isValid();
    }

    @Override
    public boolean isVerbose() {
        return beVerbose;
    }

    @Override
    public boolean isDebug() {
        return debugMode;
    }

    @Override
    public boolean isDevelMode() {
        return develMode;
    }

    // ===========================
    // Staré API (bridge pro stávající kód)
    // Bude odstraněno v Fázi 6.
    // ===========================

    /** @deprecated použijte {@link #getLdapHost()} */
    public String getLDAP_host() { return getLdapHost(); }
    /** @deprecated použijte {@link #getLdapDomain()} */
    public String getLDAP_domain() { return getLdapDomain(); }
    /** @deprecated použijte {@link #getLdapFqdn()} */
    public String getLDAP_fqdn() { return getLdapFqdn(); }
    /** @deprecated použijte {@link #getLdapBase()} */
    public String getLDAP_base() { return getLdapBase(); }
    /** @deprecated použijte {@link #getLdapBaseStudents()} */
    public String getLDAP_baseStudents() { return getLdapBaseStudents(); }
    /** @deprecated použijte {@link #getLdapBaseAlumni()} */
    public String getLDAP_baseAlumni() { return getLdapBaseAlumni(); }
    /** @deprecated použijte {@link #getLdapBaseFaculty()} */
    public String getLDAP_baseFaculty() { return getLdapBaseFaculty(); }
    /** @deprecated použijte {@link #getLdapBaseTeachers()} */
    public String getLDAP_baseTeachers() { return getLdapBaseTeachers(); }
    /** @deprecated použijte {@link #getLdapBaseContacts()} */
    public String getLDAP_baseContacts() { return getLdapBaseContacts(); }
    /** @deprecated použijte {@link #getLdapBaseDistributionLists()} */
    public String getLDAP_baseDL() { return getLdapBaseDistributionLists(); }
    /** @deprecated použijte {@link #getLdapBaseStudentGroups()} */
    public String getLDAP_baseStudentGroups() { return getLdapBaseStudentGroups(); }
    /** @deprecated použijte {@link #getLdapBaseGlobalGroups()} */
    public String getLDAP_baseGlobalGroups() { return getLdapBaseGlobalGroups(); }
    /** @deprecated */
    public Boolean isLDAP_MSAD() { return true; }
    /** @deprecated použijte {@link #isLdapSsl()} */
    public Boolean useSSL() { return isLdapSsl(); }
    /** @deprecated použijte {@link #getLdapDomain()} */
    public String getLocalDomain() { return getLdapDomain(); }

    /** @deprecated použijte {@link #getSqlHost()} */
    public String getSQL_host() { return getSqlHost(); }
    /** @deprecated */
    public String getSQL_hostFQDN() {
        return getSqlHost().toLowerCase() + "." + getLdapDomain().toUpperCase();
    }
    /** @deprecated použijte {@link #getSqlDatabase()} */
    public String getSQL_database() { return getSqlDatabase(); }
    /** @deprecated */
    public Boolean sql_NTLM() { return isSqlNtlm(); }
    /** @deprecated */
    public Boolean sql_Kerberos() { return isSqlKerberos(); }
    /** @deprecated */
    public String getSQL_SPN() {
        String host = getSqlHost().toLowerCase().replace("." + getLdapDomain().toLowerCase(), "");
        return "MSSQLSvc/" + host + "." + getLdapDomain().toLowerCase() + "@" + getLdapDomain().toUpperCase();
    }
    /** @deprecated */
    public String getKrb_user() {
        return getSqlUser().toLowerCase() + "@" + getLdapDomain().toUpperCase();
    }

    /** @deprecated použijte {@link #getSmtpHost()} */
    public String getSMTP_host() { return getSmtpHost(); }
    /** @deprecated */
    public String getSMTP_user() {
        return getSmtpUser() + "@" + getMailDomain();
    }
    /** @deprecated */
    public String getSMTP_pass() { return getSmtpPass(); }

    /** @deprecated použijte {@link #isVerbose()} */
    public Boolean beVerbose() { return beVerbose; }
    /** @deprecated použijte {@link #isDebug()} */
    public Boolean debugMode() { return debugMode; }
    /** @deprecated použijte {@link #isDevelMode()} */
    public Boolean develMode() { return develMode; }

    public void verbosity(Boolean verboseMode) { this.beVerbose = verboseMode; }
    public void debug(Boolean debugMode) { this.debugMode = debugMode; }
    public void setDevelMode(Boolean develMode) { this.develMode = develMode; }

    /**
     * Informační značka o aktuálním zpracování.
     *
     * @return značka ve tvaru YYYY-MM-DD HH:mm:ss @ hostname (OS)
     */
    public String systemInfoTag() {
        String hostname;
        String os;

        try {
            hostname = new BufferedReader(
                    new InputStreamReader(Runtime.getRuntime().exec("hostname").getInputStream()))
                    .readLine();
        } catch (IOException e) {
            hostname = "[unknown]";
        }

        try {
            os = System.getProperty("os.name") + " " + System.getProperty("os.version");
        } catch (Exception e) {
            os = "[unknown]";
        }

        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return formatter.format(new Date()) + " @ " + hostname + " (" + os + ")";
    }

    // ===========================
    // Interní
    // ===========================

    /**
     * Interaktivní dotaz na řetězcovou hodnotu.
     */
    private String promptString(Console console, String label, String defaultValue) {
        String def = (defaultValue != null) ? defaultValue : "";
        String input = console.readLine("%s [%s]: ", label, def);
        return (input == null || input.isBlank()) ? def : input;
    }

    /**
     * Interaktivní dotaz na booleovskou hodnotu.
     */
    private boolean promptBoolean(Console console, String label, boolean defaultValue) {
        String def = defaultValue ? "ano" : "ne";
        String input = console.readLine("%s [%s]: ", label, def);
        if (input == null || input.isBlank()) return defaultValue;
        String lower = input.toLowerCase();
        return "a".equals(lower) || "ano".equals(lower) || "y".equals(lower)
                || "yes".equals(lower) || "1".equals(lower);
    }

    private YamlAppConfig delegate() {
        if (config == null) {
            throw new IllegalStateException("Konfigurace nebyla načtena. Zavolejte load() nebo interactivePrompt().");
        }
        return config;
    }

    private boolean useEncryption() {
        return PASSPHRASE != null && PASSPHRASE.length > 0;
    }

    private void syncRuntimeFlags() {
        if (config != null) {
            config.setVerbose(beVerbose);
            config.setDebug(debugMode);
            config.setDevelMode(develMode);
        }
    }

    @Override
    public String toString() {
        if (config == null) return "(konfigurace nenačtena)";
        StringWriter sw = new StringWriter();
        config.writeTo(sw);
        return sw.toString();
    }
}
