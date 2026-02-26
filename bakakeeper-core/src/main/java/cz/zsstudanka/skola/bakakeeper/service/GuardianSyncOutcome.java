package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Výsledek synchronizace zákonných zástupců – kombinuje operační výsledky
 * a validační chyby (chybné kontaktní údaje, chybějící primární zástupce).
 *
 * @param results         výsledky synchronizačních operací
 * @param validationErrors validační chyby kontaktních údajů
 *
 * @author Jan Hladěna
 */
public record GuardianSyncOutcome(
        List<SyncResult> results,
        List<GuardianValidationError> validationErrors
) {}
