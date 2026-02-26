package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Služba pro kontrolu konektivity a konfigurace.
 *
 * @author Jan Hladěna
 */
public interface CheckService {

    /** Provede kompletní kontrolu všech služeb (konfigurace, AD, SQL, SMTP). */
    List<CheckResult> checkAll();

    /** Kontrola validity konfigurace. */
    CheckResult checkConfig();

    /** Kontrola LDAP/AD spojení. */
    CheckResult checkLdap();

    /** Kontrola SQL spojení. */
    CheckResult checkSql();

    /** Kontrola SMTP spojení. */
    CheckResult checkSmtp();
}
