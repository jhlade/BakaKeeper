package cz.zsstudanka.skola.bakakeeper.model.collections;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kolekce tříd.
 *
 * Dynamická kolekce.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Tridy {

    // "1.A"
    private Map<String, Trida> tridy;


    public Tridy() {
        this.tridy = new LinkedHashMap<String, Trida>();
    }

    public void add(Trida trida) {
        this.tridy.put(trida.getDisplayName(), trida);
    }

    public Trida get(String trida) {
        if (this.tridy.containsKey(trida)) {
            return this.tridy.get(trida);
        }

        return null;
    }

    public void remove(Trida trida) {
        if (this.tridy.containsKey(trida.getDisplayName())) {
            this.tridy.remove(trida);
        }
    }

    public void remove(String trida) {
        if (this.tridy.containsKey(trida)) {
            this.tridy.remove(trida);
        }
    }

    public Map getTridy() {
        return this.tridy;
    }

}
