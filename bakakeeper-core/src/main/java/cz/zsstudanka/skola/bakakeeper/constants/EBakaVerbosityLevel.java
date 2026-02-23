package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Úrovně podrobnosti protokolování.
 *
 * @author Jan Hladěna
 */
public enum EBakaVerbosityLevel {

    /** normální */
    NORMAL(0),
    /** s podrobnostmi */
    VERBOSE(1),
    /** ladění */
    DEBUG(2),
    /** vývojové detaily */
    DEVEL(3);

    private final int level;

    EBakaVerbosityLevel(int level) {
        this.level = level;
    }

    public int getLevel() {
        return this.level;
    }
}
