package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;

/**
 * Controller hlavního okna.
 *
 * @author Jan Hladěna
 */
public class MainWindowController extends AbstractController {

    // TODO model hlavního okna

    public MainWindowController(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Ukončení grafické aplikace.
     */
    public void exitApp() {
        System.exit(0);
    }
}
