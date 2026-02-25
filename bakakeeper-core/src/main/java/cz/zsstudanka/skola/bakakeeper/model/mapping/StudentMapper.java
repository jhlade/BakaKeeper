package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.utils.BakaUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Mapper pro převod dat žáka z DataSQL a DataLDAP na typovaný StudentRecord.
 *
 * @author Jan Hladěna
 */
public class StudentMapper {

    private StudentMapper() {
        // utilita – bez instancí
    }

    /**
     * Vytvoří StudentRecord z SQL dat.
     *
     * @param sql data z evidence Bakaláři
     * @return typovaný záznam žáka (partial = true, paired = false), nebo null
     */
    public static StudentRecord fromSQL(DataSQL sql) {
        if (sql == null) {
            return null;
        }

        StudentRecord record = new StudentRecord();
        record.setPartial(true);
        record.setPaired(false);

        // sqlValue() konvertuje sentinel "(NULL)" zpět na skutečný null
        record.setInternalId(sqlValue(sql, EBakaSQL.F_STU_ID));
        record.setSurname(sqlValue(sql, EBakaSQL.F_STU_SURNAME));
        record.setGivenName(sqlValue(sql, EBakaSQL.F_STU_GIVENNAME));
        String surname = record.getSurname();
        String givenName = record.getGivenName();
        record.setDisplayName(
                (surname != null ? surname : "") + " " +
                (givenName != null ? givenName : ""));
        record.setEmail(sqlValue(sql, EBakaSQL.F_STU_MAIL));
        record.setClassName(sqlValue(sql, EBakaSQL.F_STU_CLASS));
        record.setClassNumber(sqlValue(sql, EBakaSQL.F_STU_CLASS_ID));
        record.setExpired(sqlValue(sql, EBakaSQL.F_STU_EXPIRED));

        // ročník – z aliasu B_ROCNIK (pokud existuje v SQL joinu)
        String classYearStr = sqlValue(sql, EBakaSQL.F_STU_BK_CLASSYEAR);
        if (classYearStr != null && !classYearStr.isEmpty()) {
            try {
                record.setClassYear(Integer.parseInt(classYearStr));
            } catch (NumberFormatException e) {
                // nevalidní data – ponecháme výchozí 0
            }
        }

        record.setClassLetter(sqlValue(sql, EBakaSQL.F_STU_BK_CLASSLETTER));

        // data zákonného zástupce (pokud je v SQL joinu)
        record.setGuardianInternalId(sqlValue(sql, EBakaSQL.F_GUA_BK_ID));
        record.setGuardianSurname(sqlValue(sql, EBakaSQL.F_GUA_BK_SURNAME));
        record.setGuardianGivenName(sqlValue(sql, EBakaSQL.F_GUA_BK_GIVENNAME));
        record.setGuardianPhone(sqlValue(sql, EBakaSQL.F_GUA_BK_MOBILE));
        record.setGuardianEmail(sqlValue(sql, EBakaSQL.F_GUA_BK_MAIL));

        return record;
    }

    /**
     * Vytvoří StudentRecord z LDAP dat.
     *
     * @param ldap data z Active Directory
     * @return typovaný záznam žáka (partial = true, paired = false), nebo null
     */
    public static StudentRecord fromLDAP(DataLDAP ldap) {
        if (ldap == null) {
            return null;
        }

        StudentRecord record = new StudentRecord();
        record.setPartial(true);
        record.setPaired(false);

        record.setInternalId(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT01));
        record.setSurname(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_LAST));
        record.setGivenName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_FIRST));
        record.setDisplayName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_DISPLAY));
        record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));

        String dn = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN);
        record.setDn(dn);

        // derivace třídy z DN – např. CN=novak.jan,OU=Trida-A,OU=Rocnik-6,...
        if (dn != null && dn.contains("Trida-") && dn.contains("Rocnik-")) {
            try {
                record.setClassYear(BakaUtils.classYearFromDn(dn));
                record.setClassLetter(BakaUtils.classLetterFromDn(dn));
                record.setClassName(BakaUtils.classStringFromDN(dn));
            } catch (Exception ignored) {
                // DN neobsahuje standardní strukturu (např. alumni) – ponechat výchozí
            }
        }

        record.setSamAccountName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.LOGIN));
        record.setUpn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.UPN));
        record.setTitle(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.TITLE));

        // UAC
        String uacStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.UAC);
        if (uacStr != null && !uacStr.isEmpty()) {
            try {
                record.setUac(Integer.parseInt(uacStr));
            } catch (NumberFormatException e) {
                // nevalidní data
            }
        }

        // omezení externí pošty (extensionAttribute2)
        String ext02 = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT02);
        record.setExtMailRestricted(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(ext02));

        // proxyAddresses – multi-value atribut (SMTP: primární, smtp: sekundární)
        record.setProxyAddresses(
                new ArrayList<>(MapperUtils.getMultiValueAttr(ldap, EBakaLDAPAttributes.PROXY_ADDR)));

        // poslední přihlášení
        String lastLogonStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.LAST_LOGON);
        if (lastLogonStr != null && !lastLogonStr.isEmpty()) {
            try { record.setLastLogon(Long.parseLong(lastLogonStr)); } catch (NumberFormatException ignored) {}
        }

        // poslední změna hesla
        String pwdLastSetStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.PW_LASTSET);
        if (pwdLastSetStr != null && !pwdLastSetStr.isEmpty()) {
            try { record.setPwdLastSet(Long.parseLong(pwdLastSetStr)); } catch (NumberFormatException ignored) {}
        }

        // členství ve skupinách
        record.setMemberOf(new ArrayList<>(MapperUtils.getMultiValueAttr(ldap, EBakaLDAPAttributes.MEMBER_OF)));

        // aktuální hodnoty atributů pro konvergentní rekonciliaci pravidel
        // (extensionAttribute3-15 + title) – umožňuje zjistit, zda atribut
        // potřebuje vyčistit při odebrání pravidla z konfigurace
        Map<String, String> ruleAttrs = new HashMap<>();
        for (EBakaLDAPAttributes ext : RULE_ELIGIBLE_ATTRIBUTES) {
            String val = MapperUtils.getStringAttr(ldap, ext);
            if (val != null && !val.isEmpty()) {
                ruleAttrs.put(ext.attribute(), val);
            }
        }
        // title je samostatné pole, ale pro rekonciliaci potřebujeme i jeho hodnotu v mapě
        if (record.getTitle() != null && !record.getTitle().isEmpty()) {
            ruleAttrs.put(EBakaLDAPAttributes.TITLE.attribute(), record.getTitle());
        }
        record.setRuleAttributes(ruleAttrs);

        return record;
    }

    /**
     * Atributy spravovatelné pravidly – extensionAttribute3-15.
     * EXT01 (INTERN_KOD) a EXT02 (mail restriction) jsou spravovány jinými fázemi
     * synchronizace a NEREKONCILUJÍ se přes pravidla.
     */
    private static final List<EBakaLDAPAttributes> RULE_ELIGIBLE_ATTRIBUTES = List.of(
            EBakaLDAPAttributes.EXT03, EBakaLDAPAttributes.EXT04, EBakaLDAPAttributes.EXT05,
            EBakaLDAPAttributes.EXT06, EBakaLDAPAttributes.EXT07, EBakaLDAPAttributes.EXT08,
            EBakaLDAPAttributes.EXT09, EBakaLDAPAttributes.EXT10, EBakaLDAPAttributes.EXT11,
            EBakaLDAPAttributes.EXT12, EBakaLDAPAttributes.EXT13, EBakaLDAPAttributes.EXT14,
            EBakaLDAPAttributes.EXT15
    );

    /**
     * Vrátí hodnotu z DataSQL, přičemž sentinel "(NULL)" nahrazuje skutečným null.
     */
    private static String sqlValue(DataSQL sql, EBakaSQL field) {
        String val = sql.get(field.basename());
        if (val == null || EBakaSQL.NULL.basename().equals(val)) {
            return null;
        }
        return val;
    }

    /**
     * Sloučí SQL a LDAP data do jednoho záznamu. SQL je autoritativní zdroj
     * pro jméno, třídu a ID. LDAP doplňuje AD-specifická pole (DN, sAMAccountName,
     * UPN, UAC, title).
     *
     * @param sql  data z evidence Bakaláři
     * @param ldap data z Active Directory
     * @return sloučený záznam (partial = false, paired = true)
     */
    public static StudentRecord merge(DataSQL sql, DataLDAP ldap) {
        StudentRecord record = fromSQL(sql);
        if (record == null) {
            return fromLDAP(ldap);
        }
        if (ldap == null) {
            return record;
        }

        // AD-specifická pole
        record.setDn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN));
        record.setSamAccountName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.LOGIN));
        record.setUpn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.UPN));
        record.setTitle(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.TITLE));

        // UAC
        String uacStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.UAC);
        if (uacStr != null && !uacStr.isEmpty()) {
            try {
                record.setUac(Integer.parseInt(uacStr));
            } catch (NumberFormatException e) {
                // nevalidní data
            }
        }

        // omezení externí pošty
        String ext02 = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT02);
        record.setExtMailRestricted(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(ext02));

        // proxyAddresses – multi-value atribut z LDAP
        record.setProxyAddresses(
                new ArrayList<>(MapperUtils.getMultiValueAttr(ldap, EBakaLDAPAttributes.PROXY_ADDR)));

        // poslední přihlášení
        String lastLogonStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.LAST_LOGON);
        if (lastLogonStr != null && !lastLogonStr.isEmpty()) {
            try { record.setLastLogon(Long.parseLong(lastLogonStr)); } catch (NumberFormatException ignored) {}
        }

        // poslední změna hesla
        String pwdLastSetStr = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.PW_LASTSET);
        if (pwdLastSetStr != null && !pwdLastSetStr.isEmpty()) {
            try { record.setPwdLastSet(Long.parseLong(pwdLastSetStr)); } catch (NumberFormatException ignored) {}
        }

        // členství ve skupinách
        record.setMemberOf(new ArrayList<>(MapperUtils.getMultiValueAttr(ldap, EBakaLDAPAttributes.MEMBER_OF)));

        // e-mail z SQL je autoritativní; pokud je prázdný, použijeme LDAP
        if (record.getEmail() == null || record.getEmail().isEmpty()) {
            record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        }

        record.setPartial(false);
        record.setPaired(true);

        return record;
    }
}
