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
    LIT_MALE("M", "Pohlaví muž."),
    LIT_FEMALE("Z", "Pohlaví žena."),

    // literály pro označení cizího klíče webového přístupu v poli KOD1
    LIT_WEB_STUDENT("Z", "Kód pro označení webového účtu žáka."),
    LIT_WEB_FACULTY("V", "Kód pro označení webového účtu vyučujícího."),
    LIT_WEB_GUARDIAN("R", "Kód pro označení webového účtu rodiče (primárního zákonného zástupce)."),
    LIT_WEB_ADMIN("S", "Kód pro označení webového účtu správce."),
    LIT_WEB_SYSSIG("NOUSR", ""),

    LIT_LOGIN_UPD("H", "Literál pro typ aktualizace hesla webového přístupu."),
    LIT_LOGIN_ADMIN("*", "Název účtu správce systému."),
    LIT_DUMMY_ADMIN("ADMIN", "Univerzální označení pro účet správce."),

    BK_FLAG("baka_flag", "Pomocný virtuální parametr příznaku."),

    // tabulky
    TBL_STU("dbo.zaci", "Tabulka s daty žáků."),
    TBL_FAC("dbo.ucitele", "Tabulka s daty učitelů."),
    TBL_GUA("dbo.zaci_zzd", "Tabulka s daty zákonných zástupců žáků."),
    TBL_STU_GUA("dbo.zaci_zzr", "Tabulka s definicí vztahů zákonných zástupců k žákům."),
    TBL_WEB_LOGIN("dbo.webuser", "Tabulka s definicí přístupů uživatelů webové aplikace."),
    TBL_CLASS("dbo.tridy", "Tabulka s definicí tříd."),
    TBL_LOGIN("dbo.webuser", "Tabulka s interními uživateli."),

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
    F_GUA_BK_ID("ZZ_KOD", "Alias - ID zákonného zástupce."),
    F_GUA_BK_SURNAME("ZZ_PRIJMENI", "Alias - příjmení zákonného zástupce."),
    F_GUA_BK_GIVENNAME("ZZ_JMENO", "Alias - jméno zákonného zástupce."),
    F_GUA_BK_MAIL("ZZ_MAIL", "Alias - e-mailová adresa zákonného zástupce."),
    F_GUA_BK_MOBILE("ZZ_TELEFON", "Alias - mobilní telefon zákonného zástupce."),

    // pole - Faculty - učitel
    F_FAC_ID(TBL_FAC.field + "." + "INTERN_KOD", "Interní kód vyučujícího."),
    F_FAC_GIVENNAME(TBL_FAC.field + "." + "JMENO", "Jméno vyučujícího."),
    F_FAC_SURNAME(TBL_FAC.field + "." + "PRIJMENI", "Příjmneí vyučujícího."),
    F_FAC_EMAIL(TBL_FAC.field + "." + "E_MAIL", "E-mailová adresa vyučujícího."),
    F_FAC_ACTIVE(TBL_FAC.field + "." + "UCI_LETOS", "Příznak vyučujícího aktivního v aktuálním školním roce."),

    // pole - Class - třída
    F_CLASS_YEAR(TBL_CLASS.field + "." + "ROCNIK", "Ročník třídy."),
    F_CLASS_LABEL(TBL_CLASS.field + "." + "ZKRATKA", "Označení třídy výrazem ^[0-9]\\.[A-E]$."),
    F_CLASS_TEACHER(TBL_CLASS.field + "." + "TRIDNICTVI", "Cizí klíč (F_FAC_ID) - třídní učitel."),

    // pole - Student:Guardian
    F_GS_GUAID(TBL_STU_GUA.field + "." + "ID_ZZ", "ID zákonného zástupce."),
    F_GS_STUID(TBL_STU_GUA.field + "." + "INTERN_KOD", "ID žáka."),
    FS_GS_IS_GUA(TBL_STU_GUA.field + "." + "JE_ZZ", "Příznak, zda je kontakt zákonným zástupcem."),
    FS_GS_IS_PRI(TBL_STU_GUA.field + "." + "PRIMARNI", "Příznak, zda je kontakt nejbližší kontaktní osobou žáka."),

    // pole - Web
    F_WEB_USRID(TBL_WEB_LOGIN.field + "." + "INTERN_KOD", "ID uživatele."),
    F_WEB_LOGIN(TBL_WEB_LOGIN.field + "." + "LOGIN", "Přihlašovací jméno uživatatele do webové aplikace."),
    F_WEB_TYPE(TBL_WEB_LOGIN.field + "." + "KOD1", "Typ uživatelského účtu definovaný literálem R/S/V/Z."),
    F_WEB_MODIFIED(TBL_WEB_LOGIN.field + "." + "MODIFIED", "Datum poslední modifikace webového přístupu."),
    F_WEB_MODIFIEDBY(TBL_WEB_LOGIN.field + "." + "MODIFIEDBY", "Označení odpovědnosti  modifikace (FK nebo literál)."),
    // pole - interní uživatelé
    F_LOGIN_ID(TBL_LOGIN.field + "." + "INTERN_KOD", "ID uživatele."),
    F_LOGIN_LOGIN(TBL_LOGIN.field + "." + "LOGIN", "Přihlašovací jméno uživatatele."),
    F_LOGIN_TYPE(TBL_LOGIN.field + "." + "KOD1", "Typ uživatelského účtu definovaný literálem R/S/V/Z."),
    F_LOGIN_ACL(TBL_LOGIN.field + "." + "PRAVA", "Uživatelské oprávnění (1)."),
    F_LOGIN_UPDTYPE(TBL_LOGIN.field + "." + "UPD_TYP", "Typ poslední aktualizace záznamu (H = heslo)."),
    F_LOGIN_KODF(TBL_LOGIN.field + "." + "KODF", "Kód formuláře (interní identifikátor Bakalářů)."),
    F_LOGIN_PWD(TBL_LOGIN.field + "." + "HESLO", "B64 hash hesla uživatele."),
    F_LOGIN_PWD_MET(TBL_LOGIN.field + "." + "METODA", "Hashovací metoda."),
    F_LOGIN_PWD_SALT(TBL_LOGIN.field + "." + "SALT", "Sůl hashe."),
    F_LOGIN_MODIFIED(TBL_LOGIN.field + "." + "MODIFIED", "Datum poslední modifikace uživatele."),
    F_LOGIN_MODIFIEDBY(TBL_LOGIN.field + "." + "MODIFIEDBY", "Označení odpovědnosti  modifikace (FK nebo literál)."),

    // selektor pole - aliases
    S_STU_BK_CLASSYEAR("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 1, 1) AS " + F_STU_BK_CLASSYEAR.field, "Výběr: ročník žáka."),
    S_STU_BK_CLASSLETTER("SUBSTRING(" + TBL_STU.field + "." + "TRIDA, 3, 1) AS " + F_STU_BK_CLASSLETTER.field, "Výběr: písmeno třídy žáka."),
    S_STU_BK_GUA_ID(F_GUA_ID.field + " AS " + F_GUA_BK_ID.field, "Výběr: ID zákonného zástupce."),
    S_STU_BK_GUA_SURNAME(F_GUA_SURNAME.field + " AS " + F_GUA_BK_SURNAME.field, "Výběr: příjmení zákonného zástupce."),
    S_STU_BK_GUA_GIVENNAME(F_GUA_GIVENNAME.field + " AS " + F_GUA_BK_GIVENNAME.field, "Výběr: jméno zákonného zástupce."),
    S_STU_BK_GUA_MOBILE(F_GUA_MOBILE.field + " AS " + F_GUA_BK_MOBILE.field, "Výběr: telefon zákonného zástupce."),
    S_STU_BK_GUA_MAIL(F_GUA_MAIL.field + " AS " + F_GUA_BK_MAIL.field, "Výběr: e-mailová adresa zákonného zástupce."),

    // ostatní
    NULL("(NULL)", "Literál prázdných dat."),
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

            case TBL_FAC:
                return F_FAC_ID;

            case TBL_GUA:
                return F_GUA_ID;

            // TBL_STU_GUA (dbo.zaci_zzr) nemá jednoduchý PK – je to M:N relační tabulka
        }

        return null;
    }

    public String description() {
        return (this.description != null) ? this.description : "";
    }
}
