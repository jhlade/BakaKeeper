package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.model.entities.ZakonnyZastupce;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.util.HashMap;
import java.util.Map;

/**
 * Zákonní zástupci - kolekce.
 *
 *  Zákonný zástupce je objekt kontakt Active Directory.
 *  Zákonný zástupce je záznam na SQL Serveru.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class ZakonniZastupci {

    /** mapování ZZ_KOD, Zástupce */
    private Map<String, ZakonnyZastupce> zakonniZastupci;

    public ZakonniZastupci() {
        this.zakonniZastupci = new HashMap<String, ZakonnyZastupce>();
    }

    public ZakonniZastupci(Trida trida) {
        // TODO vyhledat podle jedné třídy
    }

    public ZakonniZastupci(Rocnik rocnik) {
        // TODO vyhledat podle celého ročníku
    }

    /**
     * Zákonný zástupce podel svého interního kódu.
     *
     * @param zz_kod
     * @return
     */
    public ZakonnyZastupce get(String zz_kod) {
        if (this.zakonniZastupci.containsKey(zz_kod)) {
            return this.zakonniZastupci.get(zz_kod);
        } else {
            if (Settings.getInstance().beVerbose()) {
                System.err.println("[ CHYBA ] Nebyl nalezen zákonný zástupce pro ID " + zz_kod);
            }

            return null;
        }
    }

    public void add(ZakonnyZastupce zz) {
        this.zakonniZastupci.put(zz.getZZ_kod(), zz);
    }

}
