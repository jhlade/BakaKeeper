package cz.zsstudanka.skola.bakakeeper.model.interfaces;

/**
 * Interface globálních a dynamických kolekcí uživatelů.
 *
 * @author Jan Hladěna
 *
 * @deprecated
 */
public interface IKolekceAD extends Iterable<IUzivatelAD> {

    /**
     * Detekce kolizí proti uvedenému klíči.
     *
     * @param id kolizní řetězec
     * @return shodný objekt (nebo null)
     */
    IUzivatelAD findCollisions(String id);

}
