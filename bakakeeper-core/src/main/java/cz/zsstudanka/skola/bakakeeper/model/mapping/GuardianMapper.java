package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;

/**
 * Mapper pro převod dat zákonného zástupce z DataSQL a DataLDAP na typovaný GuardianRecord.
 *
 * @author Jan Hladěna
 */
public class GuardianMapper {

    private GuardianMapper() {
        // utilita – bez instancí
    }

    /**
     * Vytvoří GuardianRecord z SQL dat.
     *
     * @param sql data z evidence Bakaláři (aliasy ZZ_*)
     * @return typovaný záznam zákonného zástupce, nebo null
     */
    public static GuardianRecord fromSQL(DataSQL sql) {
        if (sql == null) {
            return null;
        }

        GuardianRecord record = new GuardianRecord();
        record.setPartial(true);
        record.setPaired(false);

        record.setInternalId(sql.get(EBakaSQL.F_GUA_BK_ID.basename()));
        record.setSurname(sql.get(EBakaSQL.F_GUA_BK_SURNAME.basename()));
        record.setGivenName(sql.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()));
        record.setDisplayName(
                sql.get(EBakaSQL.F_GUA_BK_SURNAME.basename()) + " " +
                sql.get(EBakaSQL.F_GUA_BK_GIVENNAME.basename()));
        record.setEmail(sql.get(EBakaSQL.F_GUA_BK_MAIL.basename()));
        record.setPhone(sql.get(EBakaSQL.F_GUA_BK_MOBILE.basename()));

        return record;
    }

    /**
     * Vytvoří GuardianRecord z LDAP dat.
     *
     * @param ldap data z Active Directory (kontakt)
     * @return typovaný záznam zákonného zástupce, nebo null
     */
    public static GuardianRecord fromLDAP(DataLDAP ldap) {
        if (ldap == null) {
            return null;
        }

        GuardianRecord record = new GuardianRecord();
        record.setPartial(true);
        record.setPaired(false);

        record.setInternalId(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT01));
        record.setSurname(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_LAST));
        record.setGivenName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_FIRST));
        record.setDisplayName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_DISPLAY));
        record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        record.setPhone(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MOBILE));
        record.setDn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN));

        String galHidden = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MSXCH_GAL_HIDDEN);
        record.setGalHidden(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(galHidden));

        String reqAuth = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MSXCH_REQ_AUTH);
        record.setRequireAuth(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(reqAuth));

        return record;
    }

    /**
     * Sloučí SQL a LDAP data do jednoho záznamu. SQL je autoritativní zdroj
     * pro jméno a ID. LDAP doplňuje DN, GAL a autorizační příznaky.
     *
     * @param sql  data z evidence Bakaláři
     * @param ldap data z Active Directory
     * @return sloučený záznam (partial = false, paired = true)
     */
    public static GuardianRecord merge(DataSQL sql, DataLDAP ldap) {
        GuardianRecord record = fromSQL(sql);
        if (record == null) {
            return fromLDAP(ldap);
        }
        if (ldap == null) {
            return record;
        }

        // AD-specifická pole
        record.setDn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN));

        String galHidden = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MSXCH_GAL_HIDDEN);
        record.setGalHidden(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(galHidden));

        String reqAuth = MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MSXCH_REQ_AUTH);
        record.setRequireAuth(
                EBakaLDAPAttributes.BK_LITERAL_TRUE.value().equalsIgnoreCase(reqAuth));

        // e-mail a telefon: SQL je autoritativní, LDAP jako fallback
        if (record.getEmail() == null || record.getEmail().isEmpty()) {
            record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        }
        if (record.getPhone() == null || record.getPhone().isEmpty()) {
            record.setPhone(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MOBILE));
        }

        record.setPartial(false);
        record.setPaired(true);

        return record;
    }
}
