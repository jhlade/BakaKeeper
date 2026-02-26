package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Služba pro audit interních dat – detekce změn hesla správce,
 * porovnání stavu v databázi se zálohou.
 *
 * @author Jan Hladěna
 */
public interface AuditService {

    /**
     * Provede kompletní audit – aktuálně kontrola hesla správce.
     *
     * @return auditní report
     */
    AuditReport runFullAudit();

    /**
     * Provede audit pouze hesla správce.
     *
     * @return auditní report
     */
    AuditReport auditAdminPassword();

    /**
     * Obnoví heslo správce z poslední zálohy.
     *
     * @return auditní report (obsahuje {@link AuditResult.AdminPasswordReverted}
     *         nebo {@link AuditResult.UserNotFound})
     */
    AuditReport revertAdminPassword();

    /**
     * Záloha aktuálního stavu hesla správce (pro automatické volání z sync/check).
     */
    void backupAdminPassword();
}
