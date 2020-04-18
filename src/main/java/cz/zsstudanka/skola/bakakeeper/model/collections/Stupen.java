package cz.zsstudanka.skola.bakakeeper.model.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stupeň - kolekce ročníků.
 *
 * @author Jan Hladěna
 *@deprecated
 */
public class Stupen {

    /** stupeň - pouze 1 nebo 2 */
    private Integer stupen;

    /** ročníky ve stupni */
    private Map<Integer, Rocniky> rocniky;

    public Stupen(Integer st) {

        if (st == 1 || st == 2) {

            this.stupen = st;
            this.rocniky = new LinkedHashMap<>();

            Integer[] roc = new Integer[]{};

            // 1. stupeň
            if (st == 1) {
                roc = new Integer[]{1, 2, 3, 4, 5};
            }

            // 2. stupeň
            if (st == 2) {
                roc = new Integer[]{6, 7, 8, 9};
            }

            this.rocniky.put(st, new Rocniky(roc));

        } // pouze 1. nebo 2. stupeň

    }


    public Rocniky get(Integer stupen) {
        if (this.rocniky.containsKey(stupen)) {
            return this.rocniky.get(stupen);
        }

        return null;
    }
}
