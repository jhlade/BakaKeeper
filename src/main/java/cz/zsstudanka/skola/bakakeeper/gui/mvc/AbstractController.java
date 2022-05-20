package cz.zsstudanka.skola.bakakeeper.gui.mvc;

/**
 * Generický řadič.
 *
 * @author Jan Hladěna
 */
public abstract class AbstractController {

    /** ukazatel na nadřízené okno */
    private final AbstractFrame mainFrame;

    /**
     * Konstruktor obecného řadiče.
     *
     * @param mainFrame hlavní okno
     */
    public AbstractController(AbstractFrame mainFrame) {
        this.mainFrame = mainFrame;
    }

    /**
     * Metoda pro zpřístupnění instance hlavního okna
     *
     * @return nadřízené okno
     */
    protected AbstractFrame getMainFrame() {
        return this.mainFrame;
    }
}
