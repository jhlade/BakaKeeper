package cz.zsstudanka.skola.bakakeeper.model;

import java.io.Serializable;
import java.util.Date;

/**
 * Neměnný snapshot interního uživatele z tabulky {@code dbo.webuser}.
 * Slouží pro auditní záznamy – porovnání aktuálního stavu proti záloze.
 *
 * @param id         interní kód (INTERN_KOD)
 * @param login      přihlašovací jméno ('*' = správce)
 * @param type       typ účtu – R/S/V/Z (KOD1)
 * @param acl        oprávnění (PRAVA)
 * @param updType    typ aktualizace (UPD_TYP)
 * @param kodf       kód (KODF)
 * @param pwdHash    B64 hash hesla (HESLO)
 * @param pwdMethod  hashovací metoda (METODA)
 * @param pwdSalt    sůl hashe (SALT)
 * @param modified   datum poslední úpravy
 * @param modifiedBy autor poslední úpravy
 *
 * @author Jan Hladěna
 */
public record InternalUserSnapshot(
        String id,
        String login,
        String type,
        String acl,
        String updType,
        String kodf,
        String pwdHash,
        String pwdMethod,
        String pwdSalt,
        Date modified,
        String modifiedBy
) implements Serializable {

    /**
     * Kontrola, zda se heslo (hash+metoda+sůl) liší od jiného snapshotu.
     *
     * @param other porovnávaný snapshot
     * @return true pokud se heslo liší
     */
    public boolean passwordDiffers(InternalUserSnapshot other) {
        if (other == null) return true;
        return !nullSafeEquals(pwdHash, other.pwdHash)
                || !nullSafeEquals(pwdMethod, other.pwdMethod)
                || !nullSafeEquals(pwdSalt, other.pwdSalt);
    }

    private static boolean nullSafeEquals(String a, String b) {
        return (a == null) ? (b == null) : a.equals(b);
    }
}
