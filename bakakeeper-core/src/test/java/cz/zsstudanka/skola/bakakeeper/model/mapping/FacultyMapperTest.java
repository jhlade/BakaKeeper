package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro FacultyMapper – převod SQL/LDAP dat na typovaný FacultyRecord.
 *
 * @author Jan Hladěna
 */
class FacultyMapperTest {

    @Test
    void fromSQLnull() {
        assertNull(FacultyMapper.fromSQL(null));
    }

    @Test
    void fromLDAPnull() {
        assertNull(FacultyMapper.fromLDAP(null));
    }

    @Test
    void fromSQL() {
        DataSQL sql = createTestSQL();

        FacultyRecord r = FacultyMapper.fromSQL(sql);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("UC001", r.getInternalId());
        assertEquals("Dvořáková", r.getSurname());
        assertEquals("Marie", r.getGivenName());
        assertEquals("Dvořáková Marie", r.getDisplayName());
        assertEquals("dvorakova@skola.cz", r.getEmail());
        assertTrue(r.isActiveThisYear());
        assertNull(r.getDn());
    }

    @Test
    void fromSQLinactive() {
        DataSQL sql = createTestSQL();
        sql.put(EBakaSQL.F_FAC_ACTIVE.basename(), "0");

        FacultyRecord r = FacultyMapper.fromSQL(sql);
        assertFalse(r.isActiveThisYear());
    }

    @Test
    void fromLDAP() {
        DataLDAP ldap = createTestLDAP();

        FacultyRecord r = FacultyMapper.fromLDAP(ldap);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("UC001", r.getInternalId());
        assertEquals("Dvořáková", r.getSurname());
        assertEquals("Marie", r.getGivenName());
        assertEquals("dvorakova@skola.cz", r.getEmail());
        assertEquals("CN=Dvořáková Marie,OU=Ucitele,DC=skola,DC=local", r.getDn());
    }

    @Test
    void mergeBoth() {
        DataSQL sql = createTestSQL();
        DataLDAP ldap = createTestLDAP();

        FacultyRecord r = FacultyMapper.merge(sql, ldap);

        assertNotNull(r);
        assertFalse(r.isPartial());
        assertTrue(r.isPaired());

        assertEquals("UC001", r.getInternalId());
        assertEquals("Dvořáková", r.getSurname());
        assertEquals("dvorakova@skola.cz", r.getEmail());
        assertTrue(r.isActiveThisYear());
        assertEquals("CN=Dvořáková Marie,OU=Ucitele,DC=skola,DC=local", r.getDn());
    }

    @Test
    void mergeBothNull() {
        assertNull(FacultyMapper.merge(null, null));
    }

    @Test
    void mergeEmailFallback() {
        DataSQL sql = createTestSQL();
        sql.put(EBakaSQL.F_FAC_EMAIL.basename(), "");

        DataLDAP ldap = createTestLDAP();

        FacultyRecord r = FacultyMapper.merge(sql, ldap);
        assertEquals("dvorakova@skola.cz", r.getEmail());
    }

    // --- pomocné metody ---

    private DataSQL createTestSQL() {
        DataSQL sql = new DataSQL();
        sql.put(EBakaSQL.F_FAC_ID.basename(), "UC001");
        sql.put(EBakaSQL.F_FAC_SURNAME.basename(), "Dvořáková");
        sql.put(EBakaSQL.F_FAC_GIVENNAME.basename(), "Marie");
        sql.put(EBakaSQL.F_FAC_EMAIL.basename(), "dvorakova@skola.cz");
        sql.put(EBakaSQL.F_FAC_ACTIVE.basename(), "1");
        return sql;
    }

    private DataLDAP createTestLDAP() {
        DataLDAP ldap = new DataLDAP();
        ldap.put(EBakaLDAPAttributes.EXT01.attribute(), "UC001");
        ldap.put(EBakaLDAPAttributes.NAME_LAST.attribute(), "Dvořáková");
        ldap.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), "Marie");
        ldap.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), "Dvořáková Marie");
        ldap.put(EBakaLDAPAttributes.MAIL.attribute(), "dvorakova@skola.cz");
        ldap.put(EBakaLDAPAttributes.DN.attribute(), "CN=Dvořáková Marie,OU=Ucitele,DC=skola,DC=local");
        return ldap;
    }
}
