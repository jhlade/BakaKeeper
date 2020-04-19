package cz.zsstudanka.skola.bakakeeper.model.interfaces;

import java.util.Iterator;
import java.util.Map;

/**
 * Interface obecných kolekcí.
 *
 * @author Jan Hladěna
 */
public interface IRecords {

    /**
     * Získání instannčího iterátoru.
     *
     * @return instanční iterátor kolekce
     */
    Iterator<Map.Entry<String, Map>> iterator();

    /**
     * Reset instančního iterátoru.
     */
    void resetIterator();

}
