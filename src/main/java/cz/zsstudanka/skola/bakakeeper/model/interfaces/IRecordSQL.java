package cz.zsstudanka.skola.bakakeeper.model.interfaces;

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

}
