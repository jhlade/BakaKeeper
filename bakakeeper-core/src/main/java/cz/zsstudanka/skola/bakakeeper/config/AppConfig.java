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

    /** Metoda připojení k SQL (ntlm/kerberos). */
    String getSqlConnectionMethod();

    /** Připojení přes NTLMv2. */
    boolean isSqlNtlm();

    /** Připojení přes Kerberos V5. */
    boolean isSqlKerberos();

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
