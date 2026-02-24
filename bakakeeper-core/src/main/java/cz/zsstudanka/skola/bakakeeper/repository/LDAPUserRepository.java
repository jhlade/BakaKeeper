package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

import java.util.List;

/**
 * Repository pro operace s uživatelskými účty v Active Directory (LDAP).
 *
 * @author Jan Hladěna
 */
public interface LDAPUserRepository {

    /**
     * Nalezne všechny žákovské účty v zadané OU (mimo absolventy).
     *
     * @param baseOu bázová OU pro vyhledávání
     * @param alumniOu OU absolventů (k vyloučení)
     * @return seznam žákovských záznamů
     */
    List<StudentRecord> findAllStudents(String baseOu, String alumniOu);

    /**
     * Nalezne všechny účty absolventů.
     *
     * @param alumniOu OU absolventů
     * @return seznam záznamů absolventů
     */
    List<StudentRecord> findAllAlumni(String alumniOu);

    /**
     * Nalezne žákovský účet podle UPN.
     *
     * @param baseOu bázová OU pro vyhledávání
     * @param upn userPrincipalName
     * @return záznam, nebo null
     */
    StudentRecord findByUPN(String baseOu, String upn);

    /**
     * Nalezne žákovský účet podle interního ID (extensionAttribute1).
     *
     * @param baseOu bázová OU pro vyhledávání
     * @param internalId interní kód
     * @return záznam, nebo null
     */
    StudentRecord findByInternalId(String baseOu, String internalId);

    /**
     * Vytvoří nový uživatelský účet v AD.
     *
     * @param cn common name
     * @param targetOu cílová OU
     * @param data atributy nového účtu
     */
    void createUser(String cn, String targetOu, DataLDAP data);

    /**
     * Nahradí hodnotu atributu u objektu.
     *
     * @param dn distinguished name objektu
     * @param attr atribut k nahrazení
     * @param value nová hodnota
     * @return úspěch operace
     */
    boolean updateAttribute(String dn, EBakaLDAPAttributes attr, String value);

    /**
     * Přidá hodnotu k multi-value atributu objektu (např. proxyAddresses).
     *
     * @param dn distinguished name objektu
     * @param attr atribut
     * @param value hodnota k přidání
     * @return úspěch operace
     */
    boolean addAttribute(String dn, EBakaLDAPAttributes attr, String value);

    /**
     * Odebere konkrétní hodnotu z multi-value atributu objektu.
     *
     * @param dn distinguished name objektu
     * @param attr atribut
     * @param oldValue hodnota k odebrání
     * @return úspěch operace
     */
    boolean removeAttribute(String dn, EBakaLDAPAttributes attr, String oldValue);

    /**
     * Přesune objekt do jiné OU.
     *
     * @param dn distinguished name objektu
     * @param targetOu cílová OU
     * @return úspěch operace
     */
    boolean moveObject(String dn, String targetOu);

    /**
     * Přesune objekt do jiné OU s volitelným vytvořením cílové OU.
     *
     * @param dn distinguished name objektu
     * @param targetOu cílová OU
     * @param createOuIfNotExists vytvořit OU, pokud neexistuje
     * @return úspěch operace
     */
    boolean moveObject(String dn, String targetOu, boolean createOuIfNotExists);

    /**
     * Ověří existenci objektu podle DN.
     *
     * @param dn distinguished name
     * @return objekt existuje
     */
    boolean checkDN(String dn);

    /**
     * Přidá objekt do skupiny.
     *
     * @param dn distinguished name objektu
     * @param groupDn DN cílové skupiny
     * @return úspěch operace
     */
    boolean addToGroup(String dn, String groupDn);

    /**
     * Odebere objekt ze všech skupin.
     *
     * @param dn distinguished name objektu
     * @return úspěch operace
     */
    boolean removeFromAllGroups(String dn);

    /**
     * Získá seznam členství objektu ve skupinách.
     *
     * @param dn distinguished name objektu
     * @return seznam DN skupin
     */
    List<String> listMembership(String dn);

    /**
     * Získá seznam přímých členů skupiny/DL.
     *
     * @param groupDn DN skupiny
     * @return seznam DN členů
     */
    List<String> listDirectMembers(String groupDn);

    /**
     * Odebere objekt z konkrétní skupiny.
     *
     * @param dn DN objektu
     * @param groupDn DN skupiny
     * @return úspěch operace
     */
    boolean removeFromGroup(String dn, String groupDn);
}
