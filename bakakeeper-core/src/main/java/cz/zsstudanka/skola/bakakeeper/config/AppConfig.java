package cz.zsstudanka.skola.bakakeeper.config;

import java.util.List;

/**
 * Rozhraní konfigurace aplikace BakaKeeper.
 * Poskytuje typovaný přístup ke všem konfiguračním hodnotám.
 *
 * @author Jan Hladěna
 */
public interface AppConfig {

    // --- LDAP / Active Directory ---

    /** Lokální doména AD (např. skola.local). */
    String getLdapDomain();

    /** FQDN řadiče domény (např. dc.skola.local). */
    String getLdapServer();

    /** Krátký hostname AD serveru (extrahovaný z FQDN). */
    String getLdapHost();

    /** Plně kvalifikované jméno LDAP serveru. */
    String getLdapFqdn();

    /** Použít SSL při komunikaci s AD. */
    boolean isLdapSsl();

    /** Základní OU uživatelů. */
    String getLdapBase();

    /** OU žáků. */
    String getLdapBaseStudents();

    /** OU vyřazených žáků (alumni). */
    String getLdapBaseAlumni();

    /** OU zaměstnanců. */
    String getLdapBaseFaculty();

    /** OU učitelů. */
    String getLdapBaseTeachers();

    /** OU vedení školy. */
    String getLdapBaseManagement();

    /** OU skupin žáků. */
    String getLdapBaseStudentGroups();

    /** OU globálních skupin. */
    String getLdapBaseGlobalGroups();

    /** OU distribučních skupin. */
    String getLdapBaseDistributionLists();

    /** OU kontaktů (zákonných zástupců). */
    String getLdapBaseContacts();

    // --- Přihlašovací údaje ---

    /** Uživatelské jméno doménového správce. */
    String getUser();

    /** Heslo doménového správce. */
    String getPass();

    // --- SQL Server ---

    /** FQDN nebo IP SQL Serveru. */
    String getSqlHost();

    /** Název databáze Bakaláři. */
    String getSqlDatabase();

    /** Metoda připojení k SQL (ntlm/kerberos/sql). */
    String getSqlConnectionMethod();

    /** Připojení přes NTLMv2. */
    boolean isSqlNtlm();

    /** Připojení přes Kerberos V5. */
    boolean isSqlKerberos();

    /** Připojení přes SQL Server autentizaci (user/password). */
    default boolean isSqlAuth() { return "sql".equals(getSqlConnectionMethod()); }

    // --- E-mail / SMTP ---

    /** Externí (e-mailová) doména školy. */
    String getMailDomain();

    /** Hostname SMTP serveru. */
    String getSmtpHost();

    /** E-mailová adresa správce ICT. */
    String getAdminMail();

    // --- Politiky ---

    /** Ročníky, které si nemohou měnit heslo. */
    List<Integer> getPwdNoChange();

    /** Ročníky, kterým heslo nikdy nevyprší. */
    List<Integer> getPwdNoExpire();

    /** Ročníky s přístupem k externímu e-mailu. */
    List<Integer> getExtMailAllowed();

    // --- Porty (volitelné, s výchozími hodnotami) ---

    /** LDAP port (výchozí: ssl=true → 636, ssl=false → 389). */
    default int getLdapPort() { return isLdapSsl() ? 636 : 389; }

    /** SQL Server port (výchozí: 1433). */
    default int getSqlPort() { return 1433; }

    /** SMTP port (výchozí: 587). */
    default int getSmtpPort() { return 587; }

    // --- Per-service credentials (fallback na globální) ---

    /** Uživatel pro LDAP (volitelný, výchozí: credentials.user). */
    default String getLdapUser() { return getUser(); }

    /** Heslo pro LDAP (volitelné, výchozí: credentials.password). */
    default String getLdapPass() { return getPass(); }

    /** Uživatel pro SQL (volitelný, výchozí: credentials.user). */
    default String getSqlUser() { return getUser(); }

    /** Heslo pro SQL (volitelné, výchozí: credentials.password). */
    default String getSqlPass() { return getPass(); }

    /** Uživatel pro SMTP (volitelný, výchozí: credentials.user). */
    default String getSmtpUser() { return getUser(); }

    /** Heslo pro SMTP (volitelné, výchozí: credentials.password). */
    default String getSmtpPass() { return getPass(); }

    // --- SMTP volby ---

    /** SMTP vyžaduje autentizaci (výchozí: true). */
    default boolean isSmtpAuth() { return true; }

    /** SMTP používá STARTTLS (výchozí: true). */
    default boolean isSmtpStarttls() { return true; }

    // --- Deklarativní pravidla ---

    /** Seznam deklarativních pravidel pro synchronizaci. */
    List<SyncRule> getRules();

    // --- Runtime příznaky (neperzistované) ---

    /** Podrobný výstup. */
    boolean isVerbose();

    /** Ladící výstup. */
    boolean isDebug();

    /** Vývojový režim. */
    boolean isDevelMode();

    // --- Validita ---

    /** Konfigurace je kompletní a platná. */
    boolean isValid();
}
