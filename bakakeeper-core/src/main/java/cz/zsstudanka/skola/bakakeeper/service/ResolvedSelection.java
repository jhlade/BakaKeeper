package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;
import java.util.Map;

/**
 * Výsledek rozpoznání rozsahu výběru žáků.
 * Obsahuje žáky seskupené podle tříd, odpovídající třídní učitele
 * a seznam nerozpoznaných identifikátorů.
 *
 * @param studentsByClass žáci seskupení podle třídy (klíč = "5.A")
 * @param classTeachers   třídní učitelé (klíč = "5.A")
 * @param notFound        individuální identifikátory, které nebyly nalezeny v evidenci
 *
 * @author Jan Hladěna
 */
public record ResolvedSelection(
        Map<String, List<StudentRecord>> studentsByClass,
        Map<String, FacultyRecord> classTeachers,
        List<String> notFound
) {}
