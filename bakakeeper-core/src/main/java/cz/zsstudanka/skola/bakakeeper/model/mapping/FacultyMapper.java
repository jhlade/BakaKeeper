package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;

/**
 * Mapper pro převod dat vyučujícího z DataSQL a DataLDAP na typovaný FacultyRecord.
 *
 * @author Jan Hladěna
 */
public class FacultyMapper {

    private FacultyMapper() {
        // utilita – bez instancí
    }

    /**
     * Vytvoří FacultyRecord z SQL dat.
     *
     * @param sql data z evidence Bakaláři
     * @return typovaný záznam vyučujícího, nebo null
     */
    public static FacultyRecord fromSQL(DataSQL sql) {
        if (sql == null) {
            return null;
        }

        FacultyRecord record = new FacultyRecord();
        record.setPartial(true);
        record.setPaired(false);

        record.setInternalId(sql.get(EBakaSQL.F_FAC_ID.basename()));
        record.setSurname(sql.get(EBakaSQL.F_FAC_SURNAME.basename()));
        record.setGivenName(sql.get(EBakaSQL.F_FAC_GIVENNAME.basename()));
        record.setDisplayName(
                sql.get(EBakaSQL.F_FAC_SURNAME.basename()) + " " +
                sql.get(EBakaSQL.F_FAC_GIVENNAME.basename()));
        record.setEmail(sql.get(EBakaSQL.F_FAC_EMAIL.basename()));

        String active = sql.get(EBakaSQL.F_FAC_ACTIVE.basename());
        record.setActiveThisYear(EBakaSQL.LIT_TRUE.basename().equals(active));

        return record;
    }

    /**
     * Vytvoří FacultyRecord z LDAP dat.
     *
     * @param ldap data z Active Directory
     * @return typovaný záznam vyučujícího, nebo null
     */
    public static FacultyRecord fromLDAP(DataLDAP ldap) {
        if (ldap == null) {
            return null;
        }

        FacultyRecord record = new FacultyRecord();
        record.setPartial(true);
        record.setPaired(false);

        record.setInternalId(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.EXT01));
        record.setSurname(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_LAST));
        record.setGivenName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_FIRST));
        record.setDisplayName(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.NAME_DISPLAY));
        record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        record.setDn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN));

        return record;
    }

    /**
     * Sloučí SQL a LDAP data do jednoho záznamu. SQL je autoritativní zdroj
     * pro jméno, ID a příznak aktivity. LDAP doplňuje DN a e-mail.
     *
     * @param sql  data z evidence Bakaláři
     * @param ldap data z Active Directory
     * @return sloučený záznam (partial = false, paired = true)
     */
    public static FacultyRecord merge(DataSQL sql, DataLDAP ldap) {
        FacultyRecord record = fromSQL(sql);
        if (record == null) {
            return fromLDAP(ldap);
        }
        if (ldap == null) {
            return record;
        }

        record.setDn(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.DN));

        if (record.getEmail() == null || record.getEmail().isEmpty()) {
            record.setEmail(MapperUtils.getStringAttr(ldap, EBakaLDAPAttributes.MAIL));
        }

        record.setPartial(false);
        record.setPaired(true);

        return record;
    }
}
