package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Callback pro sledování průběhu synchronizace.
 * Implementace může směřovat do CLI, GUI, nebo logového souboru.
 *
 * @author Jan Hladěna
 */
public interface SyncProgressListener {

    /** Obecná zpráva o průběhu. */
    void onProgress(String message);

    /** Výsledek jedné operace. */
    void onResult(SyncResult result);

    /** Začátek fáze synchronizace. */
    void onPhaseStart(String phaseName);

    /** Konec fáze synchronizace. */
    void onPhaseEnd(String phaseName, int successCount, int errorCount);

    /** Výchozí tichý listener (nic nevypisuje). */
    SyncProgressListener SILENT = new SyncProgressListener() {
        @Override public void onProgress(String message) {}
        @Override public void onResult(SyncResult result) {}
        @Override public void onPhaseStart(String phaseName) {}
        @Override public void onPhaseEnd(String phaseName, int successCount, int errorCount) {}
    };
}
