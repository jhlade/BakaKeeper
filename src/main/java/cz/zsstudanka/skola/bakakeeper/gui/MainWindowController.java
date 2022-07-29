package cz.zsstudanka.skola.bakakeeper.gui;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;

/**
 * Controller hlavního okna.
 *
 * @author Jan Hladěna
 */
public class MainWindowController extends AbstractController {

    public MainWindowController(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * Okamžité zobrazení zprávy ve stavovém řádku.
     *
     * @param message nová stavová zpráva
     */
    public void updateGlobalStatusMessage(String message) {
        getMainFrame().getController(StatusBarController.class).setMessage(message);
        getMainFrame().getView(StatusBarView.class).showStatus();

        ReportManager.log(EBakaLogType.LOG_VERBOSE, "[ GUI ] Status: " + message);
    }

    /**
     * Ukončení grafické aplikace.
     */
    public void exitApp() {
        System.exit(0);
    }
}
