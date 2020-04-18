package cz.zsstudanka.skola.bakakeeper.model.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kolekce ročníků.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Rocniky {

    /** ročníky */
    private Map<Integer, Rocnik> rocniky;

    public Rocniky(Integer[] r) {
        this.rocniky = new LinkedHashMap<Integer, Rocnik>();

        if (r.length > 0) {

            for (int rec = 0; rec < r.length; rec++) {

                // kontrola platnosti
                if (r[rec] >= 1 && r[rec] <= 9) {
                    this.rocniky.put(r[rec], new Rocnik(r[rec]));
                }

            } // fore

        } // if-0
    }

    public Rocnik get(Integer r) {
        if (this.rocniky.containsKey(r)) {
            return this.rocniky.get(r);
        }

        return null;
    }


}
