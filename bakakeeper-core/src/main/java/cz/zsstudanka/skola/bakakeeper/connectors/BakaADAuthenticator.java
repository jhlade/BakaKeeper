package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Konektor pro Active Directory – fasáda delegující na specializované helper třídy.
 *
 * <p>Veškerá logika byla extrahována do:
 * <ul>
 *   <li>{@link LdapConnectionFactory} – životní cyklus spojení, SSL, autentizace</li>
 *   <li>{@link LdapQueryEngine} – dotazy a čtení objektů (s pagingem)</li>
 *   <li>{@link LdapObjectFactory} – vytváření a mazání objektů (OU, uživatel, kontakt, skupina)</li>
 *   <li>{@link LdapAttributeModifier} – modifikace atributů (UAC/SDDL/heslo)</li>
 *   <li>{@link LdapObjectMover} – přesuny a přejmenování objektů</li>
 *   <li>{@link LdapGroupManager} – skupiny a členství</li>
 * </ul>
 *
 * @author Jan Hladěna
 */
public class BakaADAuthenticator implements LDAPConnector {

    /** singleton připojení k AD */
    private static BakaADAuthenticator instance = null;

    // --- helper třídy ---
    private final LdapConnectionFactory connectionFactory;
    private final LdapQueryEngine queryEngine;
    private final LdapAttributeModifier attributeModifier;
    private final LdapGroupManager groupManager;
    private final LdapObjectFactory objectFactory;
    private final LdapObjectMover objectMover;

    /**
     * LDAP připojení jako singleton.
     *
     * @return instance LDAP spojení
     */
    public static BakaADAuthenticator getInstance() {
        if (BakaADAuthenticator.instance == null) {
            BakaADAuthenticator.instance = new BakaADAuthenticator();
        }

        return BakaADAuthenticator.instance;
    }

    /**
     * Konstruktor – vytvoří a propojí všechny helper třídy, provede autentizaci.
     */
    public BakaADAuthenticator() {
        // 1. připojení
        this.connectionFactory = new LdapConnectionFactory();

        // 2. dotazovací engine
        this.queryEngine = new LdapQueryEngine(connectionFactory);

        // 3. propojení zpětné reference (authenticate potřebuje queryEngine pro vyhledání auth usera)
        connectionFactory.setQueryEngine(queryEngine);

        // 4. autentizace
        connectionFactory.authenticate();

        // 5. modifikátor atributů
        this.attributeModifier = new LdapAttributeModifier(connectionFactory, queryEngine);

        // 6. správce skupin
        this.groupManager = new LdapGroupManager(queryEngine, attributeModifier);

        // 7. továrna objektů
        this.objectFactory = new LdapObjectFactory(connectionFactory, queryEngine, groupManager);

        // 8. správce přesunů
        this.objectMover = new LdapObjectMover(connectionFactory, queryEngine, objectFactory);
    }

    // === Stav připojení ===

    /**
     * Zjištění příznaku úspěšného připojení.
     *
     * @return spojení navázáno
     */
    public Boolean isAuthenticated() {
        return connectionFactory.isAuthenticated();
    }

    /**
     * Informace o systémovém účtu.
     *
     * @return uživatelský účet použitý pro přihlášení
     */
    public Map authUserInfo() {
        return connectionFactory.authUserInfo();
    }

    // === Dotazy (LdapQueryEngine) ===

    @Override
    public Map getObjectInfo(String baseOU, HashMap<String, String> findAttributes, String[] retAttributes) {
        return queryEngine.getObjectInfo(baseOU, findAttributes, retAttributes);
    }

    /**
     * Informace o LDAP serveru z RootDSE.
     *
     * @return název, verze, AD funkcionalita
     */
    public Map<String, String> getServerInfo() {
        return queryEngine.getServerInfo();
    }

    @Override
    public Map getGroupInfo(String cn, String ou) {
        return queryEngine.getGroupInfo(cn, ou);
    }

    /**
     * Získání základních informací o uživateli na základě jeho UPN.
     *
     * @param upn UserPrincipalName
     * @param base bázová OU
     * @return data uživatele
     */
    public Map getUserInfo(String upn, String base) {
        return queryEngine.getUserInfo(upn, base);
    }

    @Override
    public int checkOU(String ou) {
        return queryEngine.checkOU(ou);
    }

    @Override
    public Boolean checkDN(String dn) {
        return queryEngine.checkDN(dn);
    }

    // === Tvorba a mazání objektů (LdapObjectFactory) ===

    @Override
    public void createOU(String name, String base) {
        objectFactory.createOU(name, base);
    }

    @Override
    public void createNewUser(String cn, String targetOU, DataLDAP data) {
        objectFactory.createNewUser(cn, targetOU, data);
    }

    @Override
    public void createNewContact(String cn, DataLDAP data) {
        objectFactory.createNewContact(cn, data);
    }

    @Override
    public Boolean deleteContact(String dn) {
        return objectFactory.deleteContact(dn);
    }

    /**
     * Vytvoření skupiny se zabezpečením.
     *
     * @param cn common name skupiny
     * @param targetOU cílová OU
     * @param memberOf pole přímých nadřazených skupin
     * @param data atributy skupiny
     */
    public void createSecurityGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {
        objectFactory.createSecurityGroup(cn, targetOU, memberOf, data);
    }

    /**
     * Vytvoření distribuční skupiny.
     *
     * @param cn common name distribučního seznamu
     * @param targetOU cílová OU
     * @param memberOf pole přímých nadřazených skupin
     * @param data atributy skupiny
     */
    public void createDistributionGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {
        objectFactory.createDistributionGroup(cn, targetOU, memberOf, data);
    }

    @Override
    public void createGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {
        objectFactory.createGroup(cn, targetOU, memberOf, data);
    }

    // === Modifikace atributů (LdapAttributeModifier) ===

    @Override
    public Boolean replaceAttribute(String dn, EBakaLDAPAttributes attribute, String newValue) {
        return attributeModifier.replaceAttribute(dn, attribute, newValue);
    }

    @Override
    public Boolean addAttribute(String dn, EBakaLDAPAttributes attribute, String value) {
        return attributeModifier.addAttribute(dn, attribute, value);
    }

    @Override
    public Boolean removeAttribute(String dn, EBakaLDAPAttributes attribute, String oldValue) {
        return attributeModifier.removeAttribute(dn, attribute, oldValue);
    }

    @Override
    public Boolean setGroupInfo(String ou, String groupCN, EBakaLDAPAttributes attribute, String value) {
        return attributeModifier.setGroupInfo(ou, groupCN, attribute, value);
    }

    // === Přesuny a přejmenování (LdapObjectMover) ===

    @Override
    public String renameObject(String objectDN, String newCn) {
        return objectMover.renameObject(objectDN, newCn);
    }

    @Override
    public Boolean moveObject(String objectDN, String ouName) {
        return objectMover.moveObject(objectDN, ouName);
    }

    @Override
    public Boolean moveObject(String objectDN, String ouName, Boolean createOuIfNotExists) {
        return objectMover.moveObject(objectDN, ouName, createOuIfNotExists);
    }

    /**
     * Přesun objektu s volitelným přejmenováním.
     *
     * @param objectDN plné DN objektu
     * @param ouName plná cesta cílové OU
     * @param createNewOUifNotExists vytvořit cílovou OU, pokud neexistuje
     * @param renameObject přejmenovat objekt, pokud v cíli již jiný existuje
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName, Boolean createNewOUifNotExists, Boolean renameObject) {
        return objectMover.moveObject(objectDN, ouName, createNewOUifNotExists, renameObject);
    }

    // === Skupiny a členství (LdapGroupManager) ===

    @Override
    public ArrayList<String> listMembership(String objDN) {
        return groupManager.listMembership(objDN);
    }

    @Override
    public ArrayList<String> listDirectMembers(String groupDN) {
        return groupManager.listDirectMembers(groupDN);
    }

    @Override
    public Boolean addObjectToGroup(String objectDN, String destinationGroupDN) {
        return groupManager.addObjectToGroup(objectDN, destinationGroupDN);
    }

    /**
     * Zobecněné přidání objektu do skupin v seznamu.
     *
     * @param objecDN plné DN objektu
     * @param destinationGroupDNs pole plných DN cílových skupin
     * @return úspěch operace
     */
    public Boolean addObjectToGroup(String objecDN, ArrayList<String> destinationGroupDNs) {
        return groupManager.addObjectToGroup(objecDN, destinationGroupDNs);
    }

    @Override
    public Boolean removeObjectFromGroup(String objectDN, String groupDN) {
        return groupManager.removeObjectFromGroup(objectDN, groupDN);
    }

    @Override
    public Boolean removeObjectFromAllGroups(String objectDN) {
        return groupManager.removeObjectFromAllGroups(objectDN);
    }
}
