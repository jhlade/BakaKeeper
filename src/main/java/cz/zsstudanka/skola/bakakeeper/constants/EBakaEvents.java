package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Události pro protokolování a hlášení.
 *
 * @author Jan Hladěna
 */
public enum EBakaEvents {

    PRI_CHANGE("změna primárních údajů"),
    SEC_CHANGE("změna sekundárních údajů"),
    PWD_RESET("resetování hesla");

    private final String description;

    EBakaEvents(String description) {
        this.description = description;
    }
}
