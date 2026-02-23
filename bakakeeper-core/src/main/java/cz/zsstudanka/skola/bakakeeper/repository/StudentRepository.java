package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;

/**
 * Repository pro přístup k SQL datům žáků z evidence Bakaláři.
 *
 * @author Jan Hladěna
 */
public interface StudentRepository {

    /**
     * Nalezne všechny aktivní žáky, volitelně filtrované podle ročníku a/nebo písmena třídy.
     *
     * @param classYear ročník (null = všechny)
     * @param classLetter písmeno třídy (null = všechna)
     * @return seznam žáků
     */
    List<StudentRecord> findActive(Integer classYear, String classLetter);

    /**
     * Nalezne žáka podle interního kódu.
     *
     * @param internalId interní kód (INTERN_KOD)
     * @return záznam žáka, nebo null
     */
    StudentRecord findByInternalId(String internalId);

    /**
     * Zapíše e-mail žáka zpět do SQL evidence.
     *
     * @param internalId interní kód žáka
     * @param email nová e-mailová adresa
     * @return úspěch operace
     */
    boolean updateEmail(String internalId, String email);
}
