package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.KeyStoreManager;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * Správa LDAP připojení a autentizace.
 * Spravuje JNDI prostředí, SSL a autentizaci proti Active Directory.
 *
 * @author Jan Hladěna
 */
class LdapConnectionFactory {

    /** komunikační prostředí (sdíleno s helper třídami) */
    private Hashtable env;

    /** přihlášení je aktivní */
    private boolean authSucceeded = false;

    /** doména principalu */
    private final String domain;

    /** AD server */
    private final String host;

    /** získané informace o přihlášeném uživateli */
    private Map<Integer, Map> authUser;

    /** reference na dotazovací engine (pro vyhledání přihlášeného uživatele) */
    private LdapQueryEngine queryEngine;

    /**
     * Konstruktor – provede autentizaci s výchozím nastavením.
     */
    LdapConnectionFactory() {
        this.domain = Settings.getInstance().getLdapDomain();
        this.host = Settings.getInstance().getLdapHost();
    }

    /**
     * Provede autentizaci. Volá se po nastavení queryEngine.
     */
    void authenticate() {
        authenticate(Settings.getInstance().getLdapUser(), Settings.getInstance().getLdapPass());
    }

    /**
     * Nastaví referenci na dotazovací engine (pro vyhledání auth usera).
     *
     * @param queryEngine dotazovací engine
     */
    void setQueryEngine(LdapQueryEngine queryEngine) {
        this.queryEngine = queryEngine;
    }

    /**
     * Ověření připojení proti AD a vytvoření komunikačního prostředí.
     *
     * @param user uživatelské jméno účtu s právy Domain Admin
     * @param pass heslo daného účtu
     */
    private void authenticate(String user, String pass) {
        // inicializace prostředí
        this.env = new Hashtable();

        // FQDN LDAP serveru
        String fqdn = Settings.getInstance().getLdapFqdn();
        int port = Settings.getInstance().getLdapPort();

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put("com.sun.jndi.ldap.connect.pool", "true");
        if (Settings.getInstance().isLdapSsl()) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            env.put("java.naming.ldap.factory.socket", "cz.zsstudanka.skola.bakakeeper.components.BakaSSLSocketFactory");
            env.put(Context.PROVIDER_URL, "ldaps://" + fqdn + ":" + port + "/");
        } else {
            env.put(Context.PROVIDER_URL, "ldap://" + fqdn + ":" + port + "/");
        }
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user + "@" + domain);
        env.put(Context.SECURITY_CREDENTIALS, pass);

        // binární atributy
        env.put("java.naming.ldap.attributes.binary", EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute());

        boolean reinitializeKeystore = false;

        try {
            // autentizace
            LdapContext ctxGC = null;
            try {
                ctxGC = new InitialLdapContext(env, null);
                // spojení úspěšné
            } catch (NamingException namingEx) {
                reinitializeKeystore = true;
                ReportManager.handleException("Došlo k chybě při vytváření SSL spojení, proběhne pokus o opravu.", namingEx);
            } finally {
                if (ctxGC != null) { try { ctxGC.close(); } catch (NamingException ignored) {} }
            }

            if (reinitializeKeystore) {
                if (KeyStoreManager.reinitialize()) {
                    ReportManager.log(EBakaLogType.LOG_VERBOSE, "Úložiště klíčů bylo obnoveno.");
                } else {
                    ReportManager.log(EBakaLogType.LOG_ERR_VERBOSE, "Nebylo možné obnovit úložiště klíčů.");
                }

                LdapContext ctxRetry = null;
                try {
                    ctxRetry = new InitialLdapContext(env, null);
                    // opakovaný pokus úspěšný
                } finally {
                    if (ctxRetry != null) { try { ctxRetry.close(); } catch (NamingException ignored) {} }
                }
            }

            this.authSucceeded = true;

            if (Settings.getInstance().isVerbose()) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Ověření proti Active Directory proběhlo úspěšně.");
            }

            // uložení lokálních informací o přihlášeném uživateli
            if (queryEngine != null) {
                HashMap<String, String> userLDAPquery = new HashMap<>();
                userLDAPquery.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
                userLDAPquery.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
                userLDAPquery.put(EBakaLDAPAttributes.LOGIN.attribute(), user);

                // hledání v celé doméně (servisní účet může být v jiné OU)
                String domainRootDn = java.util.Arrays.stream(Settings.getInstance().getLdapDomain().split("\\."))
                        .map(part -> "DC=" + part)
                        .collect(java.util.stream.Collectors.joining(","));

                this.authUser = queryEngine.getObjectInfo(domainRootDn, userLDAPquery, new String[]{
                        EBakaLDAPAttributes.UPN.attribute(),
                        EBakaLDAPAttributes.NAME_DISPLAY.attribute()
                });
            }

            if (Settings.getInstance().isDebug()) {
                if (authUserInfo() != null) {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Přihlášení do Active Directory pod účtem " + authUserInfo().get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()) + " (" + authUserInfo().get(EBakaLDAPAttributes.UPN.attribute()) + ").");
                } else {
                    ReportManager.log(EBakaLogType.LOG_DEBUG, "Přihlášení do Active Directory proběhlo, ale uživatel nebyl nalezen v LDAP stromu.");
                }
            }
        } catch (Exception e) {
            ReportManager.handleException("Ověření proti Active Directory se nezdařilo.", e);
            this.authSucceeded = false;
        }
    }

    /**
     * Zjištění příznaku úspěšného připojení.
     *
     * @return spojení navázáno
     */
    boolean isAuthenticated() {
        return this.authSucceeded;
    }

    /**
     * Informace o systémovém účtu.
     *
     * @return uživatelský účet použitý pro přihlášení
     */
    Map authUserInfo() {
        if (!isAuthenticated()) return null;
        return this.authUser != null ? this.authUser.get(0) : null;
    }

    /**
     * Vrátí sdílené komunikační prostředí pro tvorbu LDAP kontextů.
     *
     * @return JNDI environment
     */
    Hashtable getEnv() {
        return this.env;
    }

    /**
     * Vytvoří nový LDAP kontext. Volající je zodpovědný za zavření kontextu.
     *
     * @return nový LDAP kontext
     * @throws NamingException při chybě připojení
     */
    LdapContext createContext() throws NamingException {
        return new InitialLdapContext(env, null);
    }
}
