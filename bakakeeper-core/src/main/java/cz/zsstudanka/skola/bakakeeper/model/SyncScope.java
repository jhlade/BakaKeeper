package cz.zsstudanka.skola.bakakeeper.model;

/**
 * Rozsah synchronizace – definuje granularitu workflow.
 *
 * @author Jan Hladěna
 */
public enum SyncScope {

    /** jednotlivý žák/učitel */
    INDIVIDUAL("Jednotlivec"),

    /** celá třída (např. 5.A) */
    CLASS("Třída"),

    /** celý ročník (např. 5) */
    GRADE("Ročník"),

    /** 1. stupeň (ročníky 1–5) */
    LEVEL_1("1. stupeň"),

    /** 2. stupeň (ročníky 6–9) */
    LEVEL_2("2. stupeň"),

    /** všichni žáci */
    ALL_STUDENTS("Všichni žáci"),

    /** konkrétní uživatel dle přihlašovacího jména (sAMAccountName / UPN prefix) */
    USER("Uživatel"),

    /** kategorie uživatele (ZAK, UCITEL, VEDENI, PROVOZ, ASISTENT, VYCHOVATELKA) */
    CATEGORY("Kategorie"),

    /** stupeň školy – match "1" (ročníky 1–5) nebo "2" (ročníky 6–9) */
    LEVEL("Stupeň"),

    /** učitelé */
    TEACHERS("Učitelé"),

    /** celá škola (žáci + učitelé) */
    WHOLE_SCHOOL("Celá škola");

    private final String label;

    SyncScope(String label) {
        this.label = label;
    }

    /**
     * Popis rozsahu v českém jazyce.
     *
     * @return popisek rozsahu
     */
    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
