package cz.zsstudanka.skola.bakakeeper.repository.impl;

import cz.zsstudanka.skola.bakakeeper.connectors.SQLConnector;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;
import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.entities.DataSQL;
import cz.zsstudanka.skola.bakakeeper.model.mapping.FacultyMapper;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Implementace FacultyRepository nad SQL konektorem Bakaláři.
 * Extrahuje logiku dotazování z původního SQLrecords konstruktoru pro učitele.
 *
 * @author Jan Hladěna
 */
public class BakaFacultyRepository implements FacultyRepository {

    private final SQLConnector sql;

    /** sloupce vracené dotazem */
    private static final EBakaSQL[] COLUMNS = {
            EBakaSQL.F_FAC_ID,
            EBakaSQL.F_FAC_SURNAME,
            EBakaSQL.F_FAC_GIVENNAME,
            EBakaSQL.F_FAC_EMAIL,
            EBakaSQL.F_CLASS_LABEL,
    };

    public BakaFacultyRepository(SQLConnector sql) {
        this.sql = sql;
    }

    @Override
    public List<FacultyRecord> findActive(boolean classTeachersOnly) {
        sql.connect();

        String query = buildFacultyQuery(classTeachersOnly);
        List<FacultyRecord> result = new ArrayList<>();

        try {
            ResultSet rs = sql.select(query);
            while (rs != null && rs.next()) {
                DataSQL row = mapRow(rs);
                FacultyRecord record = FacultyMapper.fromSQL(row);
                if (record != null) {
                    // classLabel z SQL joinu (ZKRATKA)
                    String classLabel = row.get(EBakaSQL.F_CLASS_LABEL.basename());
                    if (classLabel != null && !classLabel.equals(EBakaSQL.NULL.basename())) {
                        record.setClassLabel(classLabel);
                    }
                    // aktivní = vždy true (WHERE filtr)
                    record.setActiveThisYear(true);
                    result.add(record);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Chyba při načítání vyučujících z SQL.", e);
        }

        return result;
    }

    /**
     * Sestaví SELECT dotaz pro vyučující.
     * Logika extrahována z původního SQLrecords konstruktoru.
     */
    public static String buildFacultyQuery(boolean classTeachersOnly) {
        StringBuilder sb = new StringBuilder("SELECT ");

        sb.append(EBakaSQL.F_FAC_ID.field()).append(", ");
        sb.append(EBakaSQL.F_FAC_EMAIL.field()).append(", ");
        sb.append(EBakaSQL.F_FAC_GIVENNAME.field()).append(", ");
        sb.append(EBakaSQL.F_FAC_SURNAME.field()).append(", ");
        sb.append(EBakaSQL.F_CLASS_LABEL.field()).append(" ");

        sb.append("FROM ");

        if (classTeachersOnly) {
            // pouze třídní učitelé
            sb.append(EBakaSQL.TBL_FAC.field()).append(" RIGHT JOIN ").append(EBakaSQL.TBL_CLASS.field()).append(" ");
            sb.append("ON (").append(EBakaSQL.F_CLASS_TEACHER.field()).append(" = ").append(EBakaSQL.F_FAC_ID.field()).append(") ");
            sb.append("WHERE ");
            sb.append(EBakaSQL.F_CLASS_LABEL.field()).append(" LIKE '[1-9].[A-E]' ");
            sb.append("AND ").append(EBakaSQL.F_FAC_ACTIVE.field()).append(" = '").append(EBakaSQL.LIT_TRUE.field()).append("' ");
            sb.append("ORDER BY ").append(EBakaSQL.F_CLASS_LABEL.field()).append(" ASC;");
        } else {
            // všichni aktivní učitelé
            sb.append(EBakaSQL.TBL_CLASS.field()).append(" RIGHT JOIN ").append(EBakaSQL.TBL_FAC.field()).append(" ");
            sb.append("ON (").append(EBakaSQL.F_CLASS_TEACHER.field()).append(" = ").append(EBakaSQL.F_FAC_ID.field()).append(") ");
            sb.append("WHERE ");
            sb.append(EBakaSQL.F_FAC_ACTIVE.field()).append(" = '").append(EBakaSQL.LIT_TRUE.field()).append("' ");
            sb.append("AND (");
            sb.append(EBakaSQL.F_CLASS_LABEL.field()).append(" LIKE '[1-9].[A-E]' ");
            sb.append("OR ").append(EBakaSQL.F_CLASS_LABEL.field()).append(" IS NULL) ");
            sb.append("ORDER BY ").append(EBakaSQL.F_FAC_SURNAME.field()).append(" ASC;");
        }

        return sb.toString();
    }

    private DataSQL mapRow(ResultSet rs) throws Exception {
        DataSQL row = new DataSQL();
        for (EBakaSQL col : COLUMNS) {
            String val = rs.getString(col.basename());
            row.put(col.basename(), (val == null) ? EBakaSQL.NULL.basename() : val.trim());
        }
        return row;
    }
}
