package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;

import java.util.List;

/**
 * Služba pro synchronizaci distribučních skupin třídních učitelů.
 *
 * @author Jan Hladěna
 */
public interface FacultyService {

    /**
     * Synchronizuje distribuční skupiny třídních učitelů dle katalogu.
     *
     * @param classTeachers třídní učitelé z evidence
     * @param repair provést opravu (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> syncClassTeachers(List<FacultyRecord> classTeachers,
                                        boolean repair,
                                        SyncProgressListener listener);
}
