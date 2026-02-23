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
     * Přesune objekt do jiné OU.
     *
     * @param dn distinguished name objektu
     * @param targetOu cílová OU
     * @return úspěch operace
     */
    boolean moveObject(String dn, String targetOu);

    /**
     * Ověří existenci objektu podle DN.
     *
     * @param dn distinguished name
     * @return objekt existuje
     */
    boolean checkDN(String dn);
}
