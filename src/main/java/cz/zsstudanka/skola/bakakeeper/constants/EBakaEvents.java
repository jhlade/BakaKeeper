package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Události pro protokolování a hlášení.
 *
 * @author Jan Hladěna
 */
public enum EBakaEvents {

    RECORD_PAIRED("párování záznamů"),
    CHANGE_PRIMARY("změna primárních údajů"),
    CHANGE_SECONDARY("změna sekundárních údajů"),
    RESET_PWD("resetování hesla");

    private final String description;

    EBakaEvents(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
