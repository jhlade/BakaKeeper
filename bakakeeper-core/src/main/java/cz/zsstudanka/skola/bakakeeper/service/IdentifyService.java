package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Služba pro identifikaci uživatelských účtů v Active Directory.
 * Podporuje dotazy na třídy (počet žáků, třídní učitel) i jednotlivce
 * (kompletní detail včetně proxyAddresses, skupin, zástupce).
 *
 * @author Jan Hladěna
 */
public interface IdentifyService {

    /**
     * Identifikuje účty podle zadaného dotazu.
     * Dotaz může být třída ({@code 5.A}), ročník ({@code 5}), všechny ({@code *}),
     * individuální login ({@code novak.tomas}) nebo kombinace oddělená čárkou.
     *
     * @param query dotaz (viz {@link RangeSelector#parse(String)})
     * @return seznam výsledků identifikace
     */
    List<IdentifyResult> identify(String query);
}
