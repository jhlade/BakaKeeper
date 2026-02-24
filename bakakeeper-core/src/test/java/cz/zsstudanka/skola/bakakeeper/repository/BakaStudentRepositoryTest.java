package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.impl.BakaStudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testy pro BakaStudentRepository.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class BakaStudentRepositoryTest {

    @Mock SQLConnector sql;
    @Mock ResultSet rs;
    @Mock Connection conn;
    @Mock PreparedStatement ps;

    private BakaStudentRepository repo;

    @BeforeEach
    void setUp() {
        repo = new BakaStudentRepository(sql);
    }

    @Test
    void queryContainsStudentTable() {
        // ověření, že generovaný SQL dotaz obsahuje tabulku žáků
        String query = BakaStudentRepository.buildStudentQuery(null, null);
        assertTrue(query.contains(EBakaSQL.TBL_STU.field()));
        assertTrue(query.contains("LEFT JOIN"));
        assertTrue(query.contains("IS NULL")); // EVID_DO IS NULL = aktivní žáci
    }

    @Test
    void queryFiltersByClass() {
        String query = BakaStudentRepository.buildStudentQuery(5, "A");
        assertTrue(query.contains("LIKE '5.A'"));
    }

    @Test
    void queryFiltersByGradeOnly() {
        String query = BakaStudentRepository.buildStudentQuery(3, null);
        assertTrue(query.contains("LIKE '3.%'"));
    }

    @Test
    void queryFiltersByLetterOnly() {
        String query = BakaStudentRepository.buildStudentQuery(null, "B");
        assertTrue(query.contains("LIKE '%.B'"));
    }

    @Test
    void queryNoFilter() {
        String query = BakaStudentRepository.buildStudentQuery(null, null);
        // bez filtru by neměl obsahovat LIKE s číslem
        assertFalse(query.contains("LIKE '5."));
    }

    @Test
    void findActiveReturnsRecords() throws Exception {
        // mock: jeden žák v ResultSet
        when(sql.select(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(EBakaSQL.F_STU_ID.basename())).thenReturn("12345");
        when(rs.getString(EBakaSQL.F_STU_SURNAME.basename())).thenReturn("Novák");
        when(rs.getString(EBakaSQL.F_STU_GIVENNAME.basename())).thenReturn("Tomáš");
        when(rs.getString(EBakaSQL.F_STU_CLASS.basename())).thenReturn("5.A");
        when(rs.getString(EBakaSQL.F_STU_CLASS_ID.basename())).thenReturn("12");
        when(rs.getString(EBakaSQL.F_STU_MAIL.basename())).thenReturn("novak@skola.cz");
        when(rs.getString(EBakaSQL.F_STU_BK_CLASSYEAR.basename())).thenReturn("5");
        when(rs.getString(EBakaSQL.F_STU_BK_CLASSLETTER.basename())).thenReturn("A");
        when(rs.getString(EBakaSQL.F_GUA_BK_ID.basename())).thenReturn("99001");
        when(rs.getString(EBakaSQL.F_GUA_BK_SURNAME.basename())).thenReturn("Nováková");
        when(rs.getString(EBakaSQL.F_GUA_BK_GIVENNAME.basename())).thenReturn("Jana");
        when(rs.getString(EBakaSQL.F_GUA_BK_MOBILE.basename())).thenReturn("777888999");
        when(rs.getString(EBakaSQL.F_GUA_BK_MAIL.basename())).thenReturn("novakova@email.cz");

        List<StudentRecord> result = repo.findActive(null, null);

        assertEquals(1, result.size());
        assertEquals("12345", result.get(0).getInternalId());
        assertEquals("Novák", result.get(0).getSurname());
        assertEquals("Tomáš", result.get(0).getGivenName());
        assertEquals("5.A", result.get(0).getClassName());
        assertEquals(5, result.get(0).getClassYear());
        assertEquals("99001", result.get(0).getGuardianInternalId());
    }

    @Test
    void findActiveEmpty() throws Exception {
        when(sql.select(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        List<StudentRecord> result = repo.findActive(null, null);
        assertTrue(result.isEmpty());
    }

    @Test
    void findByEmailReturnsStudent() throws Exception {
        when(sql.select(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString(EBakaSQL.F_STU_ID.basename())).thenReturn("12345");
        when(rs.getString(EBakaSQL.F_STU_SURNAME.basename())).thenReturn("Novák");
        when(rs.getString(EBakaSQL.F_STU_GIVENNAME.basename())).thenReturn("Tomáš");
        when(rs.getString(EBakaSQL.F_STU_CLASS.basename())).thenReturn("5.A");
        when(rs.getString(EBakaSQL.F_STU_CLASS_ID.basename())).thenReturn("12");
        when(rs.getString(EBakaSQL.F_STU_MAIL.basename())).thenReturn("novak.tomas@skola.cz");
        when(rs.getString(EBakaSQL.F_STU_BK_CLASSYEAR.basename())).thenReturn("5");
        when(rs.getString(EBakaSQL.F_STU_BK_CLASSLETTER.basename())).thenReturn("A");
        when(rs.getString(EBakaSQL.F_GUA_BK_ID.basename())).thenReturn("99001");
        when(rs.getString(EBakaSQL.F_GUA_BK_SURNAME.basename())).thenReturn("Nováková");
        when(rs.getString(EBakaSQL.F_GUA_BK_GIVENNAME.basename())).thenReturn("Jana");
        when(rs.getString(EBakaSQL.F_GUA_BK_MOBILE.basename())).thenReturn("777888999");
        when(rs.getString(EBakaSQL.F_GUA_BK_MAIL.basename())).thenReturn("novakova@email.cz");

        StudentRecord result = repo.findByEmail("novak.tomas@skola.cz");

        assertNotNull(result);
        assertEquals("12345", result.getInternalId());
        assertEquals("novak.tomas@skola.cz", result.getEmail());

        // ověření, že dotaz obsahuje filtr na e-mail
        verify(sql).select(argThat(q -> q.contains("novak.tomas@skola.cz")));
    }

    @Test
    void findByEmailReturnsNullWhenNotFound() throws Exception {
        when(sql.select(anyString())).thenReturn(rs);
        when(rs.next()).thenReturn(false);

        StudentRecord result = repo.findByEmail("neexistuje@skola.cz");
        assertNull(result);
    }

    @Test
    void updateEmail() throws Exception {
        when(sql.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);
        when(ps.executeUpdate()).thenReturn(1);

        boolean success = repo.updateEmail("12345", "novy@skola.cz");

        assertTrue(success);
        verify(ps).setString(1, "novy@skola.cz");
        verify(ps).setString(2, "12345");
        verify(conn).commit();
    }
}
