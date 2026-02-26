package cz.zsstudanka.skola.bakakeeper.service;

/**
 * Validační chyba zákonného zástupce zjištěná při synchronizaci.
 * Používá se pro cílené hlášení třídnímu učiteli – učitel obdrží
 * seznam konkrétních chyb pro svou třídu.
 *
 * @param studentName jméno žáka (příjmení + jméno)
 * @param className název třídy (např. "5.A")
 * @param teacherEmail e-mail třídního učitele (pro směrování hlášení)
 * @param type typ chyby
 * @param detail doplňující informace (např. neplatná hodnota)
 *
 * @author Jan Hladěna
 */
public record GuardianValidationError(
        String studentName,
        String className,
        String teacherEmail,
        ErrorType type,
        String detail
) {

    /**
     * Typy validačních chyb zákonných zástupců.
     */
    public enum ErrorType {
        /** Chybí telefonní číslo. */
        MISSING_PHONE,
        /** Neplatný formát telefonního čísla. */
        INVALID_PHONE,
        /** Chybí e-mailová adresa. */
        MISSING_EMAIL,
        /** Neplatný formát e-mailové adresy. */
        INVALID_EMAIL,
        /** Žák nemá primárního zákonného zástupce. */
        NO_PRIMARY_GUARDIAN
    }

    @Override
    public String toString() {
        return className + " – " + studentName + ": " + type
                + (detail != null ? " (" + detail + ")" : "");
    }
}
