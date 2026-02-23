package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.Writer;
import java.util.*;

/**
 * Implementace AppConfig načítaná z YAML souboru.
 * Používá SnakeYAML pro parsing a serializaci.
 *
 * @author Jan Hladěna
 */
public class YamlAppConfig implements AppConfig {

    /** surová data z YAML */
    private final Map<String, Object> root;

    /** runtime příznaky – neperzistované */
    private boolean verbose;
    private boolean debug;
    private boolean develMode;

    /** příznak validity */
    private boolean valid;

    /**
     * Načte konfiguraci z YAML vstupního proudu.
     *
     * @param input vstupní proud s YAML obsahem
     */
    public YamlAppConfig(InputStream input) {
        Yaml yaml = new Yaml();
        Map<String, Object> loaded = yaml.load(input);
        this.root = (loaded != null) ? loaded : new LinkedHashMap<>();
        this.valid = validate();
    }

    /**
     * Vytvoří konfiguraci z hotové mapy (pro testy a programové vytváření).
     *
     * @param data mapa konfiguračních dat
     */
    public YamlAppConfig(Map<String, Object> data) {
        this.root = (data != null) ? data : new LinkedHashMap<>();
        this.valid = validate();
    }

    // --- LDAP ---

    @Override
    public String getLdapDomain() {
        return getString("ldap", "domain");
    }

    @Override
    public String getLdapServer() {
        return getString("ldap", "server");
    }

    @Override
    public String getLdapHost() {
        String server = getLdapServer();
        String domain = getLdapDomain();
        if (server == null) return null;
        if (domain != null && server.toLowerCase().contains(domain.toLowerCase())) {
            return server.toLowerCase().replace("." + domain.toLowerCase(), "");
        }
        return server.toLowerCase();
    }

    @Override
    public String getLdapFqdn() {
        String host = getLdapHost();
        String domain = getLdapDomain();
        if (host == null || domain == null) return null;
        return host + "." + domain;
    }

    @Override
    public boolean isLdapSsl() {
        return getBoolean("ldap", "ssl");
    }

    @Override
    public String getLdapBase() {
        return getString("ldap", "base");
    }

    @Override
    public String getLdapBaseStudents() {
        return getString("ldap", "students");
    }

    @Override
    public String getLdapBaseAlumni() {
        return getString("ldap", "alumni");
    }

    @Override
    public String getLdapBaseFaculty() {
        return getString("ldap", "faculty");
    }

    @Override
    public String getLdapBaseTeachers() {
        return getString("ldap", "teachers");
    }

    @Override
    public String getLdapBaseManagement() {
        return getString("ldap", "management");
    }

    @Override
    public String getLdapBaseStudentGroups() {
        return getString("ldap", "student_groups");
    }

    @Override
    public String getLdapBaseGlobalGroups() {
        return getString("ldap", "global_groups");
    }

    @Override
    public String getLdapBaseDistributionLists() {
        return getString("ldap", "distribution_lists");
    }

    @Override
    public String getLdapBaseContacts() {
        return getString("ldap", "contacts");
    }

    // --- Přihlašovací údaje ---

    @Override
    public String getUser() {
        return getString("credentials", "user");
    }

    @Override
    public String getPass() {
        return getString("credentials", "password");
    }

    // --- SQL ---

    @Override
    public String getSqlHost() {
        return getString("sql", "host");
    }

    @Override
    public String getSqlDatabase() {
        return getString("sql", "database");
    }

    @Override
    public String getSqlConnectionMethod() {
        String method = getString("sql", "method");
        return (method != null) ? method.toLowerCase() : "ntlm";
    }

    @Override
    public boolean isSqlNtlm() {
        return "ntlm".equals(getSqlConnectionMethod());
    }

    @Override
    public boolean isSqlKerberos() {
        return "kerberos".equals(getSqlConnectionMethod());
    }

    // --- Mail ---

    @Override
    public String getMailDomain() {
        return getString("mail", "domain");
    }

    @Override
    public String getSmtpHost() {
        return getString("mail", "smtp_host");
    }

    @Override
    public String getAdminMail() {
        return getString("mail", "admin");
    }

    // --- Politiky ---

    @Override
    public List<Integer> getPwdNoChange() {
        return getIntegerList("policies", "pwd_no_change");
    }

    @Override
    public List<Integer> getPwdNoExpire() {
        return getIntegerList("policies", "pwd_no_expire");
    }

    @Override
    public List<Integer> getExtMailAllowed() {
        return getIntegerList("policies", "ext_mail_allowed");
    }

    // --- Pravidla ---

    @Override
    public List<SyncRule> getRules() {
        Object rawRules = root.get("rules");
        if (!(rawRules instanceof List<?> list)) {
            return List.of();
        }

        List<SyncRule> result = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                SyncRule rule = parseRule(map);
                if (rule != null) {
                    result.add(rule);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    // --- Runtime příznaky ---

    @Override
    public boolean isVerbose() {
        return verbose;
    }

    @Override
    public boolean isDebug() {
        return debug;
    }

    @Override
    public boolean isDevelMode() {
        return develMode;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setDevelMode(boolean develMode) {
        this.develMode = develMode;
    }

    // --- Validita ---

    @Override
    public boolean isValid() {
        return valid;
    }

    // --- Serializace do YAML ---

    /**
     * Zapíše konfiguraci do YAML formátu.
     *
     * @param writer výstupní writer
     */
    public void writeTo(Writer writer) {
        // sestavíme čistou mapu pro serializaci (bez runtime příznaků)
        Map<String, Object> output = buildOutputMap();
        Yaml yaml = new Yaml();
        yaml.dump(output, writer);
    }

    /**
     * Vrátí surová konfigurační data (pro šifrované ukládání).
     *
     * @return kořenová mapa
     */
    public Map<String, Object> getRawData() {
        return Collections.unmodifiableMap(root);
    }

    // ========================
    // Interní pomocné metody
    // ========================

    /**
     * Získá vnořenou sekci jako mapu.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSection(String section) {
        Object val = root.get(section);
        if (val instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    /**
     * Získá řetězcovou hodnotu ze sekce.
     */
    private String getString(String section, String key) {
        Object val = getSection(section).get(key);
        return (val != null) ? val.toString() : null;
    }

    /**
     * Získá booleovskou hodnotu ze sekce.
     */
    private boolean getBoolean(String section, String key) {
        Object val = getSection(section).get(key);
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return "true".equalsIgnoreCase(s) || "1".equals(s) || "ano".equalsIgnoreCase(s);
        if (val instanceof Number n) return n.intValue() != 0;
        return false;
    }

    /**
     * Získá seznam celých čísel ze sekce.
     */
    @SuppressWarnings("unchecked")
    private List<Integer> getIntegerList(String section, String key) {
        Object val = getSection(section).get(key);
        if (val instanceof List<?> list) {
            List<Integer> result = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Number n) {
                    result.add(n.intValue());
                } else if (item instanceof String s) {
                    result.add(Integer.parseInt(s.trim()));
                }
            }
            return Collections.unmodifiableList(result);
        }
        // podpora CSV řetězce (kompatibilita)
        if (val instanceof String s && !s.isBlank()) {
            List<Integer> result = new ArrayList<>();
            for (String part : s.replace(" ", "").split(",")) {
                result.add(Integer.parseInt(part));
            }
            return Collections.unmodifiableList(result);
        }
        return List.of();
    }

    /**
     * Parsuje jedno pravidlo z YAML mapy.
     */
    private SyncRule parseRule(Map<?, ?> map) {
        try {
            String scopeStr = Objects.toString(map.get("scope"), null);
            String match = Objects.toString(map.get("match"), null);
            String attribute = Objects.toString(map.get("attribute"), null);
            String value = Objects.toString(map.get("value"), null);

            if (scopeStr == null || attribute == null) return null;

            SyncScope scope = SyncScope.valueOf(scopeStr.toUpperCase());
            return new SyncRule(scope, match, attribute, value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Validuje, zda jsou přítomny všechny povinné konfigurační klíče.
     */
    private boolean validate() {
        return getString("ldap", "domain") != null
                && getString("ldap", "server") != null
                && getString("ldap", "base") != null
                && getString("ldap", "students") != null
                && getString("ldap", "alumni") != null
                && getString("ldap", "faculty") != null
                && getString("ldap", "teachers") != null
                && getString("ldap", "management") != null
                && getString("ldap", "student_groups") != null
                && getString("ldap", "global_groups") != null
                && getString("ldap", "distribution_lists") != null
                && getString("ldap", "contacts") != null
                && getString("credentials", "user") != null
                && getString("credentials", "password") != null
                && getString("sql", "host") != null
                && getString("sql", "database") != null
                && getString("mail", "domain") != null
                && getString("mail", "smtp_host") != null
                && getString("mail", "admin") != null;
    }

    /**
     * Sestaví výstupní mapu pro YAML serializaci.
     */
    private Map<String, Object> buildOutputMap() {
        Map<String, Object> output = new LinkedHashMap<>();

        Map<String, Object> ldap = new LinkedHashMap<>();
        ldap.put("domain", getLdapDomain());
        ldap.put("server", getLdapServer());
        ldap.put("ssl", isLdapSsl());
        ldap.put("base", getLdapBase());
        ldap.put("students", getLdapBaseStudents());
        ldap.put("alumni", getLdapBaseAlumni());
        ldap.put("faculty", getLdapBaseFaculty());
        ldap.put("teachers", getLdapBaseTeachers());
        ldap.put("management", getLdapBaseManagement());
        ldap.put("student_groups", getLdapBaseStudentGroups());
        ldap.put("global_groups", getLdapBaseGlobalGroups());
        ldap.put("distribution_lists", getLdapBaseDistributionLists());
        ldap.put("contacts", getLdapBaseContacts());
        output.put("ldap", ldap);

        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("host", getSqlHost());
        sql.put("database", getSqlDatabase());
        sql.put("method", getSqlConnectionMethod());
        output.put("sql", sql);

        Map<String, Object> mail = new LinkedHashMap<>();
        mail.put("domain", getMailDomain());
        mail.put("smtp_host", getSmtpHost());
        mail.put("admin", getAdminMail());
        output.put("mail", mail);

        Map<String, Object> creds = new LinkedHashMap<>();
        creds.put("user", getUser());
        creds.put("password", getPass());
        output.put("credentials", creds);

        Map<String, Object> policies = new LinkedHashMap<>();
        policies.put("ext_mail_allowed", new ArrayList<>(getExtMailAllowed()));
        policies.put("pwd_no_change", new ArrayList<>(getPwdNoChange()));
        policies.put("pwd_no_expire", new ArrayList<>(getPwdNoExpire()));
        output.put("policies", policies);

        // pravidla
        List<Map<String, Object>> rulesOut = new ArrayList<>();
        for (SyncRule rule : getRules()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("scope", rule.getScope().name());
            r.put("match", rule.getMatch());
            r.put("attribute", rule.getAttribute());
            r.put("value", rule.getValue());
            rulesOut.add(r);
        }
        output.put("rules", rulesOut);

        return output;
    }
}
