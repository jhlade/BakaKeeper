package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.SyncRule;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;

import java.util.List;

/**
 * Služba pro aplikaci deklarativních pravidel synchronizace.
 *
 * @author Jan Hladěna
 */
public interface RuleService {

    /**
     * Aplikuje sadu deklarativních pravidel na žákovské účty.
     *
     * @param rules pravidla z konfigurace
     * @param students LDAP žákovské účty
     * @param repair provést zápis (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return seznam výsledků
     */
    List<SyncResult> applyRules(List<SyncRule> rules, List<StudentRecord> students,
                                 boolean repair, SyncProgressListener listener);
}
