package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import java.util.*;

/**
 * Konektor pro Active Directory.
 *
 * @author Jan Hladěna
 */
public class BakaADAuthenticator {

    /** maximální limit pro přejmenování objektu během přesunu */
    private static int MOVE_LIMIT = 99;

    /** singleton připojení k AD */
    private static BakaADAuthenticator instance = null;

    /** komunikační prostředí */
    private Hashtable env;
    /** přihlášení je aktivní */
    private Boolean authSucceeded = false;


    /** globální nastavení */
    private Settings settings = Settings.getInstance();

    /** doména principalu */
    private String domain;
    /** AD server */
    private String host;
    private String baseLDAP;

    /** získané informace */
    private Map<Integer, Map> authUser;


    /**
     * LDAP připojení jako singleton.
     *
     * @return instance LDAP spojení.
     */
    public static BakaADAuthenticator getInstance() {
        if (BakaADAuthenticator.instance == null) {
            BakaADAuthenticator.instance = new BakaADAuthenticator();
        }

        return BakaADAuthenticator.instance;
    }

    /**
     * Konstruktor s výchozím nastavením.
     */
    public BakaADAuthenticator() {
        this.domain = Settings.getInstance().getLDAP_domain();
        this.host = Settings.getInstance().getLDAP_host();
        authenticate(Settings.getInstance().getUser(), Settings.getInstance().getPass());
    }

    /**
     * Ověření připojení proti AD a vytvoření komunikačního prostředí.
     *
     * @param user Uživatelské jméno účtu s právy Domain Admin a vyššími
     * @param pass Uživatelské heslo daného účtu
     */
    private void authenticate(String user, String pass) {

        // inicializace prostředí
        this.env = new Hashtable();

        // FQDN LDAP serveru
        String fqdn;

        if (!host.toLowerCase().contains(domain.toLowerCase())) {
            fqdn = host + "." + domain;
        } else {
            fqdn = host;
        }

        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        if (Settings.getInstance().useSSL()) {
            env.put(Context.SECURITY_PROTOCOL, "ssl");
            // ověřování certifikátu ve vlasním úložišti klíčů
            env.put("java.naming.ldap.factory.socket", "cz.zsstudanka.skola.bakakeeper.components.BakaSSLSocketFactory");
            env.put(Context.PROVIDER_URL, "ldaps://" + fqdn + ":636/");
        } else {
            env.put(Context.PROVIDER_URL, "ldap://" + fqdn + "/");
        }
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, user + "@" + domain);
        env.put(Context.SECURITY_CREDENTIALS, pass);

        LdapContext ctxGC = null;

        try {
            // autentizace
            ctxGC = new InitialLdapContext(env, null);

            setAuthSucceeded(true);

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_VERBOSE, "Ověření proti Active Directory proběhlo úspěšně.");
            }

            // uložení lokálních informací
            HashMap<String, String> userLDAPquery = new HashMap<String, String>();

            userLDAPquery.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
            userLDAPquery.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
            userLDAPquery.put(EBakaLDAPAttributes.LOGIN.attribute(), user);

            this.authUser = getObjectInfo(Settings.getInstance().getLDAP_base(), userLDAPquery, new String[]{
                    EBakaLDAPAttributes.UPN.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute()
            });

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG, "Přihlášení do Active Directory pod účtem " + authUserInfo().get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()) + " (" + authUserInfo().get(EBakaLDAPAttributes.UPN.attribute()) + ").");
            }
        } catch (Exception e) {
            ReportManager.handleException("Ověření proti Active Directory se nezdařilo.", e);
            setAuthSucceeded(false);
        }

    }

    /**
     * Nastavení příznaku úspěšného připojení.
     *
     * @param success spojení navázáno
     */
    private void setAuthSucceeded(Boolean success) {
        this.authSucceeded = success;
    }

    /**
     * Zjištění příznaku úspěšného připojení.
     *
     * @return spojení navázáno
     */
    public Boolean isAuthenticated() {
        return this.authSucceeded;
    }

    /**
     * Informace o systémovém účtu
     *
     * @return uživatelksý účet použitý pro přihlášení
     */
    public Map authUserInfo() {
        if (!isAuthenticated()) return null;
        return this.authUser.get(0);
    }

    /**
     * Základní informace o objektu podle předaných atributů a základní cesty OU.
     *
     * @param baseOU základní OU pro prohledávání
     * @param findAttributes pole dotazů atribut/hodnota
     * @param retAttributes mapa záskaných atributů
     * @return mapa získaných dat
     */
    public Map getObjectInfo(String baseOU, HashMap<String, String> findAttributes, String[] retAttributes) {

        StringBuilder findAND = new StringBuilder();
        findAND.append("(&");

        Iterator attrIterator = findAttributes.entrySet().iterator();
        while (attrIterator.hasNext()) {
            findAND.append("(");

            Map.Entry ldapQ = (Map.Entry) attrIterator.next();

            findAND.append(ldapQ.getKey());
            findAND.append("=");
            findAND.append(ldapQ.getValue());

            findAND.append(")");
        }

        findAND.append(")");

        if (!isAuthenticated()) return null;

        // výsledek
        HashMap<Integer, Map> userInfo = new HashMap();
        Integer resNum = 0;

        // kontext
        LdapContext ctxGC = null;

        try {
            ctxGC = new InitialLdapContext(env, null);

            // LDAP výsledky a dotaz
            String returnedAtts[] = retAttributes;
            String searchFilter = findAND.toString();

            // řízení
            SearchControls searchCtls = new SearchControls();
            searchCtls.setReturningAttributes(returnedAtts);
            searchCtls.setSearchScope(SearchControls.SUBTREE_SCOPE);

            // provedení dotazu
            NamingEnumeration answer = ctxGC.search(baseOU, searchFilter, searchCtls);
            while (answer.hasMoreElements()) {

                SearchResult result = (SearchResult) answer.next();
                Attributes attrs = result.getAttributes();

                Map userDetails = new HashMap<String, Object>();

                if (attrs != null) {
                    NamingEnumeration ne = attrs.getAll();

                    while (ne.hasMore()) {

                        // kontrukce atributu
                        Attribute attr = (Attribute) ne.next();

                        // jeden atribut
                        if (attr.size() == 1) {
                            userDetails.put(attr.getID().toString(), attr.get());
                        } else {
                            // pole atributů
                            ArrayList<Object> retData = new ArrayList<>();
                            for (int ats = 0; ats < attr.size(); ats++) {
                                retData.add(attr.get(ats));
                            }

                            userDetails.put(attr.getID().toString(), retData);
                        }
                    }

                    ne.close();
                }

                userInfo.put(resNum, userDetails);

                resNum++;
            }

        } catch (NamingException e) {
            ReportManager.handleException("Hledaný objekt nebylo možné nalézt.", e);

            // prázdný výsledek - objekt nenalezen
            return null;
        }

        return userInfo;
    }

    /**
     * Informace o konkrétní skupině nebo distribučním seznamu.
     *
     * @param cn základní jméno skupiny
     * @param OU cílová OU
     * @return informace nebo null
     */
    public Map getGroupInfo(String cn, String OU) {

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.OC_GROUP.attribute(), EBakaLDAPAttributes.OC_GROUP.value());
        ldapQ.put(EBakaLDAPAttributes.CN.attribute(), cn);

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.DN.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.GT_GENERAL.attribute(),
                EBakaLDAPAttributes.MEMBER_OF.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.DESCRIPTION.attribute(),
                EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
                EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute()
        };

        return getObjectInfo(OU, ldapQ, retAttributes);
    }

    /**
     * Získání základních o uživateli na základě jeho UPN.
     *
     * @param upn UserPrincipalName
     * @param base bázová OU
     * @return data uživatele
     */
    public Map getUserInfo(String upn, String base) {

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
        ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
        ldapQ.put(EBakaLDAPAttributes.UPN.attribute(), upn);

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.DN.attribute(),
                EBakaLDAPAttributes.NAME_FIRST.attribute(),
                EBakaLDAPAttributes.NAME_LAST.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),

                EBakaLDAPAttributes.UPN.attribute(),
                EBakaLDAPAttributes.LOGIN.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),

                EBakaLDAPAttributes.PW_LASTSET.attribute(),
                EBakaLDAPAttributes.UAC.attribute(),

                EBakaLDAPAttributes.MEMBER_OF.attribute(),

                EBakaLDAPAttributes.EXT01.attribute(),
                EBakaLDAPAttributes.TITLE.attribute(),
                EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
                EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute()
        };

        return getObjectInfo(base, ldapQ, retAttributes);
    }

    /**
     * Provedení kontroly existence a obsahu OU.
     *
     * @param OU cílová OU
     * @return počet položek, nebo -1 v případě neexistence OU
     */
    public int checkOU(String OU) {
        HashMap<String, String> ldapQ = new HashMap<String, String>();

        // kontakty
        if (OU.contains(Settings.getInstance().getLDAP_baseContacts())) {
            ldapQ.put(EBakaLDAPAttributes.OC_CONTACT.attribute(), EBakaLDAPAttributes.OC_CONTACT.value());
        }

        // skupiny a seznamy
        if (
                OU.contains(Settings.getInstance().getLDAP_baseStudentGroups())
                || OU.contains(Settings.getInstance().getLDAP_baseDL())
        ) {
            ldapQ.put(EBakaLDAPAttributes.OC_GROUP.attribute(), EBakaLDAPAttributes.OC_GROUP.value());
        }

        // uživatelé
        if (
                OU.contains(Settings.getInstance().getLDAP_baseStudents())
                || OU.contains(Settings.getInstance().getLDAP_baseFaculty())
                || OU.contains(Settings.getInstance().getLDAP_baseAlumni())
        ) {
            ldapQ.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
            ldapQ.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
        }

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
        };

        Map<Integer, Map> result = getObjectInfo(OU, ldapQ, retAttributes);

        return (result == null) ? -1 : result.size();
    }

    /**
     * Ověření existence daného DN.
     *
     * @param dn plné DN testovaného objektu
     * @return existence objektu
     */
    public Boolean checkDN(String dn) {

        // dotaz
        String cn = BakaUtils.parseCN(dn);
        String ou = BakaUtils.parseBase(dn);

        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.CN.attribute(), cn);

        // atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.DN.attribute(),
        };

        // výsledek
        Map result = getObjectInfo(ou, ldapQ, retAttributes);

        if (result == null) {
            return false;
        }

        return (result.size() >= 1) ? true : false;
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

        Map info = getObjectInfo(Settings.getInstance().getLDAP_baseContacts(), ldapQ, retAttributes);

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
                addObjectToGroup(groupDN, destinationGroupDN);
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

        // kontext
        LdapContext ctxGC = null;

        try {
            ctxGC = new InitialLdapContext(env, null);

            if (objectClass.length == 2 && objectClass[1].contains(EBakaLDAPAttributes.OC_OU.value())) {
                // tvorba OU
                ctxGC.createSubcontext("ou=" + cnName + "," + targetOU, container);
            } else {
                // tvorba objektu
                ctxGC.createSubcontext("cn=" + cnName + "," + targetOU, container);
            }

        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné vytvořit požadovaný LDAP záznam (" + cnName + ").", e);
        }

    }

    /**
     * Smazání záznamu podle DN.
     *
     * @param dn plné DN objektu
     */
    private Boolean deleteRecord(String dn) {

        // kontext
        LdapContext ctxGC = null;

        try {
            ctxGC = new InitialLdapContext(env, null);
            ctxGC.destroySubcontext(dn);
            return true;
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné smazat záznam [" + dn + "].", e);
        }

        return false;
    }

    /**
     * Modifikace hodnoty atributu pro požadovaný objekt.
     *
     * @param modOp typ operace DirContext
     * @param dn plné jméno objektu
     * @param attribute název atributu
     * @param value nová hodnota
     */
    private boolean modifyAttribute(int modOp, String dn, EBakaLDAPAttributes attribute, String value) {

        if (Settings.getInstance().debugMode()) {
            ReportManager.log(EBakaLogType.LOG_LDAP, "Operace typu [" + modOp + "] nad objektem: [" + dn + "]. Atribut: [" + attribute.attribute().toString() + "], cílová hodnota: [" + value + "].");
        }

        LdapContext bakaContext = null;

        try {
            bakaContext = new InitialLdapContext(env, null);

            ModificationItem mod[] = new ModificationItem[1];

            // heslo
            if (attribute.equals(EBakaLDAPAttributes.PW_UNICODE)) {
                String password = "\"" + value + "\"";
                try {
                    byte[] unicodePwd = password.getBytes("UTF-16LE");
                    mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), unicodePwd));
                } catch (Exception e) {
                    ReportManager.handleException("Nebylo možné nastavit heslo.", e);
                }
            } else {
                // vše ostatní
                mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), value));
            }

            bakaContext.modifyAttributes(dn, mod);
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné modifikovat atribut objektu.", e);
            return false;
        }

        return true;
    }

    /**
     * Smazání konkrétního atributu z objektu.
     *
     * @param dn plné DN objektu
     * @param attributes požadovaný atribut
     * @param oldValue stará hodnota atributu
     * @return úspěch operace
     */
    public Boolean removeAttribute(String dn, EBakaLDAPAttributes attributes, String oldValue) {
        return modifyAttribute(DirContext.REMOVE_ATTRIBUTE, dn, attributes, oldValue);
    }

    /**
     * Přidání nového atributu k objektu.
     *
     * @param dn plné DN objektu
     * @param attribute požadovaný atribut
     * @param value nová hodnota atributu
     * @return úspěch operace
     */
    public Boolean addAttribute(String dn, EBakaLDAPAttributes attribute, String value) {
        return modifyAttribute(DirContext.ADD_ATTRIBUTE, dn, attribute, value);
    }

    /**
     * Změna hodnoty atributu objektu.
     *
     * @param dn plné DN objektu
     * @param attribute požadovaný atribut k nahrazení
     * @param newValue nová hodnota
     * @return úspěch operace
     */
    public Boolean replaceAttribute(String dn, EBakaLDAPAttributes attribute, String newValue) {
        if (modifyAttribute(DirContext.REPLACE_ATTRIBUTE, dn, attribute, newValue)) {
            return true;
        } else {
            return modifyAttribute(DirContext.ADD_ATTRIBUTE, dn, attribute, newValue);
        }
    }

    /**
     * Nastavení atributu skupiny. V případě existence bude atribut nahrazen.
     *
     * @param OU prohledávaná OU
     * @param groupCN CN skupiny
     * @param attribute atribut
     * @param value hodnota atributu
     * @return úspěch operace
     */
    public Boolean setGroupInfo(String OU, String groupCN, EBakaLDAPAttributes attribute, String value) {
        Map<Integer, Map<String, Object>> group = getGroupInfo(groupCN, OU);

        if (group.get(0).containsKey(attribute.attribute())) {
            return replaceAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        } else {
            return addAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        }
    }

    /**
     * Přesun objektu do požadované OU.
     *
     * @param objectDN plné DN objektu
     * @param ouName cílová OU
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName) {
        return moveObject(objectDN, ouName, false, false);
    }

    /**
     * Přesun objektu do požadované OU. Pokud cílová OU neexistuje a je
     * nastaveno <code>createNewOUifNotExists</code>, bude vytvořena.
     *
     * @param objectDN plné DN objektu
     * @param ouName cílová OU
     * @param createNewOUifNotExists vytvořit novou OU, pokud neexistuje
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName, Boolean createNewOUifNotExists) {
        return moveObject(objectDN, ouName, createNewOUifNotExists, false);
    }

    /**
     * Přesun objektu do požadované organizační jednotky.
     * Pokud je nastaveno <code>createNewOUifNotExists</code> a cílová OU neexituje,
     * bude vytvořena.
     * Pokud je nastaveno <code>renameObject</code>, objekt bude bude v případě neúspěchu
     * přejmenován.
     *
     * @param objectDN plné DN objektu
     * @param ouName plná cesta cílové OU
     * @param createNewOUifNotExists vytvořit cílovou OU, pokud neexistuje
     * @param renameObject přejmenovat přesunovaný objekt, pokud v cíli již jiný objekt s požadovaným názvem existuje
     * @return úspěch operace
     */
    public Boolean moveObject(String objectDN, String ouName, Boolean createNewOUifNotExists, Boolean renameObject) {
        // kontrola existence cílové ou
        if (!createNewOUifNotExists && checkOU(ouName) == -1) {

            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Cílová organizační jednotka pro přesun objektu neexistuje.");
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Nebylo možné přesunout objekt [" + objectDN + "] do umístění [" + ouName + "].");
            }

            return false;
        }

        // nový název objektu
        String objCN = BakaUtils.parseCN(objectDN);
        String newObjectDN = "CN=" + objCN + "," + ouName;

        // prvotní kontrola existence cílového objektu
        if (checkDN(newObjectDN) && !renameObject) {
            if (Settings.getInstance().beVerbose()) {
                ReportManager.log(EBakaLogType.LOG_ERR, "Cílový název objektu již existuje, přesun nebude proveden.");
            }

            if (Settings.getInstance().debugMode()) {
                ReportManager.log(EBakaLogType.LOG_ERR_DEBUG, "Nebylo možné přesunout objekt [" + objectDN + "] do umístění [" + ouName + "].");
            }

            return false;
        }

        int moveAttempt = 0;
        Boolean dnOccupied;

        do {
            dnOccupied = checkDN(newObjectDN);
            moveAttempt ++;

            if (dnOccupied) {

                if (Settings.getInstance().beVerbose()) {
                    ReportManager.log("Název přesunovaného objektu v cíli již exituje, bude vygenerován nový.");
                }

                newObjectDN = BakaUtils.nextDN(newObjectDN);

                if (Settings.getInstance().debugMode()) {
                    ReportManager.log(EBakaLogType.LOG_LDAP, "Byl vygenerován nový název [" + newObjectDN + "].");
                }
            }

        } while (dnOccupied && moveAttempt <= MOVE_LIMIT);

        if (moveAttempt >= MOVE_LIMIT) {
            ReportManager.log(EBakaLogType.LOG_ERR, "Byl překročen maximální limit pro přejmenování LDAP objektu.");
        }

        // vytvoření nové organziační jednotky
        if (createNewOUifNotExists && checkOU(ouName) == -1) {
            // vlastní název
            String cn = BakaUtils.parseLastOU(ouName);
            // cílová cesta
            String base = BakaUtils.parseBase(ouName);
            // vytvoření
            this.createOU(cn, base);
        }

        // kontext
        LdapContext ctxOM = null;

        // provedení přesunu
        try {
            ctxOM = new InitialLdapContext(env, null);
            ctxOM.rename(objectDN, newObjectDN);
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné přesunout objekt.", e);
        }

        // kontrola existence objektu po přesunutí
        return checkDN(newObjectDN);
    }

    /**
     * Výpis všech přímých skupin, kterých je daný objekt členem. V případě žádného členství
     * je vrácen prázdný seznam.
     *
     * @param objDN plné DN objektu
     * @return seznam DN skupin jako ArrayList řětězců
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
        Map info = ((Map<Integer, Map>) getObjectInfo(Settings.getInstance().getLDAP_base(), ldapQ, retAttributes)).get(0);

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
     * Přidání objektu jako člena do skupiny.
     *
     * @param objectDN plné DN objektu
     * @param destinationGroupDN plné jméno cílové skupiny
     * @return úspěch operace
     */
    public Boolean addObjectToGroup(String objectDN, String destinationGroupDN) {
        return addAttribute(destinationGroupDN, EBakaLDAPAttributes.MEMBER, objectDN);
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
        return removeAttribute(groupDN, EBakaLDAPAttributes.MEMBER, objectDN);
    }

    /**
     * Odebrání objektu ze všech dosavadních skupin.
     *
     * @param objectDN
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