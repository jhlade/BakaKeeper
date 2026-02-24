package cz.zsstudanka.skola.bakakeeper.repository.impl;

import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.model.mapping.StudentMapper;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementace StudentRepository nad SQL konektorem Bakaláři.
 * Extrahuje logiku dotazování z původního SQLrecords.
 *
 * @author Jan Hladěna
 */
public class BakaStudentRepository implements StudentRepository {

    private final SQLConnector sql;

    /** sloupce vracené dotazem */
    private static final EBakaSQL[] COLUMNS = {
            EBakaSQL.F_STU_ID,
            EBakaSQL.F_STU_CLASS_ID,
            EBakaSQL.F_STU_SURNAME,
            EBakaSQL.F_STU_GIVENNAME,
            EBakaSQL.F_STU_CLASS,
            EBakaSQL.F_STU_MAIL,
            EBakaSQL.F_STU_BK_CLASSYEAR,
            EBakaSQL.F_STU_BK_CLASSLETTER,
            EBakaSQL.S_STU_BK_GUA_ID,
            EBakaSQL.S_STU_BK_GUA_SURNAME,
            EBakaSQL.S_STU_BK_GUA_GIVENNAME,
            EBakaSQL.S_STU_BK_GUA_MOBILE,
            EBakaSQL.S_STU_BK_GUA_MAIL,
    };

    public BakaStudentRepository(SQLConnector sql) {
        this.sql = sql;
    }

    @Override
    public List<StudentRecord> findActive(Integer classYear, String classLetter) {
        sql.connect();

        String query = buildStudentQuery(classYear, classLetter);
        List<StudentRecord> result = new ArrayList<>();

        try {
            ResultSet rs = sql.select(query);
            while (rs != null && rs.next()) {
                DataSQL row = mapRow(rs);
                StudentRecord record = StudentMapper.fromSQL(row);
                if (record != null) {
                    result.add(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Chyba při načítání žáků z SQL.", e);
        }

        return result;
    }

    @Override
    public StudentRecord findByInternalId(String internalId) {
        List<StudentRecord> all = findActive(null, null);
        return all.stream()
                .filter(s -> internalId.equals(s.getInternalId()))
                .findFirst()
                .orElse(null);
    }

    @Override
    public StudentRecord findByEmail(String email) {
        sql.connect();

        // dotaz se základním filtrem (aktivní žáci) + podmínka na e-mail
        String baseQuery = buildStudentQuery(null, null);
        // vloží podmínku na e-mail před ORDER BY
        String query = baseQuery.replace("ORDER BY",
                "AND " + EBakaSQL.F_STU_MAIL.field() + " = '"
                        + email.replace("'", "''") + "' ORDER BY");

        try {
            ResultSet rs = sql.select(query);
            if (rs != null && rs.next()) {
                DataSQL row = mapRow(rs);
                return StudentMapper.fromSQL(row);
            }
        } catch (Exception e) {
            throw new RuntimeException("Chyba při vyhledávání žáka podle e-mailu.", e);
        }

        return null;
    }

    @Override
    public boolean updateEmail(String internalId, String email) {
        sql.connect();

        String update = "UPDATE " + EBakaSQL.TBL_STU.field()
                + " SET " + EBakaSQL.F_STU_MAIL.field() + " = ?"
                + " WHERE " + EBakaSQL.F_STU_ID.field() + " = ?";

        try {
            sql.getConnection().setAutoCommit(false);
            try (PreparedStatement ps = sql.getConnection().prepareStatement(update)) {
                ps.setString(1, email);
                ps.setString(2, internalId);

                int affected = ps.executeUpdate();
                sql.getConnection().commit();
                return affected == 1;
            }
        } catch (Exception e) {
            try {
                sql.getConnection().rollback();
            } catch (Exception rollbackEx) {
                // rollback selhal – nemůžeme udělat víc
            }
            throw new RuntimeException("Chyba při zápisu e-mailu do SQL.", e);
        } finally {
            try {
                sql.getConnection().setAutoCommit(true);
            } catch (Exception ignored) {
                // obnovení autocommitu selhalo
            }
        }
    }

    /**
     * Sestaví SELECT dotaz pro žáky s volitelným filtrem ročníku/třídy.
     * Logika extrahována z původního SQLrecords konstruktoru.
     */
    public static String buildStudentQuery(Integer classYear, String classLetter) {
        StringBuilder sb = new StringBuilder("SELECT ");

        // sloupce
        sb.append(EBakaSQL.F_STU_ID.field()).append(", ");
        sb.append(EBakaSQL.F_STU_CLASS_ID.field()).append(", ");
        sb.append(EBakaSQL.F_STU_SURNAME.field()).append(", ");
        sb.append(EBakaSQL.F_STU_GIVENNAME.field()).append(", ");
        sb.append(EBakaSQL.F_STU_CLASS.field()).append(", ");
        sb.append(EBakaSQL.F_STU_MAIL.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_CLASSYEAR.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_CLASSLETTER.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_GUA_ID.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_GUA_SURNAME.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_GUA_GIVENNAME.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_GUA_MOBILE.field()).append(", ");
        sb.append(EBakaSQL.S_STU_BK_GUA_MAIL.field()).append(" ");

        // FROM + JOIN zákonný zástupce
        sb.append("FROM ").append(EBakaSQL.TBL_STU.field()).append(" ");
        sb.append("LEFT JOIN ").append(EBakaSQL.TBL_GUA.field()).append(" ");
        sb.append("ON (");
        sb.append(EBakaSQL.F_GUA_ID.field()).append(" = ");
        sb.append("(SELECT TOP 1 ").append(EBakaSQL.F_GS_GUAID.field()).append(" ");
        sb.append("FROM ").append(EBakaSQL.TBL_STU_GUA.field()).append(" ");
        sb.append("WHERE ").append(EBakaSQL.F_GS_STUID.field()).append(" = ").append(EBakaSQL.F_STU_ID.field()).append(" ");
        sb.append("AND (");
        sb.append(EBakaSQL.FS_GS_IS_GUA.field()).append(" = '").append(EBakaSQL.LIT_TRUE.field()).append("'");
        sb.append(" AND ");
        sb.append(EBakaSQL.FS_GS_IS_PRI.field()).append(" = '").append(EBakaSQL.LIT_TRUE.field()).append("'");
        sb.append("))) ");

        // WHERE – pouze aktivní žáci
        sb.append("WHERE ");
        sb.append(EBakaSQL.F_STU_CLASS.field()).append(" LIKE '[1-9].[A-E]' ");
        sb.append("AND ").append(EBakaSQL.F_STU_EXPIRED.field()).append(" IS NULL ");

        // filtr ročník/třída
        if (classYear != null || classLetter != null) {
            String year = (classYear == null) ? "%" : classYear.toString();
            String letter = (classLetter == null) ? "%" : classLetter;
            sb.append("AND ").append(EBakaSQL.F_STU_CLASS.field())
              .append(" LIKE '").append(year).append(".").append(letter).append("' ");
        }

        // řazení
        sb.append("ORDER BY ").append(EBakaSQL.F_STU_BK_CLASSYEAR.basename()).append(" DESC, ");
        sb.append(EBakaSQL.F_STU_BK_CLASSLETTER.basename()).append(" ASC, ");
        sb.append(EBakaSQL.F_STU_SURNAME.field()).append(" ASC, ");
        sb.append(EBakaSQL.F_STU_GIVENNAME.field()).append(" ASC;");

        return sb.toString();
    }

    /**
     * Mapuje jeden řádek ResultSet na DataSQL.
     */
    private DataSQL mapRow(ResultSet rs) throws Exception {
        DataSQL row = new DataSQL();
        for (EBakaSQL col : COLUMNS) {
            String val = rs.getString(col.basename());
            row.put(col.basename(), (val == null) ? EBakaSQL.NULL.basename() : val.trim());
        }
        return row;
    }
}
