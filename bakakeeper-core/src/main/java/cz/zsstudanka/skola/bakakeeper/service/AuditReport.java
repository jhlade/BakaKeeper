package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Agregovaný výsledek auditní kontroly.
 *
 * @param results seznam auditních zjištění
 *
 * @author Jan Hladěna
 */
public record AuditReport(List<AuditResult> results) {

    /** Zjistí, zda audit detekoval změnu hesla správce. */
    public boolean adminPasswordChanged() {
        return results.stream().anyMatch(r -> r instanceof AuditResult.AdminPasswordChanged);
    }

    /** Zjistí, zda audit proběhl bez varovných nálezů. */
    public boolean isClean() {
        return results.stream().noneMatch(r ->
                r instanceof AuditResult.AdminPasswordChanged
                        || r instanceof AuditResult.UserNotFound);
    }
}
