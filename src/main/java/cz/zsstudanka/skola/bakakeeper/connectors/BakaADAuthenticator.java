package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.*;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

/**
 * Konektor pro Active Directory.
 *
 * @author Jan Hladěna
 */
public class BakaADAuthenticator {

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
    private Map authUser;


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
     *
     */
    public BakaADAuthenticator() {
        try {
            this.domain = Settings.getInstance().getLDAP_domain();
            this.host = Settings.getInstance().getLDAP_host();

            authenticate(Settings.getInstance().getUser(), Settings.getInstance().getPass());
        } catch (Exception e) {
            // TODO log
            // nelze přistupovat k nastavení
        }
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
                System.out.println("[ INFO ] Ověření proti Active Directory proběhlo úspěšně.");
            }

            // uložení lokálních informací
            HashMap<String, String> userLDAPquery = new HashMap<String, String>();

            userLDAPquery.put(EBakaLDAPAttributes.ST_USER.attribute(), EBakaLDAPAttributes.ST_USER.value());
            userLDAPquery.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
            userLDAPquery.put(EBakaLDAPAttributes.LOGIN.attribute(), user);

            this.authUser = getInfoInOU(Settings.getInstance().getLDAP_base(), userLDAPquery, new String[]{
                    EBakaLDAPAttributes.UPN.attribute(),
                    EBakaLDAPAttributes.NAME_DISPLAY.attribute()
            });

            if (Settings.getInstance().debugMode()) {
                System.out.println("[ DEBUG ] Přihlášení do Active Directory pod účtem " + authUserInfo().get(EBakaLDAPAttributes.NAME_DISPLAY.attribute()) + " (" + authUserInfo().get(EBakaLDAPAttributes.UPN.attribute()) + ").");
            }

        } catch (Exception e) {

            System.err.println("[ CHYBA ] Ověření proti Active Directory se nezdařilo.");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
            }

            if (Settings.getInstance().debugMode()) {
                e.printStackTrace(System.err);
            }

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
        return this.authUser;
    }

    /**
     * Základní informace o jednom kontaktu na základě e-mailové adresy.
     *
     * @param mail
     * @return
     */
    public Map<Integer, Map> getContactInfo(String mail) {

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<String, String>();

        ldapQ.put(EBakaLDAPAttributes.OC_CONTACT.attribute(), EBakaLDAPAttributes.OC_CONTACT.value());
        //ldapQ.put("dn", "dn=" + dn + Settings.getInstance().getLDAP_baseKontakty());
        ldapQ.put(EBakaLDAPAttributes.MAIL.attribute(), mail);

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.NAME_FIRST.attribute(),
                EBakaLDAPAttributes.NAME_LAST.attribute(),
                EBakaLDAPAttributes.NAME_DISPLAY.attribute(),
                EBakaLDAPAttributes.MAIL.attribute(),
                EBakaLDAPAttributes.MOBILE.attribute(),
                EBakaLDAPAttributes.DN.attribute(),
                EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(),
                EBakaLDAPAttributes.MEMBER_OF.attribute(),
                EBakaLDAPAttributes.EXT01.attribute(),
        };

        return ((Map<Integer, Map>) getInfoInOU(Settings.getInstance().getLDAP_baseContacts(), ldapQ, retAttributes)).get(0);
    }

    /**
     * Základní informace o objektu podle předaných atributů a základní cesty OU.
     *
     * @param baseOU základní OU pro prohledávání
     * @param findAttributes pole dotazů atribut/hodnota
     * @param retAttributes mapa záskaných atributů
     * @return mapa získaných dat
     */
    public Map getInfoInOU(String baseOU, HashMap<String, String> findAttributes, String[] retAttributes) {

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

            //String searchFilter = "(&(sAMAccountType=805306368)(objectClass=user)(sAMAccountName=" + sAMAaccountName + "))";
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

            if (Settings.getInstance().debugMode()) {

                System.err.println("[ CHYBA ] Došlo k závažné chybě při práci s kontextem Active Directory.");

                System.err.println("[ CHYBA ] " + e.getMessage());
                e.printStackTrace(System.err);
            }

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

        return getInfoInOU(OU, ldapQ, retAttributes);
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
                OU.contains(Settings.getInstance().getLDAP_baseGroups())
                || OU.contains(Settings.getInstance().getLDAP_baseDLContacts())
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

        Map<Integer, Map> result = getInfoInOU(OU, ldapQ, retAttributes);

        return (result == null) ? -1 : result.size();
    }

    /**
     * Vytvoření nové organizační jednotky v požadované cestě.
     *
     * @param name název nové OU
     * @param locationDN výchozí umístění
     */
    public void createOU(String name, String locationDN) {

        String[] objClass = {
                EBakaLDAPAttributes.OC_TOP.value(),
                EBakaLDAPAttributes.OC_OU.value()
        };

        createRecord(name, locationDN, objClass, null);
    }

    /**
     * Vytvoření nového žáka v Active Directory.
     *
     * @param OU cílová organizační jednotka
     * @param upn login v doméně
     * @param email nový primární e-mail
     * @param firstName křestní jméno žáka
     * @param sn příjmení žáka
     * @param displayName zobrazované jméno
     * @param intern_kod INTERN_KOD z Bakalářů
     * @param groups pole řetězců počátečních skupin
     */
    public void createNewUser(String OU, String upn, String email, String firstName, String sn, String displayName, String intern_kod, String[] groups) {
        // TODO
    }

    /**
     * Vytvoření nového kontaktu.
     *
     * @param OU organizační jednotka s kontakty
     * @param sn příjmení kontaktu
     * @param firstName křestní jméno kontaktu
     * @param displayName zobrazované jméno kontaktu
     * @param mail primární e-mail kontaktu
     * @param mobile mobilní telefonní číslo kontaktu
     * @param dLists pole řetězců počátečních distribučních seznamů
     */
    public void createContact(String OU, String sn, String firstName, String displayName, String mail, String mobile, String[] dLists) {

        if (mail == null || mail.length() < 1) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebyl nalezen platný e-mail pro zákonného zástupce " + displayName + ".");
            }

            return;
        }

        String[] objClasses = {
            EBakaLDAPAttributes.OC_TOP.value(),
            EBakaLDAPAttributes.OC_CONTACT.value(),
        };

        // příprava atributů s kontakty
        Attribute[] attrs = new Attribute[(mobile != null && mobile.length() > 0) ? 7 : 6];
        attrs[0] = new BasicAttribute(EBakaLDAPAttributes.NAME_LAST.attribute(), sn);
        attrs[1] = new BasicAttribute(EBakaLDAPAttributes.NAME_FIRST.attribute(), firstName);
        attrs[2] = new BasicAttribute(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), displayName);
        attrs[3] = new BasicAttribute(EBakaLDAPAttributes.MAIL.attribute(), mail);

        // interní kontakt - nelze schovat v GAL nebo zakázat
        if (!mail.contains(Settings.getInstance().getMailDomain())) {
            attrs[4] = new BasicAttribute(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.value());
            attrs[5] = new BasicAttribute(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), EBakaLDAPAttributes.MSXCH_REQ_AUTH.value());
        } else {
            attrs[4] = new BasicAttribute(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), "FALSE");
            attrs[5] = new BasicAttribute(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), "FALSE");
        }

        if (mobile != null && mobile.length() > 0) {
            attrs[6] = new BasicAttribute(EBakaLDAPAttributes.MOBILE.attribute(), mobile);
        }

        try {
            createRecord(displayName, OU, objClasses, attrs);
        } catch (Exception e) {

            System.err.println("[ CHYBA ] Nezdařilo se vytvořit kontakt.");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
            }

            if (Settings.getInstance().debugMode()) {
                e.printStackTrace(System.err);
            }
        }

        // prvotní přidání do distribučního seznamu
        if (dLists != null && dLists.length > 0 ) {

            // plné DN nového kontaktu
            String contactDN = "cn=" + displayName + "," + OU;

            for (int dl = 0; dl < dLists.length; dl++) {
                String dlDN = "cn=" + dLists[dl] + "," + Settings.getInstance().getLDAP_baseDLContacts();
                addContactToDL(contactDN, dlDN);
            }

        }
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
     * @param memberOf pole přímých nadřazených skupin (může být prázdné nebo null)
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
            // cílová destinace, musí ležet ve stejné OU
            String destinationGroupDN = "cn=" + memberOf[mof] + "," + targetOU;
            addObjectToGroup(groupDN, destinationGroupDN);
        }

    }

    /**
     * Vytvoření nového objektu v Active Directory.
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
                ctxGC.createSubcontext(cnName + "," + targetOU, container);
            } else {
                // tvorba objektu
                ctxGC.createSubcontext("cn=" + cnName + "," + targetOU, container);
            }

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebylo možné vytvořit požadovaný LDAP záznam (" + cnName + ").");
            }

            if (Settings.getInstance().debugMode()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
                e.printStackTrace(System.err);
            }
        }

    }

    /**
     * Smazání záznamu podle DN.
     *
     * @param dn plné DN objektu
     */
    private void deleteRecord(String dn) {

        // kontext
        LdapContext ctxGC = null;

        try {
            ctxGC = new InitialLdapContext(env, null);
            ctxGC.destroySubcontext(dn);
        } catch (Exception e) {
            // TODO
        }

    }

    /**
     * Uzamčení uživatelského účtu podle loginu.
     *
     * @param upn přihlašovací jméno uživatele k uzamčení
     */
    public void lockAccount(String upn) {
        // TODO
    }

    /**
     * Modifikace hodnoty atributu pro požadovaný objekt.
     *
     * @param modOp typ operace DirContext
     * @param dn plné jméno objektu
     * @param attribute název atributu
     * @param value nová hodnota
     */
    private boolean modifyAttribute(int modOp, String dn, EBakaLDAPAttributes attribute, Object value) {

        LdapContext bakaContext = null;

        try {
            bakaContext = new InitialLdapContext(env, null);

            ModificationItem mod[] = new ModificationItem[1];

            if (value instanceof String) {
                mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), value));
            } else {
                mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), (Attribute) value));
            }

            bakaContext.modifyAttributes(dn, mod);

        } catch (Exception e) {

            if (Settings.getInstance().beVerbose()) {
                System.out.println("[ CHYBA ] Nebylo možné modifikovat atribut objektu.");
            }

            if (Settings.getInstance().debugMode()) {
                System.out.println("[ DEBUG ] Operace typu " + modOp + " nad objektem: " + dn + " selhala. Atribut: " + attribute.attribute().toString() + ", cílová hodnota: " + value + ".");

                System.out.println("[ CHYBA ] " + e.getLocalizedMessage());
                e.printStackTrace(System.err);
            }

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
     */
    public void removeAttribute(String dn, EBakaLDAPAttributes attributes, String oldValue) {
        modifyAttribute(DirContext.REMOVE_ATTRIBUTE, dn, attributes, oldValue);
    }

    /**
     * Přidání nového atributu k objektu.
     *
     * @param dn plné DN objektu
     * @param attribute požadovaný atribut
     * @param value nová hodnota atributu
     */
    public void addAttribute(String dn, EBakaLDAPAttributes attribute, String value) {
        modifyAttribute(DirContext.ADD_ATTRIBUTE, dn, attribute, value);
    }

    /**
     * Změna hodnoty atributu objektu.
     *
     * @param dn plné DN objektu
     * @param attribute požadovaný atribut k nahrazení
     * @param newValue nová hodnota
     */
    public Boolean replaceAttribute(String dn, EBakaLDAPAttributes attribute, Object newValue) {

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
     */
    public void setGroupInfo(String OU, String groupCN, EBakaLDAPAttributes attribute, String value) {
        Map<Integer, Map<String, Object>> group = getGroupInfo(groupCN, OU);

        if (group.get(0).containsKey(attribute.attribute())) {
            replaceAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        } else {
            addAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        }
    }

    /**
     * Přesune požadovaný objekt do dané organizační jednotky.
     * Pokud je nastaveno <i>createNewOu</i> a cílová OU neexituje,
     * bude vytvořena.
     *
     * @param objectDN dn objektu
     * @param OUName plná cesta cílové OU
     * @param createNewOU vytvořit OU, pokud neexistuje
     */
    public void moveToOU(String objectDN, String OUName, Boolean createNewOU) {
        // TODO
        // All organizational unit objects	(objectCategory=organizationalUnit)
    }

    /**
     * Výpis všech přímých skupin, kterých je daný objekt členem.
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
        Map info = ((Map<Integer, Map>) getInfoInOU(Settings.getInstance().getLDAP_base(), ldapQ, retAttributes)).get(0);

        if (info != null) {

            if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) != null) {

                if (info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()) instanceof ArrayList) {

                    // více skupin
                    //result = new String[((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).size()];
                    result = new ArrayList<String>(((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).size());

                    Iterator<String> member = ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).iterator();
                    while (member.hasNext()) {
                        result.add(member.next());
                    }

                    /*
                    for (int m = 0; m  < ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).size(); m++) {
                        result.add( ((ArrayList) info.get(EBakaLDAPAttributes.MEMBER_OF.attribute())).get(m).toString() );
                    }*/

                } else {
                    // pouze jedna skupina
                    /*
                    result = new String[]{
                        info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()).toString(),
                    };*/

                    result = new ArrayList<>(1);
                    result.add(info.get(EBakaLDAPAttributes.MEMBER_OF.attribute()).toString());
                }

            } else {
                // inicializace - prázdný výsledek
                result = new ArrayList<>(0);
            }
        }

        return result;
    }

    /**
     * Přidání objektu jako člena do skupiny.
     *
     * @param objectDN plné jméno objektu
     * @param destinationGroupDN plné jméno cílové skupiny
     */
    public void addObjectToGroup(String objectDN, String destinationGroupDN) {

        addAttribute(destinationGroupDN, EBakaLDAPAttributes.MEMBER, objectDN);

        /*
        LdapContext ctxGC = null;

        try {

            ctxGC = new InitialLdapContext(env, null);

            // v režimu přidávání se mění položka member cílové skupiny
            ModificationItem member[] = new ModificationItem[1];
            member[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(EBakaLDAPAttributes.MEMBER.attribute(), objectDN));

            // modifikace seznamu členů cílové skupiny
            ctxGC.modifyAttributes(destinationGroupDN, member);

        } catch (Exception e) {
            //
        }
        */

    }

    /**
     * Odstranění objektu ze zadané skupiny.
     *
     * @param objectDN
     * @param groupDN
     */
    public void removeObjectFromGroup(String objectDN, String groupDN) {
        // z cílové skupiny odstraní objectDN z atributu member
        removeAttribute(groupDN, EBakaLDAPAttributes.MEMBER, objectDN);
    }

    /**
     * Přidání kontaktu do distribučního seznamu.
     *
     * @param contactDN plné DN kontaktu
     * @param distributionListDN plné DN distribučního seznamu
     */
    public void addContactToDL(String contactDN, String distributionListDN) {

        addAttribute(distributionListDN, EBakaLDAPAttributes.MEMBER, contactDN);

        /*
        LdapContext ctxGC = null;

        try {
            ctxGC = new InitialLdapContext(env, null);

            ModificationItem member[] = new ModificationItem[1];
            member[0] = new ModificationItem(DirContext.ADD_ATTRIBUTE, new BasicAttribute(EBakaLDAPAttributes.MEMBER.attribute(), contactDN));
            // modifikace DL
            ctxGC.modifyAttributes(distributionListDN, member);

        } catch (Exception e) {
            System.err.println("[ CHYBA ] Nezdařilo se přidat kontakt do distribučního seznamu.");

            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] " + e.getMessage());
            }

            if (Settings.getInstance().debugMode()) {
                e.printStackTrace(System.err);
            }
        }
         */

    }


}
