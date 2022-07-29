package cz.zsstudanka.skola.bakakeeper.gui.settings;

import cz.zsstudanka.skola.bakakeeper.components.ReportManager;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.gui.MainWindowController;
import cz.zsstudanka.skola.bakakeeper.gui.connection.ConnectionCheckView;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractController;
import cz.zsstudanka.skola.bakakeeper.gui.mvc.AbstractFrame;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

import java.io.File;

/**
 * Řízení nastavení a grafické inicializace.
 *
 * @author Jan Hladěna
 */
public class SettingsController extends AbstractController {

    // TODO dynamická jména z nastavení - Settings.java

    /** současný datový soubor */
    private String dataFileName = "./settings.dat";

    /** současný inicializační soubor */
    private String initFileName = "./settings.conf";

    /**
     * Konstruktor řadiče nastavení a grafické inicializace.
     *
     * @param mainFrame hlavní okno
     */
    public SettingsController(AbstractFrame mainFrame) {
        super(mainFrame);
    }

    /**
     * First responder -- LoadDataView
     *
     * Odpovědnost
     * - zjistit současný stav nastavení a případně přesměrovat na kontrolu spojení
     * - pokud nastavení není validní, provede se kontrola existence souboru nastavení
     * - pokud soubor existuje, proběhne přesměrování na odemčení
     * - pokud soubor neexistuje, proběhne přesměrování na výběr souboru nebo možnost nové inicializace
     */
    public void loadDataFirstResponder() {
        // TODO
        ReportManager.log("[ LOAD DATA View ] FirstResponder spuštěn");

        // nastavení je již otevřeno
        if (Settings.getInstance().isValid()) {
            getMainFrame().setContent(getMainFrame().getView(ConnectionCheckView.class).getContentPane());

            // TODO
            ReportManager.log("[ LOAD DATA View ] Data jsou validní, probíhá přesměrování.");

        } else {
            if (getMainFrame().getController(SettingsController.class).findDataFile()) {
                // soubor s nastavením je nalezen
                getMainFrame().setContent(getMainFrame().getView(SettingsLoadDataView.class).getContentPane());
            } else {
                // soubor s nastavením není nalezen
                getMainFrame().setContent(getMainFrame().getView(SettingsOpenFileView.class).getContentPane());
                getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("Datový soubor nebyl nalezen.");
            }
        }

    }

    /**
     * First responder -- initView
     *
     * Odpovědnost
     * -
     *
     */
    public void initFirstResponder() {
        // TODO
    }

    /**
     * Nastavení datového souboru.
     *
     * @param dataFileName datový soubor
     */
    public void setDataFileName(String dataFileName) {
        this.dataFileName = dataFileName;
        ReportManager.log(EBakaLogType.LOG_VERBOSE, "[ GUI ] Označený datový soubor: " + dataFileName);
    }

    /**
     * Aktuální datový soubor
     *
     * @return soubor nastavení
     */
    public String getDataFileName() {
        return this.dataFileName;
    }

    /**
     * Aktuální incializační soubor
     *
     * @return soubor počáteční konfigurace
     */
    public String getInitFileName() {
        return this.initFileName;
    }

    /**
     * Nastavení inicializačního souboru.
     *
     * @param initFileName inicializační soubor
     */
    public void setInitFileName(String initFileName) {
        this.initFileName = initFileName;
        ReportManager.log(EBakaLogType.LOG_VERBOSE, "[ GUI ] Označený inicializační soubor: " + initFileName);
    }

    /**
     * Ověření existence výchozího souboru nastavení.
     *
     * @return výchozí soubor existuje
     */
    public boolean findDataFile() {
        File testFile = new File(this.dataFileName);
        return testFile.isFile();
    }

    /**
     * Předání hesla ke konfiguraci.
     *
     * @param passphrase heslo ke konfiguraci
     */
    public void setPassphrase(char[] passphrase) {
        Settings.getInstance().setPassphrase(String.valueOf(passphrase));
    }

    /**
     * Načtení datového souboru.
     *
     * @return stav načtených dat
     */
    public boolean loadSettings() {
        Settings.getInstance().load(this.dataFileName);
        return Settings.getInstance().isValid();
    }

    /**
     * Invalidace nastavení a uzamčení programu.
     */
    public void unloadSettings() {
        // TODO - Settings.java invalidate()
        getMainFrame().getController(MainWindowController.class).updateGlobalStatusMessage("TODO: invalidace nastavení.");
        this.dataFileName = "./settings.dat";
        this.initFileName = "./settings.conf";
    }
}
