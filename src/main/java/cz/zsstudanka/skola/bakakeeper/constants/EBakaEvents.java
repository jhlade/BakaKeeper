package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Události pro protokolování a hlášení.
 *
 * @author Jan Hladěna
 */
public enum EBakaEvents {

    PWD_RESET("resetování hesla");

    private final String description;

    EBakaEvents(String description) {
        this.description = description;
    }
}
