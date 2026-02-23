package cz.zsstudanka.skola.bakakeeper.repository;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;

import java.util.List;

/**
 * Repository pro přístup k SQL datům vyučujících z evidence Bakaláři.
 *
 * @author Jan Hladěna
 */
public interface FacultyRepository {

    /**
     * Nalezne všechny aktivní vyučující, volitelně pouze třídní.
     *
     * @param classTeachersOnly pouze třídní učitelé
     * @return seznam vyučujících
     */
    List<FacultyRecord> findActive(boolean classTeachersOnly);
}
