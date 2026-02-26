package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.model.InternalUserSnapshot;

import java.util.Optional;

/**
 * Repozitář pro přístup k interním uživatelům (tabulka {@code dbo.webuser}).
 *
 * @author Jan Hladěna
 */
public interface InternalUserRepository {

    /**
     * Načte snapshot uživatele podle přihlašovacího jména.
     * Pro správce Bakalářů použijte login {@code "*"}.
     *
     * @param login přihlašovací jméno
     * @return snapshot uživatele, nebo prázdný pokud neexistuje
     */
    Optional<InternalUserSnapshot> findByLogin(String login);

    /**
     * Zapíše snapshot zpět do databáze (aktualizace hesla/metody/sůli).
     * Používá se pro obnovu hesla správce z auditní zálohy.
     *
     * @param snapshot data k zápisu
     */
    void writeBack(InternalUserSnapshot snapshot);
}
