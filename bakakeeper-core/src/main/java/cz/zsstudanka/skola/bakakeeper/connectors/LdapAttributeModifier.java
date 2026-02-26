package cz.zsstudanka.skola.bakakeeper.connectors;

import cz.zsstudanka.skola.bakakeeper.components.BakaSDDLHelper;
import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaUAC;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;
import net.tirasa.adsddl.ntsd.SDDL;
import net.tirasa.adsddl.ntsd.controls.SDFlagsControl;

import javax.naming.NameAlreadyBoundException;
import javax.naming.NamingException;
import javax.naming.directory.*;
import javax.naming.ldap.Control;
import javax.naming.ldap.LdapContext;
import java.util.HashMap;
import java.util.Map;

/**
 * Pomocná třída pro modifikaci LDAP atributů.
 * Extrahováno z BakaADAuthenticator – operace nad atributy objektů v Active Directory.
 *
 * @author Jan Hladěna
 */
class LdapAttributeModifier {

    /** továrna na LDAP spojení */
    private final LdapConnectionFactory connectionFactory;

    /** dotazovací engine pro čtení objektů */
    private final LdapQueryEngine queryEngine;

    /**
     * Konstruktor.
     *
     * @param connectionFactory továrna na LDAP spojení
     * @param queryEngine dotazovací engine pro čtení objektů
     */
    LdapAttributeModifier(LdapConnectionFactory connectionFactory, LdapQueryEngine queryEngine) {
        this.connectionFactory = connectionFactory;
        this.queryEngine = queryEngine;
    }

    /**
     * Modifikace hodnoty atributu pro požadovaný objekt.
     *
     * @param modOp typ operace DirContext
     * @param dn plné jméno objektu
     * @param attribute název atributu
     * @param value nová hodnota
     */
    boolean modifyAttribute(int modOp, String dn, EBakaLDAPAttributes attribute, String value) {

        if (Settings.getInstance().isDebug()) {
            ReportManager.log(EBakaLogType.LOG_LDAP, "Operace typu [" + modOp + "] nad objektem: [" + dn + "]. Atribut: [" + attribute.attribute().toString() + "], cílová hodnota: [" + value + "].");
        }

        LdapContext bakaContext = null;
        try {
            bakaContext = connectionFactory.createContext();

            ModificationItem mod[] = new ModificationItem[1];

            // heslo v Microsoft Active Directory
            if (attribute.equals(EBakaLDAPAttributes.PW_UNICODE)) {
                String password = "\"" + value + "\"";
                try {
                    byte[] unicodePwd = password.getBytes("UTF-16LE");
                    mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), unicodePwd));
                } catch (Exception e) {
                    ReportManager.handleException("Nebylo možné nastavit heslo.", e);
                }
                // změna UAC na Microsoft Active Directory
            } else if (attribute.equals(EBakaLDAPAttributes.UAC)) {
                // původní data v UAC
                HashMap<String, String> queryOrig = new HashMap<String, String>();
                queryOrig.put(EBakaLDAPAttributes.OC_USER.attribute(), EBakaLDAPAttributes.OC_USER.value());
                queryOrig.put(EBakaLDAPAttributes.CN.attribute(), BakaUtils.parseCN(dn));

                String[] origAttributes = {
                        EBakaLDAPAttributes.UAC.attribute()
                };

                Map<Integer, Map<String, String>> origData = queryEngine.getObjectInfo(BakaUtils.parseBase(dn), queryOrig, origAttributes);

                // ochrana proti NPE – objekt nebyl nalezen
                if (origData == null || origData.get(0) == null) {
                    ReportManager.log(EBakaLogType.LOG_ERR,
                            "Nebylo možné načíst původní UAC pro objekt [" + dn + "].");
                    return false;
                }

                boolean uacPNXOrig = EBakaUAC.PASSWD_CANT_CHANGE.checkFlag(origData.get(0).get(EBakaLDAPAttributes.UAC.attribute()));
                boolean uacPNXNew = EBakaUAC.PASSWD_CANT_CHANGE.checkFlag(value);

                // požadována změna oprávnění uživatelské změny hesla?
                if (uacPNXOrig != uacPNXNew) {

                    bakaContext.setRequestControls(new Control[] { new SDFlagsControl(0x04) }); // DACL

                    // data objektu
                    Map<Integer, Map<String, Object>> ntsdOrigResult = queryEngine.getObjectInfo(
                            BakaUtils.parseBase(dn), queryOrig,
                            new String[] { EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute() }
                    );

                    // ochrana proti NPE – objekt nebyl nalezen
                    if (ntsdOrigResult == null || ntsdOrigResult.get(0) == null) {
                        ReportManager.log(EBakaLogType.LOG_ERR,
                                "Nebylo možné načíst NT Security Descriptor pro objekt [" + dn + "].");
                        return false;
                    }

                    // binární data NT Security Descriptoru
                    byte[] ntsdOrig = (byte[]) ntsdOrigResult.get(0).get(EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute());
                    // inicializace SDDL
                    SDDL sddl = new SDDL(ntsdOrig);
                    // nová hodnota
                    byte[] newSddlData = BakaSDDLHelper.userCannotChangePassword(sddl, uacPNXNew).toByteArray();

                    // změna atributu - práce s ntSecurityDescriptorem namísto UAC
                    mod[0] = new ModificationItem(
                            DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute(EBakaLDAPAttributes.NT_SECURITY_DESCRIPTOR.attribute(), newSddlData)
                    );

                } else {
                    // fallback, proběhne změna na jiném místě UAC než v oprávnění pro změnu hesla
                    mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), value));
                }

            } else {
                // vše ostatní
                mod[0] = new ModificationItem(modOp, new BasicAttribute(attribute.attribute(), value));
            }

            bakaContext.modifyAttributes(dn, mod);
        } catch (AttributeInUseException | NameAlreadyBoundException e) {
            // LDAP error 20 (AttributeInUseException) nebo 68 (NameAlreadyBoundException, Samba4) –
            // atribut (např. member) již existuje.
            // Při ADD_ATTRIBUTE je to očekávaný stav (idempotentní operace).
            if (Settings.getInstance().isDebug()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG,
                        "Atribut [" + attribute.attribute() + "] již existuje na objektu [" + dn + "] – přeskakuji.");
            }
            return true;
        } catch (Exception e) {
            ReportManager.handleException("Nebylo možné modifikovat atribut objektu.", e);
            return false;
        } finally {
            if (bakaContext != null) { try { bakaContext.close(); } catch (NamingException ignored) {} }
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
     * <p>Pokud je nová hodnota null nebo prázdný řetězec, atribut se kompletně
     * odebere operací {@code REMOVE_ATTRIBUTE} (Samba4 nepřijímá
     * {@code REPLACE_ATTRIBUTE} s prázdnou hodnotou – LDAP error 21).</p>
     *
     * @param dn plné DN objektu
     * @param attribute požadovaný atribut k nahrazení
     * @param newValue nová hodnota (null/prázdný = odebrání atributu)
     * @return úspěch operace
     */
    public Boolean replaceAttribute(String dn, EBakaLDAPAttributes attribute, String newValue) {
        // Samba4 nepřijímá REPLACE_ATTRIBUTE s prázdnou hodnotou (LDAP error 21);
        // pro smazání atributu je nutné REMOVE_ATTRIBUTE bez hodnoty
        if (newValue == null || newValue.isEmpty()) {
            return removeAttributeEntirely(dn, attribute);
        }

        if (modifyAttribute(DirContext.REPLACE_ATTRIBUTE, dn, attribute, newValue)) {
            return true;
        } else {
            return modifyAttribute(DirContext.ADD_ATTRIBUTE, dn, attribute, newValue);
        }
    }

    /**
     * Kompletní odebrání atributu z objektu (bez specifikace hodnoty).
     *
     * <p>Samba4 vyžaduje {@code REMOVE_ATTRIBUTE} bez hodnoty pro smazání atributu;
     * {@code REPLACE_ATTRIBUTE} s prázdnou hodnotou způsobí LDAP error 21
     * (LDAP_INVALID_ATTRIBUTE_SYNTAX).</p>
     *
     * <p>Operace je idempotentní – pokud atribut neexistuje (LDAP error 16),
     * vrací true.</p>
     *
     * @param dn plné DN objektu
     * @param attribute atribut k odebrání
     * @return úspěch operace (true i pokud atribut neexistoval)
     */
    boolean removeAttributeEntirely(String dn, EBakaLDAPAttributes attribute) {
        if (Settings.getInstance().isDebug()) {
            ReportManager.log(EBakaLogType.LOG_LDAP,
                    "Odebrání atributu [" + attribute.attribute() + "] z objektu: [" + dn + "].");
        }

        LdapContext ctx = null;
        try {
            ctx = connectionFactory.createContext();
            ModificationItem[] mod = new ModificationItem[1];
            // BasicAttribute bez hodnoty → odebere celý atribut
            mod[0] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE,
                    new BasicAttribute(attribute.attribute()));
            ctx.modifyAttributes(dn, mod);
        } catch (NoSuchAttributeException e) {
            // atribut neexistuje → idempotentní operace (úspěch)
            if (Settings.getInstance().isDebug()) {
                ReportManager.log(EBakaLogType.LOG_DEBUG,
                        "Atribut [" + attribute.attribute() + "] neexistuje na [" + dn + "] – přeskakuji.");
            }
        } catch (Exception e) {
            ReportManager.handleException(
                    "Nebylo možné odebrat atribut [" + attribute.attribute() + "] z objektu [" + dn + "].", e);
            return false;
        } finally {
            if (ctx != null) { try { ctx.close(); } catch (NamingException ignored) {} }
        }

        return true;
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
        Map<Integer, Map<String, Object>> group = queryEngine.getGroupInfo(groupCN, OU);

        if (group.get(0).containsKey(attribute.attribute())) {
            return replaceAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        } else {
            return addAttribute(group.get(0).get(EBakaLDAPAttributes.DN.attribute()).toString(), attribute, value);
        }
    }
}
