package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;

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

        record.setInternalId(sql.get(EBakaSQL.F_STU_ID.basename()));
        record.setSurname(sql.get(EBakaSQL.F_STU_SURNAME.basename()));
        record.setGivenName(sql.get(EBakaSQL.F_STU_GIVENNAME.basename()));
        record.setDisplayName(
                sql.get(EBakaSQL.F_STU_SURNAME.basename()) + " " +
                sql.get(EBakaSQL.F_STU_GIVENNAME.basename()));
        record.setEmail(sql.get(EBakaSQL.F_STU_MAIL.basename()));
        record.setClassName(sql.get(EBakaSQL.F_STU_CLASS.basename()));
        record.setClassNumber(sql.get(EBakaSQL.F_STU_CLASS_ID.basename()));
        record.setExpired(sql.get(EBakaSQL.F_STU_EXPIRED.basename()));

        // ročník – z aliasu B_ROCNIK (pokud existuje v SQL joinu)
        String classYearStr = sql.get(EBakaSQL.F_STU_BK_CLASSYEAR.basename());
        if (classYearStr != null && !classYearStr.isEmpty()) {
            try {
                record.setClassYear(Integer.parseInt(classYearStr));
            } catch (NumberFormatException e) {
                // nevalidní data – ponecháme výchozí 0
            }
        }

        record.setClassLetter(sql.get(EBakaSQL.F_STU_BK_CLASSLETTER.basename()));

        // ID zákonného zástupce (pokud je v SQL joinu)
        String guaId = sql.get(EBakaSQL.F_GUA_BK_ID.basename());
        if (guaId != null) {
            record.setGuardianInternalId(guaId);
        }

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

        // omezení externí pošty (extensionAttribute2)
        String ext02 = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT02);
        record.setExtMailRestricted(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(ext02));

        return record;
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

        // e-mail z SQL je autoritativní; pokud je prázdný, použijeme LDAP
        if (record.getEmail() == null || record.getEmail().isEmpty()) {
            record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        }

        record.setPartial(false);
        record.setPaired(true);

        return record;
    }
}
