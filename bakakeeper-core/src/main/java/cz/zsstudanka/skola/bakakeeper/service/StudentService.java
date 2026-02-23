package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;

/**
 * Služba pro správu žákovských účtů – inicializace, synchronizace, kontrola, vyřazení.
 *
 * @author Jan Hladěna
 */
public interface StudentService {

    /**
     * Inicializuje nové žákovské účty – pro žáky ze SQL bez LDAP účtu.
     *
     * @param sqlStudents žáci z evidence
     * @param ldapStudents existující LDAP účty
     * @param repair provést zápis (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> initializeNewStudents(List<StudentRecord> sqlStudents,
                                            List<StudentRecord> ldapStudents,
                                            boolean repair,
                                            SyncProgressListener listener);

    /**
     * Zkontroluje a srovná data mezi SQL a LDAP pro spárované žáky.
     * Kontroluje: příjmení, jméno, třídu, UAC příznaky, externí poštu.
     *
     * @param sqlStudents žáci z evidence
     * @param ldapStudents žáci z LDAP
     * @param repair provést opravu (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> syncStudentData(List<StudentRecord> sqlStudents,
                                      List<StudentRecord> ldapStudents,
                                      boolean repair,
                                      SyncProgressListener listener);

    /**
     * Vyřadí žáky, kteří mají LDAP účet, ale nejsou v evidenci.
     *
     * @param sqlStudents žáci z evidence
     * @param ldapStudents žáci z LDAP
     * @param repair provést vyřazení (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> retireOrphanedStudents(List<StudentRecord> sqlStudents,
                                             List<StudentRecord> ldapStudents,
                                             boolean repair,
                                             SyncProgressListener listener);

    /**
     * Vytvoří nový žákovský účet v AD.
     *
     * @param student žák z evidence (SQL)
     * @return výsledek operace
     */
    SyncResult createStudentAccount(StudentRecord student);

    /**
     * Vyřadí žákovský účet (přesun do absolventů).
     *
     * @param student žák z LDAP
     * @return výsledek operace
     */
    SyncResult retireStudent(StudentRecord student);
}
