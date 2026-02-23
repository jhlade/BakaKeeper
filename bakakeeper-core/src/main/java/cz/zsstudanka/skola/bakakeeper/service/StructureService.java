package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Služba pro kontrolu a opravu základní AD struktury (OU a skupiny).
 *
 * Zajišťuje existenci organizačních jednotek, bezpečnostních skupin
 * a distribučních seznamů nutných pro správný běh synchronizace.
 *
 * @author Jan Hladěna
 */
public interface StructureService {

    /**
     * Kontrola a volitelná oprava kompletní AD struktury.
     *
     * Ověří existenci všech OU kontejnerů, bezpečnostních skupin
     * a distribučních seznamů. Pokud je repair=true, chybějící objekty vytvoří
     * a opraví nesprávné atributy existujících skupin.
     *
     * @param repair provést opravu (true) nebo jen kontrolu (false)
     * @param listener sledování průběhu
     * @return výsledky kontroly/opravy pro každý objekt
     */
    List<SyncResult> checkAndRepairStructure(boolean repair, SyncProgressListener listener);
}
