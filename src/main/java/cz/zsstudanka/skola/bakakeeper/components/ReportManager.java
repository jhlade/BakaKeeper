package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

/**
 * Protokolování postupu.
 *
 * @author Jan Hladěna
 */
public class ReportManager {

    // TODO -log soubor.log
    static String LOGFILE;

    /** singleton reportovacího nástroje */
    private static ReportManager instance = null;

    private String logfile;

    public ReportManager() {
    }

    public static ReportManager getInstance() {
        if (ReportManager.instance == null) {
            ReportManager.instance = new ReportManager();
        }

        return ReportManager.instance;
    }

    /**
     * Nastavení souboru pro protokolování.
     *
     * @param filename název souboru
     */
    public void setLogfile(String filename) {
        this.logfile = filename;

        if (App.FLAG_DEBUG) {
            System.out.println("[ DEBUG ] Byl nastaven soubor " + filename + " pro protokolování.");
        }
    }

    /**
     * TODO
     * @param message
     */
    static void out(String message) {

        System.out.println(message);
    }

    /**
     * TODO
     * @param message
     */
    static void err(String message) {

        System.err.println(message);
    }

}
