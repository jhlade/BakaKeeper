package cz.zsstudanka.skola.bakakeeper.gui.connection;

import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.gui.settings.SettingsController;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

/**
 * Řadič testování nastavení/spojení.
 *
 * @author Jan Hladěna
 */
public class ConnectionCheckController extends AbstractController {
    /**
     * Konstruktor obecného řadiče.
     *
     * @param mainFrame hlavní okno
     */
    public ConnectionCheckController(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    public void connectionCheckFirstResponder() {

        // TODO
        if (!Settings.getInstance().isValid()) {
            getMainFrame().getController(SettingsController.class).loadDataFirstResponder();
        }

    }
}
