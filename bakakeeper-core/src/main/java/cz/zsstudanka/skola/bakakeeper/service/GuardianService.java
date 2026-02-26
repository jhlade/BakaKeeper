package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.GuardianRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;
import java.util.Map;

/**
 * Služba pro správu kontaktů zákonných zástupců v AD.
 *
 * @author Jan Hladěna
 */
public interface GuardianService {

    /**
     * Synchronizuje kontakty zákonných zástupců podle evidence žáků.
     * Vytvoří chybějící, aktualizuje data, smaže osiřelé, nastaví distribuční skupiny.
     * Současně validuje kontaktní údaje (telefon, e-mail) a sbírá chyby.
     *
     * @param sqlStudents žáci z evidence (SQL) s údaji o zástupci
     * @param existingContacts stávající kontakty v AD
     * @param classTeacherEmails mapování třída → e-mail třídního učitele
     * @param repair provést zápis (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return výsledky synchronizace + validační chyby
     */
    GuardianSyncOutcome syncGuardians(List<StudentRecord> sqlStudents,
                                       List<GuardianRecord> existingContacts,
                                       Map<String, String> classTeacherEmails,
                                       boolean repair,
                                       SyncProgressListener listener);
}
