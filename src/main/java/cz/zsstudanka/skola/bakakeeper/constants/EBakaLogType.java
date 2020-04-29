package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Typy protokolovaných zpráv.
 *
 * @author Jan Hladěna
 */
public enum EBakaLogType {
    LOG_OK("[ OK ]", false),
    LOG_TEST("[ TEST ]", false),
    LOG_INFO("[ INFO ]", false),
    LOG_ERR("[ CHYBA ]", true),
    LOG_WARN("[ POZOR ]", false),
    LOG_VERBOSE("[ INFO ]", false),
    LOG_ERR_VERBOSE("[ CHYBA ]", true),
    LOG_DEBUG("[ DEBUG ]", false),
    LOG_ERR_DEBUG("[ DEBUG ]", true),
    LOG_LDAP("[ DEBUG ] [ LDAP ]", false),
    LOG_SQL("[ DEBUG ] [ SQL ]", false),
    LOG_DEVEL("[ DEVEL ]", false);

    /** značka před zprávou */
    private final String tag;
    /** zpráva označuje chybový stav */
    private final Boolean isError;

    EBakaLogType (String tag, Boolean isError) {
        this.tag = tag;
        this.isError = isError;
    }

    public String tag() {
        return this.tag;
    }

    public Boolean isError() {
        return this.isError;
    }
}
