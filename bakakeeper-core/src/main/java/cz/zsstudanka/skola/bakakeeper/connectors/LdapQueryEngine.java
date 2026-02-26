package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.BakaSDDLHelper;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import net.tirasa.adsddl.ntsd.SDDL;
import net.tirasa.adsddl.ntsd.controls.SDFlagsControl;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.io.IOException;
import java.util.*;

/**
 * LDAP dotazovací engine extrahovaný z BakaADAuthenticator.
 * Obsahuje metody pro čtení objektů z Active Directory.
 *
 * @author Jan Hladěna
 */
class LdapQueryEngine {

    /** stránkování výsledků */
    private final int PAGE_SIZE = 250;

    /** továrna na LDAP připojení */
    private final LdapConnectionFactory connectionFactory;

    /**
     * Konstruktor.
     *
     * @param connectionFactory továrna na LDAP připojení
     */
    LdapQueryEngine(LdapConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    /**
     * Základní informace o objektu podle předaných atributů a základní cesty OU.
     *
     * @param baseOU základní OU pro prohledávání
     * @param findAttributes pole dotazů atribut/hodnota
     * @param retAttributes seznam čtených atributů
     * @return mapa získaných dat
     */
    public Map getObjectInfo(String baseOU, HashMap<String, String> findAttributes, final String[] retAttributes) {

        if (!connectionFactory.isAuthenticated()) return null;

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

        // výsledek
        HashMap<Integer, Map> objInfo = new HashMap();
        // počet výsledků
        Integer resNum = 0;

        // obecný kontext
        DirContext ctxGC = null;

        try {
            byte[] cookie = null;

            // LDAP dotaz a výsledky
            String searchFilter = findAND.toString();
            String[] returnedAtts = retAttributes;

            if (baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) {
                ctxGC = new InitialDirContext(connectionFactory.getEnv());
            } else {
                ctxGC = new InitialLdapContext(connectionFactory.getEnv(), null);

                // použití stránkování pro výsledky
                if (PAGE_SIZE > 0 && !baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) {
                    ((InitialLdapContext) ctxGC).setRequestControls(new Control[]{ new PagedResultsControl(PAGE_SIZE, Control.NONCRITICAL) });
                }

                // LDAP je MS AD a požaduje se UAC -> bude se číst i NTSD
                if (Settings.getInstance().isLDAP_MSAD() && Arrays.stream(retAttributes).anyMatch(EBakaLDAPAttributes.UAC.attribute()::equals)) {

                    returnedAtts = new String[retAttributes.length + 1];
                    int rA;
                    for (rA = 0; rA < retAttributes.length; rA++) {
                        returnedAtts[rA] = retAttributes[rA];
                    }
                    returnedAtts[retAttributes.length] = EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute();

                    // LDAP_SERVER_SD_FLAGS_OID = DACL (0x4)
                    ((InitialLdapContext) ctxGC).setRequestControls(new Control[]{new SDFlagsControl(0x04)});
                }
            }

            // řízení proledávání
            SearchControls searchCtls = new SearchControls();
            searchCtls.setReturningAttributes(returnedAtts);
            searchCtls.setSearchScope((baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) ? SearchControls.OBJECT_SCOPE : SearchControls.SUBTREE_SCOPE);

            // každá stránka
            do {
                // provedení dotazu + výsledky
                final NamingEnumeration<SearchResult> answer = ctxGC.search((baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) ? "" : baseOU, searchFilter, searchCtls);
                while (answer.hasMoreElements()) {

                    //SearchResult result = (SearchResult) answer.next();
                    SearchResult result = answer.next();
                    Attributes attrs = result.getAttributes();

                    // jeden objekt
                    Map objDetails = new HashMap<String, Object>();

                    // atributy výsledku
                    if (attrs != null) {
                        NamingEnumeration<? extends Attribute> enumeration = attrs.getAll();

                        while (enumeration.hasMore()) {

                            // konstrukce atributu
                            Attribute attr = (Attribute) enumeration.next();

                            // jeden prvek atributu
                            if (attr.size() == 1) {
                                // vložení výsledku
                                objDetails.put(attr.getID().toString(), (Object) attr.get());
                            } else {
                                // pole prvků atributu (skupiny, ...)
                                ArrayList<Object> retData = new ArrayList<>();
                                for (int ats = 0; ats < attr.size(); ats++) {
                                    retData.add(attr.get(ats));
                                }

                                // vložení pole výsledků
                                objDetails.put(attr.getID().toString(), retData);
                            }
                        } // jednotlivé atributy

                        // modifikace UAC
                        if (Settings.getInstance().isLDAP_MSAD() && Arrays.stream(retAttributes).anyMatch(EBakaLDAPAttributes.UAC.attribute()::equals)) {
                            if (BakaSDDLHelper.isUserCannotChangePassword(new SDDL((byte[]) objDetails.get(EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute())))) {
                                // nastavení flagu do UAC; ve výchozím stavu MS AS vždy uvádí 0
                                objDetails.replace(EBakaLDAPAttributes.UAC.attribute(), String.format("%d", EBakaUAC.PASSWD_CANT_CHANGE.setFlag((String) objDetails.get(EBakaLDAPAttributes.UAC.attribute()).toString())));
                            }
                        }

                        enumeration.close();
                    }

                    objInfo.put(resNum, objDetails);
                    resNum++;
                }

                // řízení stránky
                if (PAGE_SIZE > 0 && !baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) {
                    final Control[] controls = ((InitialLdapContext) ctxGC).getResponseControls();
                    if (controls != null) {
                        for (Control control : controls) {
                            // řízení stránkování
                            if (control instanceof PagedResultsResponseControl) {
                                // získání cookie další stránky
                                cookie = ((PagedResultsResponseControl) control).getCookie();
                            }
                        }
                    } // žádné řízení
                }

                // reaktivace stránkování s novým cookie
                if (PAGE_SIZE > 0 && !baseOU.equals(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute())) {
                    ((InitialLdapContext) ctxGC).setRequestControls(new Control[]{ new PagedResultsControl(PAGE_SIZE, cookie, Control.CRITICAL) });
                }

                // není žádná další stránka
            } while (cookie != null);

            ctxGC.close();

        } catch (NamingException e) {
            ReportManager.handleException("Hledaný objekt nebylo možné nalézt.", e);
            // prázdný výsledek - objekt nenalezen
            return null;
        } catch (IOException e) {
            ReportManager.handleException("Došlo k chybě během stránkování výsledků.", e);
            return null;
        }

        return objInfo;
    }

    /**
     * Informace o LDAP serveru z RootDSE.
     *
     * @return Název, verze, AD funkcionalita.
     */
    public Map<String, String> getServerInfo() {

        // dotaz
        HashMap<String, String> ldapQ = new HashMap<String, String>();
        ldapQ.put(EBakaLDAPAttributes.OC_GENERAL.attribute(), EBakaLDAPAttributes.BK_SYMBOL_ANY_VALUE.value());

        // požadované atributy
        String[] retAttributes = {
                EBakaLDAPAttributes.SRV_VENDOR.attribute(),
                EBakaLDAPAttributes.SRV_VERSION.attribute(),
                EBakaLDAPAttributes.SRV_AD_CATALOG_READY.attribute(),
                EBakaLDAPAttributes.SRV_AD_DOMAIN_LEVEL.attribute(),
                EBakaLDAPAttributes.SRV_AD_FOREST_LEVEL.attribute()
        };

        return (Map<String, String>) getObjectInfo(EBakaLDAPAttributes.BK_SYMBOL_ROOTDSE.attribute(), ldapQ, retAttributes).get(0);
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

                EBakaLDAPAttributes.LAST_LOGON.attribute(),
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
}
