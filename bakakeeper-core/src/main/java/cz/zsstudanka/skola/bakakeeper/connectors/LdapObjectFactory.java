package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Továrna pro vytváření a mazání LDAP objektů v Active Directory.
 * Extrahováno z BakaADAuthenticator – metody pro tvorbu OU, uživatelů,
 * kontaktů, skupin a obecných záznamů.
 *
 * @author Jan Hladěna
 */
class LdapObjectFactory {

    /** továrna na LDAP spojení */
    private final LdapConnectionFactory connectionFactory;

    /** dotazovací engine pro vyhledávání objektů */
    private final LdapQueryEngine queryEngine;

    /** správce skupin (může být nastaven později přes setter) */
    private LdapGroupManager groupManager;

    /**
     * Konstruktor.
     *
     * @param connectionFactory továrna na LDAP spojení
     * @param queryEngine dotazovací engine
     * @param groupManager správce skupin (může být null, nastavit později přes setter)
     */
    LdapObjectFactory(LdapConnectionFactory connectionFactory, LdapQueryEngine queryEngine, LdapGroupManager groupManager) {
        this.connectionFactory = connectionFactory;
        this.queryEngine = queryEngine;
        this.groupManager = groupManager;
    }

    /**
     * Nastaví správce skupin (pro případ cyklické závislosti).
     *
     * @param groupManager správce skupin
     */
    void setGroupManager(LdapGroupManager groupManager) {
        this.groupManager = groupManager;
    }

    /**
     * Vytvoření nové organizační jednotky v požadované cestě.
     *
     * @param name název nové OU
     * @param base výchozí umístění
     */
    public void createOU(String name, String base) {

        String[] objClass = {
                EBakaLDAPAttributes.OC_TOP.value(),
                EBakaLDAPAttributes.OC_OU.value()
        };

        createRecord(name, base, objClass, null);
    }

    /**
     * Vytvoření nového uživatele v Active Directory.
     *
     * @param cn základní jméno uživatele
     * @param targetOU cílová organizační jednotka
     * @param data parametry uživatele
     */
    public void createNewUser(String cn, String targetOU, DataLDAP data) {

        // třída objektu - uživatel Active Directory
        String[] objectClasses = {
            EBakaLDAPAttributes.OC_TOP.value(),
            EBakaLDAPAttributes.OC_PERSON.value(),
            EBakaLDAPAttributes.OC_ORG_PERSON.value(),
            EBakaLDAPAttributes.OC_USER.value()
        };

        // data objektu
        Attribute[] attrs = new Attribute[data.size()];
        int a = 0;

        Iterator<String> dataIterator = data.keySet().iterator();
        while (dataIterator.hasNext()) {
            String attrKey = dataIterator.next();

            // heslo
            if (attrKey.equals(EBakaLDAPAttributes.PW_UNICODE.attribute())) {
                String password = "\"" + data.get(attrKey) + "\"";
                try {
                    byte[] unicodePwd = password.getBytes("UTF-16LE");
                    attrs[a] = new BasicAttribute(attrKey, unicodePwd);
                    a++;
                } catch (Exception e) {
                    ReportManager.handleException("Nebylo možné vytvořit uživatelské heslo.", e);
                }
                continue;
            }

            // vše ostatní
            attrs[a] = new BasicAttribute(attrKey, data.get(attrKey));
            // inkrementace
            a++;
        }

        // vytvoření záznamu - uživatele
        createRecord(cn, targetOU, objectClasses, attrs);
    }

    /**
     * Vytvoření nového kontaktu v Acrtive Directory.
     *
     * @param cn základní jméno kontaktu
     * @param data parametry kontaktu
     */
    public void createNewContact(String cn, DataLDAP data) {

        // třída objektu - kontakt
        String[] objectClasses = {
                EBakaLDAPAttributes.OC_TOP.value(),
                EBakaLDAPAttributes.OC_PERSON.value(),
                EBakaLDAPAttributes.OC_ORG_PERSON.value(),
                EBakaLDAPAttributes.OC_CONTACT.value()
        };

        // data objektu
        Attribute[] attrs = new Attribute[data.size()];
        int a = 0;

        Boolean inDomain = (data.containsKey(EBakaLDAPAttributes.MAIL.attribute()) && data.get(EBakaLDAPAttributes.MAIL.attribute()).toString().contains(Settings.getInstance().getMailDomain()));

        Iterator<String> dataIterator = data.keySet().iterator();
        while (dataIterator.hasNext()) {
            String attrKey = dataIterator.next();

            Object finalData = data.get(attrKey);

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_LDAP, "Mapuje se " + attrKey + " = [" + finalData + "]");
            }

            // vyžadováno ověření
            if (inDomain && attrKey.equals(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute())) {
                finalData = EBakaLDAPAttributes.BK_LITERAL_FALSE.value();
            }

            // skrytí v GAL
            if (inDomain && attrKey.equals(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute())) {
                finalData = EBakaLDAPAttributes.BK_LITERAL_FALSE.value();
            }

            attrs[a] = new BasicAttribute(attrKey, finalData);
            a++;
        }

        // OU s kontakty
        String targetOU = Settings.getInstance().getLDAP_baseContacts();

        // vytvoření záznamu - kontaktu
        createRecord(cn, targetOU, objectClasses, attrs);
    }

    /**
     * Bezpečné smazání kontaktu.
     *
     * @param dn plné jméno kontaktu
     * @return úspěch operace
     */
    public Boolean deleteContact(String dn) {

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<>();
        ldapQ.put(EBakaLDAPAttributes.OC_CONTACT.attribute(), EBakaLDAPAttributes.OC_CONTACT.value());
        ldapQ.put(EBakaLDAPAttributes.DN.attribute(), dn);

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.DN.attribute(),
        };

        Map info = queryEngine.getObjectInfo(Settings.getInstance().getLDAP_baseContacts(), ldapQ, retAttributes);

        // kontakt nenalezen
        if (info.size() != 1) {
            return false;
        }

        return deleteRecord(dn);
    }

    /**
     * Vytvoření skupiny se zabezpečením.
     *
     * @param cn common name skupiny
     * @param targetOU cílová OU
     * @param memberOf pole přímých nadřazených skupin
     * @param data
     */
    public void createSecurityGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {
        data.put(EBakaLDAPAttributes.GT_SECURITY.attribute(), EBakaLDAPAttributes.GT_SECURITY.value());
        createGroup(cn, targetOU, memberOf, data);
    }

    /**
     * Vytvoření distribuční skupiny.
     *
     * @param cn common name distribučnáho seznamu
     * @param targetOU cílová OU
     * @param memberOf pole přímých nadřazených skupin
     * @param data
     */
    public void createDistributionGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {
        data.put(EBakaLDAPAttributes.GT_DISTRIBUTION.attribute(), EBakaLDAPAttributes.GT_DISTRIBUTION.value());
        createGroup(cn, targetOU, memberOf, data);
    }

    /**
     * Vytvoření nové obecné skupiny. V datech musí být specifikován atribut groupType.
     *
     * @param cn common name skupiny
     * @param targetOU cílová organizační jednotka
     * @param memberOf pole dn přímých nadřazených skupin (může být prázdné nebo null)
     * @param data mapa dalších požadovaných atributů
     */
    public void createGroup(String cn, String targetOU, String[] memberOf, HashMap<String, String> data) {

        String[] objClasses = {
                EBakaLDAPAttributes.OC_TOP.value(),
                EBakaLDAPAttributes.OC_GROUP.value(),
        };

        Attribute[] attrs = new Attribute[data.size()];
        int a = 0;
        for (Map.Entry<String, String> entry : data.entrySet()) {
            attrs[a] = new BasicAttribute(entry.getKey(), entry.getValue());
            a++;
        }

        // vytvoření objektu skupiny
        createRecord(cn, targetOU, objClasses, attrs);

        // modifikace nadřazených položek
        if (memberOf == null || memberOf.length == 0)
        {
            return;
        }

        // plné DN nově vytvořené skupiny
        String groupDN = "cn=" + cn + "," + targetOU;

        for (int mof = 0; mof < memberOf.length; mof++) {
            // cílová destinace
            String destinationGroupDN = memberOf[mof];

            if (destinationGroupDN != null) {
                groupManager.addObjectToGroup(groupDN, destinationGroupDN);
            }
        }

    }

    /**
     * Vytvoření nového obecného objektu v Active Directory.
     *
     * @param cnName základní jméno jednotky
     * @param targetOU cílová organizační jednotka
     * @param objectClass pole tříd objektu
     * @param attributes pole atributů
     */
    private void createRecord(String cnName, String targetOU, String[] objectClass, Attribute[] attributes) {

        // kontejner atributů
        Attributes container = new BasicAttributes();

        // CN
        if (cnName != null) {
            Attribute cn = new BasicAttribute(EBakaLDAPAttributes.CN.attribute(), cnName);
            container.put(cn);
        }

        // třídy objektu (top, group, ...)
        Attribute objClasses = new BasicAttribute(EBakaLDAPAttributes.OC_GENERAL.attribute());
        for (String objc: objectClass) {
            objClasses.add(objc);
        }
        container.put(objClasses);

        // ostatní atributy
        if (attributes != null) {

            for (Attribute attr : attributes) {
                container.put(attr);
            }

        }

        // kontext – try-finally pro správné uzavření
        LdapContext ctxGC = null;
        try {
            ctxGC = connectionFactory.createContext();

            if (objectClass.length == 2 && objectClass[1].contains(EBakaLDAPAttributes.OC_OU.value())) {
                // tvorba OU
                ctxGC.createSubcontext("ou=" + cnName + "," + targetOU, container);
            } else {
                // tvorba objektu
                ctxGC.createSubcontext("cn=" + cnName + "," + targetOU, container);
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit požadovaný LDAP záznam (" + cnName + ").", e);
        } finally {
            if (ctxGC != null) { try { ctxGC.close(); } catch (NamingException ignored) {} }
        }

    }

    /**
     * Smazání záznamu podle DN.
     *
     * @param dn plné DN objektu
     */
    private Boolean deleteRecord(String dn) {

        // kontext – try-finally pro správné uzavření
        LdapContext ctxGC = null;
        try {
            ctxGC = connectionFactory.createContext();
            ctxGC.destroySubcontext(dn);
            return true;
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné smazat záznam [" + dn + "].", e);
        } finally {
            if (ctxGC != null) { try { ctxGC.close(); } catch (NamingException ignored) {} }
        }

        return false;
    }

}
