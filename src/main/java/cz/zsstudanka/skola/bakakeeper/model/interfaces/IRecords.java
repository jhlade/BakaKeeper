package cz.zsstudanka.skola.bakakeeper.model.interfaces;

import java.util.Iterator;

/**
 * Interface obecných kolekcí.
 *
 * @author Jan Hladěna
 */
public interface IRecords {

    /**
     * Získání instančního iterátoru.
     *
     * @return instanční iterátor
     */
    Iterator<String> iterator();

    /**
     * Reset instančního iterátoru.
     */
    void resetIterator();

    /**
     * Získání příznaku konkrétního objektu.
     *
     * @param key klíč objektu
     * @return příznak zpracování
     */
    Boolean getFlag(String key);

    /**
     * Nastavení příznaku zpracování konkrétního objektu.
     *
     * @param key klíč objektu
     * @param flag příznak
     */
    void setFlag(String key, Boolean flag);

}
