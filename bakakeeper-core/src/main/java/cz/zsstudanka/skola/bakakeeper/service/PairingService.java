package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

/**
 * Služba pro párování SQL záznamů s existujícími LDAP účty.
 *
 * @author Jan Hladěna
 */
public interface PairingService {

    /**
     * Pokusí se spárovat žáka z evidence s existujícím LDAP účtem.
     * Porovnává příjmení, jméno, ročník a třídu.
     *
     * @param sqlStudent žák z evidence
     * @param ldapStudent existující LDAP účet
     * @param repair zapsat párování (true) nebo jen zkontrolovat (false)
     * @return výsledek párování
     */
    SyncResult attemptToPair(StudentRecord sqlStudent, StudentRecord ldapStudent, boolean repair);
}
