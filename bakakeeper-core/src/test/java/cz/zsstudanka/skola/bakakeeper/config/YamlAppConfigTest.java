package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro YamlAppConfig.
 *
 * @author Jan Hladěna
 */
class YamlAppConfigTest {

    private static YamlAppConfig config;

    @BeforeAll
    static void setUp() {
        InputStream is = YamlAppConfigTest.class.getResourceAsStream("/test-config.yml");
        assertNotNull(is, "Testovací YAML soubor musí existovat");
        config = new YamlAppConfig(is);
    }

    // --- LDAP ---

    @Test
    void ldapDomain() {
        assertEquals("test.local", config.getLdapDomain());
    }

    @Test
    void ldapServer() {
        assertEquals("dc.test.local", config.getLdapServer());
    }

    @Test
    void ldapHostExtracted() {
        // z "dc.test.local" s doménou "test.local" → "dc"
        assertEquals("dc", config.getLdapHost());
    }

    @Test
    void ldapFqdn() {
        assertEquals("dc.test.local", config.getLdapFqdn());
    }

    @Test
    void ldapSsl() {
        assertTrue(config.isLdapSsl());
    }

    @Test
    void ldapBase() {
        assertEquals("OU=Uzivatele,DC=test,DC=local", config.getLdapBase());
    }

    @Test
    void ldapBaseStudents() {
        assertEquals("OU=Zaci,OU=Uzivatele,DC=test,DC=local", config.getLdapBaseStudents());
    }

    @Test
    void ldapBaseAlumni() {
        assertEquals("OU=Alumni,OU=Zaci,OU=Uzivatele,DC=test,DC=local", config.getLdapBaseAlumni());
    }

    @Test
    void ldapBaseContacts() {
        assertEquals("OU=Kontakty,DC=test,DC=local", config.getLdapBaseContacts());
    }

    @Test
    void ldapBaseDistributionLists() {
        assertEquals("OU=DL,OU=Skupiny,DC=test,DC=local", config.getLdapBaseDistributionLists());
    }

    // --- SQL ---

    @Test
    void sqlHost() {
        assertEquals("sql.test.local", config.getSqlHost());
    }

    @Test
    void sqlDatabase() {
        assertEquals("testdb", config.getSqlDatabase());
    }

    @Test
    void sqlMethodNtlm() {
        assertEquals("ntlm", config.getSqlConnectionMethod());
        assertTrue(config.isSqlNtlm());
        assertFalse(config.isSqlKerberos());
    }

    // --- Mail ---

    @Test
    void mailDomain() {
        assertEquals("test.ext", config.getMailDomain());
    }

    @Test
    void smtpHost() {
        assertEquals("mail.test.ext", config.getSmtpHost());
    }

    @Test
    void adminMail() {
        assertEquals("admin@test.ext", config.getAdminMail());
    }

    // --- Credentials ---

    @Test
    void user() {
        assertEquals("testuser", config.getUser());
    }

    @Test
    void password() {
        assertEquals("heslo123", config.getPass());
    }

    // --- Politiky ---

    @Test
    void pwdNoChange() {
        assertEquals(List.of(1, 2, 3), config.getPwdNoChange());
    }

    @Test
    void pwdNoExpire() {
        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), config.getPwdNoExpire());
    }

    @Test
    void extMailAllowed() {
        assertEquals(List.of(6, 7, 8, 9), config.getExtMailAllowed());
    }

    // --- Pravidla ---

    @Test
    void rulesLoaded() {
        List<SyncRule> rules = config.getRules();
        assertEquals(2, rules.size());
    }

    @Test
    void firstRule() {
        SyncRule rule = config.getRules().get(0);
        assertEquals(SyncScope.CLASS, rule.getScope());
        assertEquals("6.A", rule.getMatch());
        assertEquals("extensionAttribute5", rule.getAttribute());
        assertEquals("TRUE", rule.getValue());
    }

    @Test
    void secondRule() {
        SyncRule rule = config.getRules().get(1);
        assertEquals(SyncScope.GRADE, rule.getScope());
        assertEquals("9", rule.getMatch());
    }

    // --- Validita ---

    @Test
    void validConfig() {
        assertTrue(config.isValid());
    }

    @Test
    void invalidConfigMissingKeys() {
        String minimal = "ldap:\n  domain: test.local\n";
        YamlAppConfig invalid = new YamlAppConfig(
                new ByteArrayInputStream(minimal.getBytes(StandardCharsets.UTF_8)));
        assertFalse(invalid.isValid());
    }

    @Test
    void emptyConfigInvalid() {
        YamlAppConfig empty = new YamlAppConfig(
                new ByteArrayInputStream("{}".getBytes(StandardCharsets.UTF_8)));
        assertFalse(empty.isValid());
    }

    // --- Runtime příznaky ---

    @Test
    void runtimeFlagsDefault() {
        assertFalse(config.isVerbose());
        assertFalse(config.isDebug());
        assertFalse(config.isDevelMode());
    }

    @Test
    void runtimeFlagsModifiable() {
        YamlAppConfig c = new YamlAppConfig(
                YamlAppConfigTest.class.getResourceAsStream("/test-config.yml"));
        c.setVerbose(true);
        c.setDebug(true);
        c.setDevelMode(true);
        assertTrue(c.isVerbose());
        assertTrue(c.isDebug());
        assertTrue(c.isDevelMode());
    }

    // --- Serializace ---

    @Test
    void writeToYaml() {
        StringWriter sw = new StringWriter();
        config.writeTo(sw);
        String yaml = sw.toString();
        assertTrue(yaml.contains("test.local"));
        assertTrue(yaml.contains("sql.test.local"));
        assertTrue(yaml.contains("testuser"));
    }

    @Test
    void roundTrip() {
        // zapíšeme do YAML a znovu načteme
        StringWriter sw = new StringWriter();
        config.writeTo(sw);

        YamlAppConfig reloaded = new YamlAppConfig(
                new ByteArrayInputStream(sw.toString().getBytes(StandardCharsets.UTF_8)));

        assertEquals(config.getLdapDomain(), reloaded.getLdapDomain());
        assertEquals(config.getSqlHost(), reloaded.getSqlHost());
        assertEquals(config.getUser(), reloaded.getUser());
        assertEquals(config.getPwdNoChange(), reloaded.getPwdNoChange());
        assertEquals(config.getRules().size(), reloaded.getRules().size());
        assertTrue(reloaded.isValid());
    }

    // --- Kerberos SQL metoda ---

    @Test
    void sqlKerberos() {
        String yaml = """
                ldap:
                  domain: k.local
                  server: dc.k.local
                  ssl: false
                  base: "DC=k,DC=local"
                  students: "OU=S,DC=k,DC=local"
                  alumni: "OU=A,DC=k,DC=local"
                  faculty: "OU=F,DC=k,DC=local"
                  teachers: "OU=T,DC=k,DC=local"
                  management: "OU=M,DC=k,DC=local"
                  student_groups: "OU=SG,DC=k,DC=local"
                  global_groups: "OU=GG,DC=k,DC=local"
                  distribution_lists: "OU=DL,DC=k,DC=local"
                  contacts: "OU=C,DC=k,DC=local"
                sql:
                  host: sql.k.local
                  database: db
                  method: kerberos
                mail:
                  domain: k.ext
                  smtp_host: mail.k.ext
                  admin: a@k.ext
                credentials:
                  user: u
                  password: p
                policies:
                  ext_mail_allowed: []
                  pwd_no_change: []
                  pwd_no_expire: []
                rules: []
                """;

        YamlAppConfig krbConfig = new YamlAppConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(krbConfig.isSqlKerberos());
        assertFalse(krbConfig.isSqlNtlm());
        assertFalse(krbConfig.isLdapSsl());
    }

    // --- Prázdné pravidla ---

    @Test
    void emptyRules() {
        // template z resources nemá pravidla
        InputStream is = YamlAppConfigTest.class.getResourceAsStream("/bakakeeper.yml");
        assertNotNull(is);
        YamlAppConfig template = new YamlAppConfig(is);
        assertTrue(template.getRules().isEmpty());
        assertTrue(template.isValid());
    }

    // --- Neplatné pravidlo (neznámý scope) ---

    @Test
    void invalidRuleIgnored() {
        String yaml = """
                ldap:
                  domain: t.local
                  server: dc.t.local
                  ssl: false
                  base: "DC=t,DC=local"
                  students: "OU=S,DC=t,DC=local"
                  alumni: "OU=A,DC=t,DC=local"
                  faculty: "OU=F,DC=t,DC=local"
                  teachers: "OU=T,DC=t,DC=local"
                  management: "OU=M,DC=t,DC=local"
                  student_groups: "OU=SG,DC=t,DC=local"
                  global_groups: "OU=GG,DC=t,DC=local"
                  distribution_lists: "OU=DL,DC=t,DC=local"
                  contacts: "OU=C,DC=t,DC=local"
                sql:
                  host: sql.t.local
                  database: db
                  method: ntlm
                mail:
                  domain: t.ext
                  smtp_host: m.t.ext
                  admin: a@t.ext
                credentials:
                  user: u
                  password: p
                policies:
                  ext_mail_allowed: []
                  pwd_no_change: []
                  pwd_no_expire: []
                rules:
                  - scope: NEEXISTUJE
                    match: "x"
                    attribute: attr
                    value: val
                  - scope: CLASS
                    match: "5.B"
                    attribute: attr2
                    value: val2
                """;
        YamlAppConfig c = new YamlAppConfig(
                new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));
        // neplatný scope se přeskočí, platný zůstane
        assertEquals(1, c.getRules().size());
        assertEquals("5.B", c.getRules().get(0).getMatch());
    }
}
