package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;

/**
 * Služba pro správu kontaktů zákonných zástupců v AD.
 *
 * @author Jan Hladěna
 */
public interface GuardianService {

    /**
     * Synchronizuje kontakty zákonných zástupců podle evidence žáků.
     * Vytvoří chybějící, aktualizuje data, smaže osiřelé, nastaví distribuční skupiny.
     *
     * @param sqlStudents žáci z evidence (SQL) s údaji o zástupci
     * @param existingContacts stávající kontakty v AD
     * @param repair provést zápis (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> syncGuardians(List<StudentRecord> sqlStudents,
                                    List<GuardianRecord> existingContacts,
                                    boolean repair,
                                    SyncProgressListener listener);
}
