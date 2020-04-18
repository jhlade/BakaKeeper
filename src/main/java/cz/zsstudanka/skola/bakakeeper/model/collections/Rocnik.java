package cz.zsstudanka.skola.bakakeeper.model.collections;

import cz.zsstudanka.skola.bakakeeper.model.interfaces.IKolekceAD;
import cz.zsstudanka.skola.bakakeeper.model.interfaces.IUzivatelAD;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Ročník - kolekce tříd stejného ročníku.
 *
 * @author Jan Hladěna
 * @deprecated
 */
public class Rocnik implements IKolekceAD {

    // označení 1,2,3,4,5,6,7,8,9
    private Integer rocnik;

    // primární klíč písmeno A,B,C,D,E
    private Map<String, Trida> tridy;

    public String getCisloRocniku() {
        return this.rocnik.toString();
    }

    public Rocnik(Integer rocnik) {

        this.tridy = new HashMap<>();

        if (rocnik >= 1 && rocnik <= 9) {
            for (char tr = 'A'; tr <= 'E'; tr++) {
                this.tridy.put(String.valueOf(tr), new Trida(rocnik, String.valueOf(tr)));
            }
        }

        populate();
    }

    /**
     * Naplnění tříd v ročníku daty.
     */
    public void populate() {
        tridy.forEach((k, v) -> {
            v.populate();
        });
    }



    /**
     * Název odpovídající bezpečnostní skupiny pro tento ročník.
     *
     * @return řetězec ve formátu Rocnik-1
     */
    public String getADGroupName() {
        return "Rocnik-" + this.getCisloRocniku();
    }

    /**
     * Vygenerovaná cesta organziační jednotky v Active Directory
     *
     * @return suffix ve tvaru OU=Rocnik-1
     */
    public String getOU() {
        return "OU=Rocnik-" + getCisloRocniku();
    }

    /**
     * Nalezení kolize v kolekci postupným procházením tříd.
     *
     * @param ad_login
     * @return
     */
    @Override
    public IUzivatelAD findCollisions(String ad_login) {

        Iterator tridyIterator = this.tridy.entrySet().iterator();
        while (tridyIterator.hasNext()) {
            return (this.tridy.get((Map.Entry) tridyIterator.next()).findCollisions(ad_login));
        }

        return null;
    }

    @Override
    public Iterator<IUzivatelAD> iterator() {
        // TODO - sdružený iterátor?
        return null;
    }
}
