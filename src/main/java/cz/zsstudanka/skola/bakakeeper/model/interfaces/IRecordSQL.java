package cz.zsstudanka.skola.bakakeeper.model.interfaces;

import cz.zsstudanka.skola.bakakeeper.constants.EBakaSQL;

/**
 * Interface záznamu v SQL.
 *
 * @author Jan Hladěna
 */
public interface IRecordSQL {

    /**
     * Získání interního ID z Bakalářů - položka INTERN_KOD.
     *
     * @return hodnota položky INTERN_KOD
     */
    String getInternalID();

    /**
     * Získání hodnoty zadané položky.
     *
     * @param field položka
     * @return hodnota
     */
    String getSQLdata(EBakaSQL field);

}
