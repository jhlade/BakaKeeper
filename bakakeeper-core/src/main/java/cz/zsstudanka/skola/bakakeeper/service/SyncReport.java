package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Agregovaný výsledek kompletní synchronizace.
 * Obsahuje seznam jednotlivých výsledků a validačních chyb zástupců.
 *
 * @param results výsledky synchronizačních operací
 * @param guardianErrors validační chyby zákonných zástupců (chybný tel./email, neprimární zástupce)
 *
 * @author Jan Hladěna
 */
public record SyncReport(
        List<SyncResult> results,
        List<GuardianValidationError> guardianErrors
) {

    /** Počet vytvořených objektů. */
    public int created() { return countType(SyncResult.Type.CREATED); }

    /** Počet aktualizovaných objektů. */
    public int updated() { return countType(SyncResult.Type.UPDATED); }

    /** Počet vyřazených objektů. */
    public int retired() { return countType(SyncResult.Type.RETIRED); }

    /** Počet spárovaných objektů. */
    public int paired() { return countType(SyncResult.Type.PAIRED); }

    /** Počet chyb. */
    public int errors() { return countType(SyncResult.Type.ERROR); }

    /** Počet objektů beze změny. */
    public int noChange() { return countType(SyncResult.Type.NO_CHANGE); }

    /** Počet přeskočených objektů. */
    public int skipped() { return countType(SyncResult.Type.SKIPPED); }

    /** Celkový počet operací (bez NO_CHANGE a SKIPPED). */
    public int totalActions() {
        return created() + updated() + retired() + paired() + errors();
    }

    /** Synchronizace proběhla bez chyb. */
    public boolean isSuccess() {
        return errors() == 0;
    }

    private int countType(SyncResult.Type type) {
        return (int) results.stream().filter(r -> r.getType() == type).count();
    }
}
