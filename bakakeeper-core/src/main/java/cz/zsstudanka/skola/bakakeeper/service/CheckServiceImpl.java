package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.AppConfig;
import cz.zsstudanka.skola.bakakeeper.connectors.BakaMailer;
import cz.zsstudanka.skola.bakakeeper.connectors.LDAPConnector;
import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementace kontroly konektivity a konfigurace.
 * Testuje AD, SQL a SMTP spojení přes abstraktní rozhraní konektorů.
 *
 * @author Jan Hladěna
 */
public class CheckServiceImpl implements CheckService {

    private final AppConfig config;
    private final LDAPConnector ldap;
    private final SQLConnector sql;
    private final BakaMailer mailer;

    public CheckServiceImpl(AppConfig config, LDAPConnector ldap,
                             SQLConnector sql, BakaMailer mailer) {
        this.config = config;
        this.ldap = ldap;
        this.sql = sql;
        this.mailer = mailer;
    }

    @Override
    public List<CheckResult> checkAll() {
        List<CheckResult> results = new ArrayList<>();
        results.add(checkConfig());
        results.add(checkLdap());
        results.add(checkSql());
        results.add(checkSmtp());
        return results;
    }

    @Override
    public CheckResult checkConfig() {
        if (config.isValid()) {
            return CheckResult.success("Konfigurace");
        }
        return CheckResult.failure("Konfigurace", "Konfigurační data nejsou validní.");
    }

    @Override
    public CheckResult checkLdap() {
        try {
            if (ldap.isAuthenticated()) {
                return CheckResult.success("Active Directory");
            }
            return CheckResult.failure("Active Directory", "Nepodařilo se autentizovat vůči AD.");
        } catch (Exception e) {
            return CheckResult.failure("Active Directory", e.getMessage());
        }
    }

    @Override
    public CheckResult checkSql() {
        try {
            if (sql.testConnection()) {
                return CheckResult.success("SQL Server");
            }
            return CheckResult.failure("SQL Server", "Nepodařilo se připojit k SQL serveru.");
        } catch (Exception e) {
            return CheckResult.failure("SQL Server", e.getMessage());
        }
    }

    @Override
    public CheckResult checkSmtp() {
        try {
            if (mailer.testSMTP()) {
                return CheckResult.success("SMTP");
            }
            return CheckResult.failure("SMTP", "Nepodařilo se připojit na SMTP server.");
        } catch (Exception e) {
            return CheckResult.failure("SMTP", e.getMessage());
        }
    }
}
