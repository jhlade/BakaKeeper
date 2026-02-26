package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.*;

/**
 * Správa skupinového členství v Active Directory.
 * Extrahováno z BakaADAuthenticator – listMembership, listDirectMembers,
 * addObjectToGroup, removeObjectFromGroup, removeObjectFromAllGroups.
 *
 * @author Jan Hladěna
 */
class LdapGroupManager {

    /** dotazovací engine pro čtení objektů */
    private final LdapQueryEngine queryEngine;

    /** modifikátor atributů (pro member ADD/REMOVE) */
    private final LdapAttributeModifier attributeModifier;

    /**
     * Konstruktor.
     *
     * @param queryEngine dotazovací engine
     * @param attributeModifier modifikátor atributů
     */
    LdapGroupManager(LdapQueryEngine queryEngine, LdapAttributeModifier attributeModifier) {
        this.queryEngine = queryEngine;
        this.attributeModifier = attributeModifier;
    }

    /**
     * Výpis všech přímých skupin, kterých je daný objekt členem. V případě žádného členství
     * je vrácen prázdný seznam.
     *
     * @param objDN plné DN objektu
     * @return seznam DN skupin jako ArrayList řetězců
     */
    public ArrayList<String> listMembership(String objDN) {

        // výchozí seznam
        ArrayList<String> result = null;

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.DN.attribute(), objDN);

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.MEMBER_OF.attribute()
        };

        // pouze první položka
        Map<Integer, Map> rawResult = (Map<Integer, Map>) queryEngine.getObjectInfo(Settings.getInstance().getLDAP_base(), ldapQ, retAttributes);
        if (rawResult == null || rawResult.isEmpty()) {
            return new ArrayList<>(0);
        }
        Map info = rawResult.get(0);

        if (info != null) {

            if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) != null) {

                if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) instanceof ArrayList) {
                    // více skupin
                    result = new ArrayList<String>(((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).size());

                    Iterator<String> member = ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).iterator();
                    while (member.hasNext()) {
                        result.add(member.next());
                    }
                } else {
                    // pouze jedna skupina
                    result = new ArrayList<>(1);
                    result.add(info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()).toString());
                }

            } else {
                // inicializace - prázdný výsledek
                result = new ArrayList<>(0);
            }
        }

        // seřazení seznamu
        Collections.sort(result);
        return result;
    }

    /**
     * Výpis všech přímých členů skupiny definované pomocí plného DN.
     *
     * @param dn plné DN skupiny
     * @return seznam DN přímo podřízených objektů
     */
    public ArrayList<String> listDirectMembers(String dn) {
        ArrayList<String> result = new ArrayList<>(0);

        // dotaz na všechny přímé členy skupiny (uživatele i kontakty)
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.MEMBER_OF.attribute(), dn);

        // získat DN
        String[] retAttributes = {
                EBakaLDAPAttributes.DN.attribute(),
        };

        Map<Integer, Map<String, String>> query = (Map<Integer, Map<String, String>>) queryEngine.getObjectInfo(Settings.getInstance().getLDAP_base(), ldapQ, retAttributes);

        if (query != null && query.size() > 0) {
            result = new ArrayList<String>(query.size());

            for (int memberCounter = 0; memberCounter < query.size(); memberCounter++) {
                Map<String, String> member = query.get(memberCounter);
                if (member != null && member.get(EBakaLDAPAttributes.DN.attribute()) != null) {
                    result.add(member.get(EBakaLDAPAttributes.DN.attribute()));
                }
            }
        }

        return result;
    }

    /**
     * Přidání objektu jako člena do skupiny.
     *
     * @param objectDN plné DN objektu
     * @param destinationGroupDN plné jméno cílové skupiny
     * @return úspěch operace
     */
    public Boolean addObjectToGroup(String objectDN, String destinationGroupDN) {
        return attributeModifier.addAttribute(destinationGroupDN, EBakaLDAPAttributes.MEMBER, objectDN);
    }

    /**
     * Zobecněné přidání objektu do skupin v seznamu.
     *
     * @param objecDN plné DN objektu
     * @param destinationGroupDNs pole plných DN cílových skupin
     * @return úspěch operace
     */
    public Boolean addObjectToGroup(String objecDN, ArrayList<String> destinationGroupDNs) {
        if (destinationGroupDNs != null && destinationGroupDNs.size() > 0) {
            Boolean result = true;

            Iterator<String> destinationGroups = destinationGroupDNs.iterator();
            while (destinationGroups.hasNext()) {
                result &= addObjectToGroup(objecDN, destinationGroups.next());
            }

            return result;
        }

        return true;
    }

    /**
     * Odstranění objektu ze zadané skupiny.
     *
     * @param objectDN plné DN objektu
     * @param groupDN plné DN odstraňované skupiny
     * @return úspěch operace
     */
    public Boolean removeObjectFromGroup(String objectDN, String groupDN) {
        // z cílové skupiny odstraní objectDN z atributu member
        return attributeModifier.removeAttribute(groupDN, EBakaLDAPAttributes.MEMBER, objectDN);
    }

    /**
     * Odebrání objektu ze všech dosavadních skupin.
     *
     * @param objectDN plné DN objektu
     * @return úspěch operace
     */
    public Boolean removeObjectFromAllGroups(String objectDN) {
        ArrayList<String> groups = listMembership(objectDN);

        // žádné skupiny
        if (groups == null || groups.size() == 0) {
            return true;
        }

        Boolean result = true;

        // postupně odebrat všechny skupiny
        for (String grDN : groups) {
            result &= removeObjectFromGroup(objectDN, grDN);
        }

        return result;
    }
}
