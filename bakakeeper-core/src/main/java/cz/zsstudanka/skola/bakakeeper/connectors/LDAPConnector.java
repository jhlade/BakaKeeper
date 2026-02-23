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

    /** Přesun objektu do jiné OU. */
    Boolean moveObject(String objectDN, String ouName);

    /** Seznam členství objektu ve skupinách. */
    ArrayList<String> listMembership(String objDN);

    /** Přidání objektu do skupiny. */
    Boolean addObjectToGroup(String objectDN, String destinationGroupDN);

    /** Odebrání objektu ze všech skupin. */
    Boolean removeObjectFromAllGroups(String objectDN);
}
