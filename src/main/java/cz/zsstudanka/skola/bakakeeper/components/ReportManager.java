package cz.zsstudanka.skola.bakakeeper.components;

import cz.zsstudanka.skola.bakakeeper.App;
import cz.zsstudanka.skola.bakakeeper.constants.EBakaLogType;
import cz.zsstudanka.skola.bakakeeper.settings.Settings;

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
            ReportManager.log(EBakaLogType.LOG_DEBUG, "Byl nastaven výstupní soubor " + filename + " pro protokolování.");
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
        log(EBakaLogType.LOG_INFO, message);
    }

    /**
     * Zápis zprávy do protokolu.
     *
     * @param type typ protokolované zprávy
     * @param message zpráva
     */
    public static void log(EBakaLogType type, String message) {
        ReportManager RM = getInstance();
        RM.println(message, type);
    }

    /**
     * Výpis lokalizované zprávy zachycené výjimky do protokolu.
     *
     * @param type typ protokolované zprávy
     * @param e zachycená výjimka
     */
    public static void exceptionMessage(EBakaLogType type, Exception e) {
        log(type, "Došlo k výjimce: " + e.getLocalizedMessage());
    }

    /**
     * Zkratka prop výpis lokalizované zprávy zachycené výjimky do protokolu
     * ve výchozím typu události - chybové detailní zprávy.
     *
     * @param e zachycená výjimka
     */
    public static void exceptionMessage(Exception e) {
        exceptionMessage(EBakaLogType.LOG_ERR_VERBOSE, e);
    }

    /**
     * Výpis zásobníku ze zachycené výjimky do protokolu událostí.
     *
     * @param type typ protokolované zprávy
     * @param e zachycená výjimka
     */
    public static void printStackTrace(EBakaLogType type, Exception e) {
        StringBuilder stackTraceLines = new StringBuilder();

        stackTraceLines.append(e.getLocalizedMessage() + ":");
        stackTraceLines.append("\n");

        for (StackTraceElement exceptionLine : e.getStackTrace()) {
            stackTraceLines.append("[ TRACE ] ");
            stackTraceLines.append(exceptionLine.toString());
            stackTraceLines.append("\n");
        }

        log(type, stackTraceLines.toString());
    }

    /**
     * Zkratka protokolování výpisu zásobníku zachycené výjimky pro výchozí
     * typ události - chybové ladící informace.
     *
     * @param e zachycená výjimka
     */
    public static void printStackTrace(Exception e) {
        printStackTrace(EBakaLogType.LOG_ERR_DEBUG, e);
    }

    /**
     * Univerzální metoda pro zpracování události výjimky.
     *
     * @param message chybová zpráva o stavu
     * @param e zachycená výjimka
     */
    public static void handleException(String message, Exception e) {
        handleException(message, e, false);
    }

    /**
     * Univerzální metoda pro zpracování události výjimky.
     *
     * @param message chybová zpráva o stavu
     * @param e zachycená výjimka
     * @param verboseOnly protokolovat pouze v případě detailní a ladící úrovně
     */
    public static void handleException(String message, Exception e, Boolean verboseOnly) {
        if (verboseOnly) {
            log(EBakaLogType.LOG_ERR, message);
        }

        if (Settings.getInstance().beVerbose()) {
            exceptionMessage(e);
        }

        if (Settings.getInstance().debugMode()) {
            printStackTrace(e);
        }
    }

}
