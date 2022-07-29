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

    /** zámek nastavení */
    private boolean statusLock = true;

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

    /**
     * Nastavení je zamčeno.
     *
     * @return příznak uzamčení
     */
    public boolean isLocked() {
        return this.statusLock;
    }

    /**
     * Uzamčení stavového zámku.
     */
    public void settingsLock() {
        setStatusLock(true);
    }

    /**
     * Odemčení stavového zámku.
     */
    public void settingsUnlock() {
        setStatusLock(false);
    }

    /**
     * Natsavení stavového zámku.
     *
     * @param lock příznak uzamčení
     */
    private void setStatusLock(boolean lock) {
        this.statusLock = lock;
    }
}
