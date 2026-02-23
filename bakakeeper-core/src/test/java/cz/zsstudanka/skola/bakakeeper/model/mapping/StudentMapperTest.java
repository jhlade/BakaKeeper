package cz.zsstudanka.skola.bakakeeper.model.mapping;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaLDAPAttributes;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataLDAP;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro StudentMapper – převod SQL/LDAP dat na typovaný StudentRecord.
 *
 * @author Jan Hladěna
 */
class StudentMapperTest {

    @Test
    void fromSQLnull() {
        assertNull(StudentMapper.fromSQL(null));
    }

    @Test
    void fromLDAPnull() {
        assertNull(StudentMapper.fromLDAP(null));
    }

    @Test
    void fromSQL() {
        DataSQL sql = createTestSQL();

        StudentRecord r = StudentMapper.fromSQL(sql);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("12345", r.getInternalId());
        assertEquals("Novák", r.getSurname());
        assertEquals("Tomáš", r.getGivenName());
        assertEquals("Novák Tomáš", r.getDisplayName());
        assertEquals("novak.tomas@skola.cz", r.getEmail());
        assertEquals("5.A", r.getClassName());
        assertEquals(5, r.getClassYear());
        assertEquals("A", r.getClassLetter());
        assertEquals("12", r.getClassNumber());
        assertEquals("67890", r.getGuardianInternalId());
        assertNull(r.getDn());
    }

    @Test
    void fromLDAP() {
        DataLDAP ldap = createTestLDAP();

        StudentRecord r = StudentMapper.fromLDAP(ldap);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());

        assertEquals("12345", r.getInternalId());
        assertEquals("Novák", r.getSurname());
        assertEquals("Tomáš", r.getGivenName());
        assertEquals("Novák Tomáš", r.getDisplayName());
        assertEquals("novak.tomas@skola.cz", r.getEmail());
        assertEquals("CN=Novák Tomáš,OU=Trida-A,OU=Rocnik-5,OU=Zaci,DC=skola,DC=local", r.getDn());
        assertEquals("novak.tomas", r.getSamAccountName());
        assertEquals("novak.tomas@skola.local", r.getUpn());
        assertEquals("Žák", r.getTitle());
        assertEquals(512, r.getUac());
        assertTrue(r.isExtMailRestricted());
    }

    @Test
    void mergeBoth() {
        DataSQL sql = createTestSQL();
        DataLDAP ldap = createTestLDAP();

        StudentRecord r = StudentMapper.merge(sql, ldap);

        assertNotNull(r);
        assertFalse(r.isPartial());
        assertTrue(r.isPaired());

        // SQL je autoritativní pro jméno a ID
        assertEquals("12345", r.getInternalId());
        assertEquals("Novák", r.getSurname());
        assertEquals("Tomáš", r.getGivenName());
        assertEquals("5.A", r.getClassName());
        assertEquals("novak.tomas@skola.cz", r.getEmail());

        // LDAP doplňuje AD-specifická pole
        assertEquals("CN=Novák Tomáš,OU=Trida-A,OU=Rocnik-5,OU=Zaci,DC=skola,DC=local", r.getDn());
        assertEquals("novak.tomas", r.getSamAccountName());
        assertEquals("novak.tomas@skola.local", r.getUpn());
        assertEquals("Žák", r.getTitle());
        assertEquals(512, r.getUac());
        assertTrue(r.isExtMailRestricted());
    }

    @Test
    void mergeSQLonly() {
        StudentRecord r = StudentMapper.merge(createTestSQL(), null);

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());
        assertEquals("Novák", r.getSurname());
        assertNull(r.getDn());
    }

    @Test
    void mergeLDAPonly() {
        StudentRecord r = StudentMapper.merge(null, createTestLDAP());

        assertNotNull(r);
        assertTrue(r.isPartial());
        assertFalse(r.isPaired());
        assertEquals("Novák", r.getSurname());
        assertEquals("novak.tomas", r.getSamAccountName());
    }

    @Test
    void mergeBothNull() {
        assertNull(StudentMapper.merge(null, null));
    }

    @Test
    void mergeEmailFallback() {
        // SQL bez e-mailu → použije se LDAP
        DataSQL sql = createTestSQL();
        sql.put(EBakaSQL.F_STU_MAIL.basename(), "");

        DataLDAP ldap = createTestLDAP();

        StudentRecord r = StudentMapper.merge(sql, ldap);
        assertEquals("novak.tomas@skola.cz", r.getEmail());
    }

    @Test
    void extMailNotRestricted() {
        DataLDAP ldap = createTestLDAP();
        ldap.put(EBakaLDAPAttributes.EXT02.attribute(), "FALSE");

        StudentRecord r = StudentMapper.fromLDAP(ldap);
        assertFalse(r.isExtMailRestricted());
    }

    // --- pomocné metody pro vytváření testovacích dat ---

    private DataSQL createTestSQL() {
        DataSQL sql = new DataSQL();
        sql.put(EBakaSQL.F_STU_ID.basename(), "12345");
        sql.put(EBakaSQL.F_STU_SURNAME.basename(), "Novák");
        sql.put(EBakaSQL.F_STU_GIVENNAME.basename(), "Tomáš");
        sql.put(EBakaSQL.F_STU_MAIL.basename(), "novak.tomas@skola.cz");
        sql.put(EBakaSQL.F_STU_CLASS.basename(), "5.A");
        sql.put(EBakaSQL.F_STU_CLASS_ID.basename(), "12");
        sql.put(EBakaSQL.F_STU_EXPIRED.basename(), "");
        sql.put(EBakaSQL.F_STU_BK_CLASSYEAR.basename(), "5");
        sql.put(EBakaSQL.F_STU_BK_CLASSLETTER.basename(), "A");
        sql.put(EBakaSQL.F_GUA_BK_ID.basename(), "67890");
        return sql;
    }

    private DataLDAP createTestLDAP() {
        DataLDAP ldap = new DataLDAP();
        ldap.put(EBakaLDAPAttributes.EXT01.attribute(), "12345");
        ldap.put(EBakaLDAPAttributes.NAME_LAST.attribute(), "Novák");
        ldap.put(EBakaLDAPAttributes.NAME_FIRST.attribute(), "Tomáš");
        ldap.put(EBakaLDAPAttributes.NAME_DISPLAY.attribute(), "Novák Tomáš");
        ldap.put(EBakaLDAPAttributes.MAIL.attribute(), "novak.tomas@skola.cz");
        ldap.put(EBakaLDAPAttributes.DN.attribute(), "CN=Novák Tomáš,OU=Trida-A,OU=Rocnik-5,OU=Zaci,DC=skola,DC=local");
        ldap.put(EBakaLDAPAttributes.LOGIN.attribute(), "novak.tomas");
        ldap.put(EBakaLDAPAttributes.UPN.attribute(), "novak.tomas@skola.local");
        ldap.put(EBakaLDAPAttributes.TITLE.attribute(), "Žák");
        ldap.put(EBakaLDAPAttributes.UAC.attribute(), "512");
        ldap.put(EBakaLDAPAttributes.EXT02.attribute(), "TRUE");
        return ldap;
    }
}
