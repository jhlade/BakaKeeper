package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * Události pro protokolování a hlášení.
 *
 * @author Jan Hladěna
 */
public enum EBakaEvents {

    STRUCTURE_REPAIR("Byla provedena oprava základní struktury"),
    CATALOG_INIT("Do evidence byly zapsány nové e-mailové adresy"),
    CATALOG_NULL_GUARDIAN("U následujících žáků nebyl nalezen platný kontakt na zákonné zástupce"),
    DIRECTORY_NEW_USER("Byly vytvořeny nové uživatelské účty"),
    DIRECTORY_NEW_CONTACT("Byly zapsány nové kontakty"),
    DIRECTORY_USER_REMOVE("Byly odstraněny neplatné uživatelské účty"),
    DIRECTORY_CONTACT_REMOVE("Byly odstraněny nepotřebné kontakty"),
    RECORD_PAIRED("Bylo provedeno párování záznamů"),
    CHANGE_PRIMARY("Byla provedena změna primárních údajů"),
    CHANGE_SECONDARY("Byla provedena změna sekundárních údajů"),
    RESET_PWD("Bylo provedeno resetování uživatelského hesla");

    private final String description;

    EBakaEvents(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }
}
