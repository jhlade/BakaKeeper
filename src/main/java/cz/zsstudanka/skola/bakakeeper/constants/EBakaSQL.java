package cz.zsstudanka.skola.bakakeeper.constants;

/**
 * SQL pole používaná v aplikaci Bakaláři.
 *
 * @author Jan Hladěna
 */
public enum EBakaSQL {

    // literály
    LIT_TRUE("1", "Logická hodnota PRAVDA."),
    LIT_FALSE("0", "Logická hodnota NEPRAVDA."),

    BK_FLAG("baka_flag", "Pomocný virtuální parametr příznaku."),

    // tabulky
    TBL_STU("dbo.zaci", "Tabulka s daty žáků."),
    TBL_FAC("dbo.ucitele", "Tabulka s daty učitelů."), // TODO kontrola
    TBL_GUA("dbo.zaci_zzd", "Tabulka s daty zákonných zástupců žáků."),
    TBL_STU_GUA("dbo.zaci_zzr", "Tabulka s definicí vztahů zákonných zástupců k žákům."),

    // pole - Student
    F_STU_ID(TBL_STU.field + "." + "INTERN_KOD", "Interní kód žáka."),
    F_STU_CLASS_ID(TBL_STU.field + "." + "C_TR_VYK", "Číslo žáka v třídním výkazu."),
    F_STU_SURNAME(TBL_STU.field + "." + "PRIJMENI", "Příjmení žáka."),
    F_STU_GIVENNAME(TBL_STU.field + "." + "JMENO", "Jméno žáka."),
    F_STU_CLASS(TBL_STU.field + "." + "TRIDA", "Třída žáka ve tvaru X.Y."),
    F_STU_MAIL(TBL_STU.field + "." + "E_MAIL", "E-mailová adresa žáka."),
    F_STU_EXPIRED(TBL_STU.field + "." + "EVID_DO", "Datum - konec platnosti evidence žáka."),
    F_STU_BK_CLASSYEAR("B_ROCNIK", "Ročník žáka."),
    F_STU_BK_CLASSLETTER("B_TRIDA", "Písmeno třídy žáka."),

    // pole - Guardian
    F_GUA_ID(TBL_GUA.field + "." + "ID", "Interní kód zákonného zástupce."),
    F_GUA_SURNAME(TBL_GUA.field + "." + "PRIJMENI", "Příjmení zákonného zástupce."),
    F_GUA_GIVENNAME(TBL_GUA.field + "." + "JMENO", "Jméno zákonného zástupce."),
    F_GUA_MOBILE(TBL_GUA.field + "." + "TEL_MOBIL", "Mobilní telefon zákonného zástupce."),
    F_GUA_MAIL(TBL_GUA.field + "." + "E_MAIL", "E-mailová adresa zákonného zástupce."),

    // pole - Student:Guardian
    F_GS_GUAID(TBL_STU_GUA.field + "." + "ID_ZZ", "ID zákonného zástupce."),
    F_GS_STUID(TBL_STU_GUA.field + "." + "INTERN_KOD", "ID žáka."),
    FS_GS_IS_GUA(TBL_STU_GUA.field + "." + "JE_ZZ", "Příznak, zda je kontakt zákonným zástupcem."),
    FS_GS_IS_PRI(TBL_STU_GUA.field + "." + "PRIMARNI", "Příznak, zda je kontakt nejbližší kontaktní osobou žáka."),

    // selektor pole - aliases
    S_STU_BK_CLASSYEAR("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 1, 1) AS " + F_STU_BK_CLASSYEAR.field, "Výběr: ročník žáka."),
    S_STU_BK_CLASSLETTER("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 3, 1) AS " + F_STU_BK_CLASSLETTER.field, "Výběr: písmeno třídy žáka."),
    S_STU_BK_GUA_ID(F_GUA_ID.field + " AS ZZ_KOD", "Výběr: ID zákonného zástupce."),
    S_STU_BK_GUA_SURNAME(F_GUA_SURNAME.field + " AS ZZ_PRIJMENI", "Výběr: příjmení zákonného zástupce."),
    S_STU_BK_GUA_GIVENNAME(F_GUA_GIVENNAME.field + " AS ZZ_JMENO", "Výběr: jméno zákonného zástupce."),
    S_STU_BK_GUA_MOBILE(F_GUA_MOBILE.field + " AS ZZ_TELEFON", "Výběr: telefon zákonného zástupce."),
    S_STU_BK_GUA_MAIL(F_GUA_MAIL.field + " AS ZZ_MAIL", "Výběr: e-mailová adresa zákonného zástupce."),

    DEBUG("DEBUG_FIELD", "Pro účely ladění.");

    private final String field;
    private final String description;

    EBakaSQL(String field, String description) {
        this.field = field;
        this.description = description;
    }

    public String field() {
        return this.field;
    }

    public String basename() {

        if (field.contains(" AS ")) {
            return field().split(" AS ")[field().split(" AS ").length - 1];
        }

        if (field.contains(".")) {
            return field().split("\\.")[field().split("\\.").length - 1];
        }

        return field();
    }

    public EBakaSQL primaryKey() {
        switch (this) {

            case TBL_STU:
                return F_STU_ID;

            // TODO
            /*
            case TBL_FAC:
                return F_FAC_ID; */

            case TBL_GUA:
                return F_GUA_ID;

            // TODO
            /*
            case TBL_STU_GUA:
                return F_STU_GUA_ID;  */
        }

        return null;
    }

    public String description() {
        return (this.description != null) ? this.description : "";
    }
}
