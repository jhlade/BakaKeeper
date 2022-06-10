package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Typy protokolovaných zpráv.
 *
 * @author Jan Hladěna
 */
public enum EBakaLogType {
    LOG_STDOUT("", false, EBakaVerbosityLevel.NORMAL), // prázdné stdout
    LOG_STDERR("", true, EBakaVerbosityLevel.NORMAL), // prázdné stderr
    LOG_OK("[ OK ]", false, EBakaVerbosityLevel.NORMAL),
    LOG_TEST("[ TEST ]", false, EBakaVerbosityLevel.NORMAL),
    LOG_INFO("[ INFO ]", false, EBakaVerbosityLevel.NORMAL),
    LOG_ERR("[ CHYBA ]", true, EBakaVerbosityLevel.NORMAL),
    LOG_WARN("[ POZOR ]", false, EBakaVerbosityLevel.NORMAL),
    LOG_VERBOSE("[ INFO ]", false, EBakaVerbosityLevel.VERBOSE),
    LOG_ERR_VERBOSE("[ CHYBA ]", true, EBakaVerbosityLevel.VERBOSE),
    LOG_DEBUG("[ DEBUG ]", false, EBakaVerbosityLevel.DEBUG),
    LOG_ERR_DEBUG("[ DEBUG ]", true, EBakaVerbosityLevel.DEBUG),
    LOG_LDAP("[ DEBUG ] [ LDAP ]", false, EBakaVerbosityLevel.DEVEL),
    LOG_SQL("[ DEBUG ] [ SQL ]", false, EBakaVerbosityLevel.DEVEL),
    LOG_DEVEL("[ DEVEL ]", false, EBakaVerbosityLevel.DEVEL);

    /** značka před zprávou */
    private final String tag;
    /** zpráva označuje chybový stav */
    private final Boolean isError;

    /** úroveň protokolování */
    private final EBakaVerbosityLevel verbosityLevel;

    EBakaLogType (String tag, Boolean isError, EBakaVerbosityLevel verbosityLevel) {
        this.tag = tag;
        this.isError = isError;
        this.verbosityLevel = verbosityLevel;
    }

    public String tag() {
        return this.tag;
    }

    public Boolean isError() {
        return this.isError;
    }

    public EBakaVerbosityLevel verbosityLevel() {
        return this.verbosityLevel;
    }
}
