package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

import java.util.List;

/**
 * Repository pro operace s kontakty zákonných zástupců v Active Directory.
 *
 * @author Jan Hladěna
 */
public interface GuardianRepository {

    /**
     * Nalezne všechny kontakty zákonných zástupců.
     *
     * @param baseOu bázová OU kontaktů
     * @return seznam záznamů zákonných zástupců
     */
    List<GuardianRecord> findAllContacts(String baseOu);

    /**
     * Nalezne kontakt podle interního ID (extensionAttribute1).
     *
     * @param baseOu bázová OU kontaktů
     * @param internalId interní kód z Bakalářů
     * @return záznam, nebo null
     */
    GuardianRecord findByInternalId(String baseOu, String internalId);

    /**
     * Vytvoří nový kontakt v AD.
     *
     * @param cn common name
     * @param data atributy kontaktu
     */
    void createContact(String cn, DataLDAP data);

    /**
     * Smaže kontakt z AD.
     *
     * @param dn distinguished name
     * @return úspěch operace
     */
    boolean deleteContact(String dn);

    /**
     * Zjistí členství kontaktu v distribučních skupinách.
     *
     * @param dn distinguished name
     * @return seznam DN distribučních skupin
     */
    List<String> listMembership(String dn);

    /**
     * Přidá objekt do distribuční skupiny.
     *
     * @param dn distinguished name objektu
     * @param groupDn DN skupiny
     * @return úspěch operace
     */
    boolean addToGroup(String dn, String groupDn);

    /**
     * Odebere objekt ze všech distribučních skupin.
     *
     * @param dn distinguished name
     * @return úspěch operace
     */
    boolean removeFromAllGroups(String dn);
}
