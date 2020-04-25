package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Protokolování postupu, sběr informací, vytváření sestav.
 *
 * @author Jan Hladěna
 */
public class ReportManager {

    /** singleton reportovacího nástroje */
    private static ReportManager instance = null;

    /** výstupní soubor protokolu */
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
            System.out.println("[ DEBUG ] Byl nastaven výstupní soubor " + filename + " pro protokolování.");
        }
    }

    /**
     * Provedení zápisu protokolované zprávy.
     *
     * @param message zpráva
     * @param type typ zprávy
     */
    private void print(String message, EBakaLogType type) {

        StringBuilder messageBuilder = new StringBuilder();
        messageBuilder.append(type.tag());
        messageBuilder.append(" ");
        messageBuilder.append(message);

        // výpis na standardní výstupy
        if (type.isError()) {
            System.err.print(messageBuilder.toString());
        } else {
            System.out.print(messageBuilder.toString());
        }

        // použití log souboru
        if (this.logfile != null) {
            // časové razítko
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String timestamp = formatter.format(new Date());

            StringBuilder logBuilder = new StringBuilder();
            logBuilder.append(timestamp);
            logBuilder.append(" ");
            logBuilder.append(messageBuilder.toString());

            try {
                PrintWriter log = new PrintWriter(new FileWriter(this.logfile, true), true);
                log.print(logBuilder.toString());
                log.close();
            } catch (Exception e) {
                System.err.println("[ CHYBA ] Došlo k závažné chybě při pokusu o zápis do protokolu.");

                if (App.FLAG_VERBOSE) {
                    System.err.println("[ CHYBA ] " + e.getLocalizedMessage());
                }

                if (App.FLAG_DEBUG) {
                    e.printStackTrace(System.err);
                }
            }
        }
    }

    private void println(String message, EBakaLogType type) {
        print(message + "\n", type);
    }

    /**
     * Zápis informační zprávy do protokolu.
     *
     * @param message zpráva
     */
    public static void log(String message) {
        log(message, EBakaLogType.LOG_INFO);
    }

    /**
     * Zápis zprávy do protokolu.
     *
     * @param message zpráva
     * @param type typ protokolované zprávy
     */
    public static void log(String message, EBakaLogType type) {
        ReportManager RM = getInstance();
        RM.println(message, type);
    }

}
