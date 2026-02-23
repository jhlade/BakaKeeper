package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro GuardianMapper – převod SQL/LDAP dat na typovaný GuardianRecord.
 *
 * @author Jan Hladěna
 */
class GuardianMapperTest {

    @Test
    void fromSQLnull() {
        assertNull(GuardianMapper.fromSQL(null));
    }

    @Test
    void fromLDAPnull() {
        assertNull(GuardianMapper.fromLDAP(null));
    }

    @Test
    void fromSQL() {
        DataSQL sql = createTestSQL();

        GuardianRecord r = GuardianMapper.fromSQL(sql);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("99001", r.getInternalId());
        assertEquals("Nováková", r.getSurname());
        assertEquals("Jana", r.getGivenName());
        assertEquals("Nováková Jana", r.getDisplayName());
        assertEquals("novakova@email.cz", r.getEmail());
        assertEquals("777888999", r.getPhone());
        assertNull(r.getDn());
    }

    @Test
    void fromLDAP() {
        DataLDAP ldap = createTestLDAP();

        GuardianRecord r = GuardianMapper.fromLDAP(ldap);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("99001", r.getInternalId());
        assertEquals("Nováková", r.getSurname());
        assertEquals("Jana", r.getGivenName());
        assertEquals("novakova@email.cz", r.getEmail());
        assertEquals("777888999", r.getPhone());
        assertEquals("CN=Nováková Jana,OU=Kontakty,DC=skola,DC=local", r.getDn());
        assertTrue(r.isGalHidden());
        assertTrue(r.isRequireAuth());
    }

    @Test
    void mergeBoth() {
        DataSQL sql = createTestSQL();
        DataLDAP ldap = createTestLDAP();

        GuardianRecord r = GuardianMapper.merge(sql, ldap);

        assertNotNull(r);
        assertFalse(r.isPartial());
        assertTrue(r.isPaired());

        // SQL je autoritativní
        assertEquals("99001", r.getInternalId());
        assertEquals("Nováková", r.getSurname());
        assertEquals("novakova@email.cz", r.getEmail());
        assertEquals("777888999", r.getPhone());

        // LDAP doplňuje
        assertEquals("CN=Nováková Jana,OU=Kontakty,DC=skola,DC=local", r.getDn());
        assertTrue(r.isGalHidden());
        assertTrue(r.isRequireAuth());
    }

    @Test
    void mergeBothNull() {
        assertNull(GuardianMapper.merge(null, null));
    }

    @Test
    void mergeEmailFallback() {
        DataSQL sql = createTestSQL();
        sql.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "");

        DataLDAP ldap = createTestLDAP();

        GuardianRecord r = GuardianMapper.merge(sql, ldap);
        assertEquals("novakova@email.cz", r.getEmail());
    }

    @Test
    void mergePhoneFallback() {
        DataSQL sql = createTestSQL();
        sql.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "");

        DataLDAP ldap = createTestLDAP();

        GuardianRecord r = GuardianMapper.merge(sql, ldap);
        assertEquals("777888999", r.getPhone());
    }

    @Test
    void galNotHidden() {
        DataLDAP ldap = createTestLDAP();
        ldap.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), "FALSE");

        GuardianRecord r = GuardianMapper.fromLDAP(ldap);
        assertFalse(r.isGalHidden());
    }

    // --- pomocné metody ---

    private DataSQL createTestSQL() {
        DataSQL sql = new DataSQL();
        sql.put(EBakaSQL.F_GUA_BK_ID.basename(), "99001");
        sql.put(EBakaSQL.F_GUA_BK_SURNAME.basename(), "Nováková");
        sql.put(EBakaSQL.F_GUA_BK_GIVENNAME.basename(), "Jana");
        sql.put(EBakaSQL.F_GUA_BK_MAIL.basename(), "novakova@email.cz");
        sql.put(EBakaSQL.F_GUA_BK_MOBILE.basename(), "777888999");
        return sql;
    }

    private DataLDAP createTestLDAP() {
        DataLDAP ldap = new DataLDAP();
        ldap.put(EBakaLDAPAttributes.EXT01.attribute(), "99001");
        ldap.put(EBakaLDAPAttributes.NAME_LAST.attribute(), "Nováková");
        ldap.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), "Jana");
        ldap.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), "Nováková Jana");
        ldap.put(EBakaLDAPAttributes.MAIL.attribute(), "novakova@email.cz");
        ldap.put(EBakaLDAPAttributes.MOBILE.attribute(), "777888999");
        ldap.put(EBakaLDAPAttributes.DN.attribute(), "CN=Nováková Jana,OU=Kontakty,DC=skola,DC=local");
        ldap.put(EBakaLDAPAttributes.MSXCH_GAL_HIDDEN.attribute(), "TRUE");
        ldap.put(EBakaLDAPAttributes.MSXCH_REQ_AUTH.attribute(), "TRUE");
        return ldap;
    }
}
