package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Výsledek resetu hesla jednoho žáka.
 * Rozšiřuje {@link SyncResult} o skutečně nastavené heslo (potřebné pro PDF sestavu).
 *
 * @param result výsledek operace
 * @param password nastavené heslo (null při chybě)
 * @author Jan Hladěna
 */
public record PasswordResetResult(
        SyncResult result,
        String password
) {

    /**
     * Zkratka pro zjištění úspěchu operace.
     *
     * @return true pokud byl reset úspěšný
     */
    public boolean isSuccess() {
        return result.isSuccess();
    }
}
