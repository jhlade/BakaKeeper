package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Služba pro správu stavu uživatelských účtů (zakázání/povolení).
 *
 * @author Jan Hladěna
 */
public interface AccountService {

    /**
     * Zakáže účet – nastaví příznak ACCOUNTDISABLE v UAC.
     * Účet zůstane v původní OU a skupinách (měkký zámek).
     *
     * @param dn distinguished name účtu
     * @param currentUac aktuální hodnota userAccountControl
     * @return výsledek operace
     */
    SyncResult suspendAccount(String dn, int currentUac);

    /**
     * Povolí účet – odebere příznak ACCOUNTDISABLE z UAC.
     *
     * @param dn distinguished name účtu
     * @param currentUac aktuální hodnota userAccountControl
     * @return výsledek operace
     */
    SyncResult unsuspendAccount(String dn, int currentUac);
}
