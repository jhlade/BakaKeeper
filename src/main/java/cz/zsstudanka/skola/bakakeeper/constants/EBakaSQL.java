package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * SQL pole používaná v aplikaci Bakaláři.
 *
 * @author Jan Hladěna
 */
public enum EBakaSQL {

    // tables
    TBL_STU("dbo.zaci", "Tabulka s daty žáků."),
    TBL_FAC("dbo.ucitele", "Tabulka s daty učitelů."), // TODO kontrola
    TBL_GUA("dbo.zaci_zzd", "Tabulka s daty zákonných zástupců žáků."),
    TBL_STU_GUA("dbo.zaci_zzr", "Tabulka s definicí vztahů zákonných zástupců k žákům."),

    // fields - Student
    F_STU_ID(TBL_STU.field + "." + "INTERN_KOD", "Interní kód žáka."),
    F_STU_SURNAME(TBL_STU.field + "." + "PRIJMENI", "Příjmení žáka."),
    F_STU_GIVENNAME(TBL_STU.field + "." + "JMENO", "Jméno žáka."),
    F_STU_CLASS(TBL_STU.field + "." + "TRIDA", "Třída žáka ve tvaru X.Y."),
    F_STU_BK_CLASSYEAR("B_ROCNIK", "Ročník žáka."),
    F_STU_BK_CLASSLETTER("B_TRIDA", "Písmeno třídy žáka."),

    // fields - Guardian
    F_GUA_ID(TBL_GUA.field + "." + "ID", "Interní kód zákonného zástupce."),
    F_GUA_SURNAME(TBL_GUA.field + "." + "PRIJMENI", "Příjmení zákonného zástupce."),
    F_GUA_GIVENNAME(TBL_GUA.field + "." + "JMENO", "Jméno zákonného zástupce."),
    F_GUA_MOBILE(TBL_GUA.field + "." + "TEL_MOBIL", "Mobilní telefon zákonného zástupce."),
    F_GUA_MAIL(TBL_GUA.field + "." + "E_MAIL", "E-mailová adresa zákonného zástupce."),

    // selector fields - aliases
    S_STU_BK_CLASSYEAR("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 1, 1) AS " + F_STU_BK_CLASSYEAR.field, "Výběr: ročník žáka."),
    S_STU_BK_CLASSLETTER("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 3, 1) AS " + F_STU_BK_CLASSLETTER.field, "Výběr: písmeno třídy žáka."),

    XX("", "");

    private final String field;
    private final String description;

    EBakaSQL(String field, String description) {
        this.field = field;
        this.description = description;
    }

    public String field() {
        return this.field;
    }

    public String description() {
        return (this.description != null) ? this.description : "";
    }
}
