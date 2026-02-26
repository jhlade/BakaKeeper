package cz.zsstudanka.skola.bakakeeper.service;

import java.util.Date;

/**
 * Výsledek auditní kontroly – uzavřená hierarchie.
 * Každý podtyp reprezentuje konkrétní zjištění.
 *
 * @author Jan Hladěna
 */
public sealed interface AuditResult {

    /** Heslo správce se změnilo oproti poslední záloze. */
    record AdminPasswordChanged(String login, Date lastKnown, Date current) implements AuditResult {}

    /** Heslo správce je shodné s poslední zálohou. */
    record AdminPasswordOk(String login, Date lastChecked) implements AuditResult {}

    /** Heslo správce nemá žádnou předchozí zálohu – nová záloha vytvořena. */
    record AdminPasswordFirstBackup(String login) implements AuditResult {}

    /** Heslo správce bylo obnoveno ze zálohy. */
    record AdminPasswordReverted(String login) implements AuditResult {}

    /** Interní uživatel nebyl nalezen v databázi. */
    record UserNotFound(String login) implements AuditResult {}
}
