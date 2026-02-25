package cz.zsstudanka.skola.bakakeeper.service;

import java.util.List;

/**
 * Výsledek identifikace – buď souhrn tříd, detail jednotlivce, nebo nenalezený.
 *
 * @author Jan Hladěna
 */
public sealed interface IdentifyResult
        permits IdentifyResult.ClassSummaryResult,
                IdentifyResult.IndividualDetailResult,
                IdentifyResult.NotFoundResult {

    /**
     * Souhrn tříd – pro dotazy typu *, 5, 5.A.
     *
     * @param classes    seznam souhrnů jednotlivých tříd
     * @param totalCount celkový počet žáků napříč třídami
     */
    record ClassSummaryResult(
            List<ClassInfo> classes,
            int totalCount
    ) implements IdentifyResult {}

    /**
     * Souhrn jedné třídy.
     *
     * @param classLabel   označení třídy ("5.A")
     * @param studentCount počet žáků
     * @param teacherName  jméno třídního učitele (null = nemá)
     * @param teacherEmail e-mail třídního učitele (null = nemá)
     */
    record ClassInfo(
            String classLabel,
            int studentCount,
            String teacherName,
            String teacherEmail
    ) {}

    /**
     * Detail individuálního žáka / uživatele.
     *
     * @param surname         příjmení
     * @param givenName       křestní jméno
     * @param displayName     zobrazované jméno
     * @param email           e-mail / UPN
     * @param className       třída ("5.A")
     * @param classNumber     číslo v třídním výkazu
     * @param defaultPassword výchozí heslo (null pokud nelze určit)
     * @param dn              distinguished name
     * @param lastLogon       poslední přihlášení (Windows FILETIME ticks, null = nikdy)
     * @param pwdLastSet      poslední změna hesla (Windows FILETIME ticks)
     * @param uac             UserAccountControl bitmask
     * @param accountType     typ účtu (žák/vyřazený/učitel/zaměstnanec)
     * @param proxyAddresses  seznam proxy adres
     * @param groups          seznam názvů skupin (CN)
     * @param guardianName    jméno zákonného zástupce (null = není)
     * @param guardianEmail   e-mail zákonného zástupce
     * @param guardianPhone   telefon zákonného zástupce
     * @param teacherName     jméno třídního učitele (null = není / neznámý)
     * @param teacherEmail    e-mail třídního učitele
     */
    record IndividualDetailResult(
            String surname,
            String givenName,
            String displayName,
            String email,
            String className,
            String classNumber,
            String defaultPassword,
            String dn,
            Long lastLogon,
            Long pwdLastSet,
            int uac,
            String accountType,
            List<String> proxyAddresses,
            List<String> groups,
            String guardianName,
            String guardianEmail,
            String guardianPhone,
            String teacherName,
            String teacherEmail
    ) implements IdentifyResult {}

    /**
     * Uživatel nebyl nalezen.
     *
     * @param query   původní dotaz
     * @param message popis chyby
     */
    record NotFoundResult(
            String query,
            String message
    ) implements IdentifyResult {}
}
