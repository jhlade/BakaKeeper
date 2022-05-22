package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;

/**
 * Řízení stavového řádku programu.
 *
 * @author Jan Hladěna
 */
public class StatusBarController extends AbstractController {

    /** zpráva stavu */
    private String message = "";

    /**
     * Konstruktor obecného řadiče.
     *
     * @param mainFrame hlavní okno
     */
    public StatusBarController(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Získání zprávy stavu.
     *
     * @return stavová zpráva
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Nastavení zprávy stavu.
     *
     * @param message nová stavová zpráva
     */
    public void setMessage(String message) {
        this.message = message;
    }
}
