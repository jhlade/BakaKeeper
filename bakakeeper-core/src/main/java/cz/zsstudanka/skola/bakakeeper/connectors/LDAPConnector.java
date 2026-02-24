package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Rozhraní pro LDAP/AD konektivitu. Umožňuje testovatelnost repository vrstvy
 * bez závislosti na konkrétní implementaci (BakaADAuthenticator).
 *
 * @author Jan Hladěna
 */
public interface LDAPConnector {

    /** Dotaz na objekty v AD. */
    @SuppressWarnings("rawtypes")
    Map getObjectInfo(String baseOU, HashMap<String, String> findAttributes, String[] retAttributes);

    /** Ověření existence DN. */
    Boolean checkDN(String dn);

    /** Vytvoření nového uživatelského účtu. */
    void createNewUser(String cn, String targetOU, DataLDAP data);

    /** Vytvoření nového kontaktu. */
    void createNewContact(String cn, DataLDAP data);

    /** Smazání kontaktu. */
    Boolean deleteContact(String dn);

    /** Nahrazení atributu objektu. */
    Boolean replaceAttribute(String dn, EBakaLDAPAttributes attribute, String newValue);

    /** Přidání hodnoty k multi-value atributu (např. proxyAddresses). */
    Boolean addAttribute(String dn, EBakaLDAPAttributes attribute, String value);

    /** Odebrání konkrétní hodnoty z multi-value atributu. */
    Boolean removeAttribute(String dn, EBakaLDAPAttributes attribute, String oldValue);

    /** Přesun objektu do jiné OU. */
    Boolean moveObject(String objectDN, String ouName);

    /** Přesun objektu do jiné OU s volitelným vytvořením cílové OU. */
    Boolean moveObject(String objectDN, String ouName, Boolean createOuIfNotExists);

    /** Seznam členství objektu ve skupinách. */
    ArrayList<String> listMembership(String objDN);

    /** Přidání objektu do skupiny. */
    Boolean addObjectToGroup(String objectDN, String destinationGroupDN);

    /** Odebrání objektu ze všech skupin. */
    Boolean removeObjectFromAllGroups(String objectDN);

    /** Seznam přímých členů skupiny/DL. */
    ArrayList<String> listDirectMembers(String groupDN);

    /** Odebrání objektu z konkrétní skupiny. */
    Boolean removeObjectFromGroup(String objectDN, String groupDN);

    // --- Správa struktury OU a skupin ---

    /** Kontrola existence OU. Vrací počet položek v OU, nebo -1 pokud neexistuje. */
    int checkOU(String ou);

    /** Vytvoření nové organizační jednotky. */
    void createOU(String name, String base);

    /** Získání informací o skupině (DN, displayName, groupType, memberOf, mail atd.). */
    @SuppressWarnings("rawtypes")
    Map getGroupInfo(String cn, String ou);

    /** Vytvoření skupiny s atributy a případným členstvím v nadřazených skupinách. */
    void createGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data);

    /** Nastavení/oprava atributu existující skupiny. */
    Boolean setGroupInfo(String ou, String groupCN, EBakaLDAPAttributes attribute, String value);
}
