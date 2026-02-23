package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Záznam žáka – typovaná entita slučující data z SQL evidence a LDAP.
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class StudentRecord extends Person {

    /** třída ve tvaru "X.Y" (např. "5.A") */
    private String className;

    /** ročník (1–9) */
    private int classYear;

    /** písmeno třídy (A–E) */
    private String classLetter;

    /** číslo v třídním výkazu (C_TR_VYK) */
    private String classNumber;

    /** userPrincipalName (přihlašovací jméno s doménou) */
    private String upn;

    /** sAMAccountName (max 20 znaků, pre-Windows 2000) */
    private String samAccountName;

    /** UserAccountControl bitmask */
    private int uac;

    /** pracovní pozice / title (zobrazuje se v O365) */
    private String title;

    /** omezení příjmu externí pošty (extensionAttribute2 = TRUE) */
    private boolean extMailRestricted;

    /** interní ID primárního zákonného zástupce */
    private String guardianInternalId;

    /** datum konce evidence (EVID_DO) */
    private String expired;
}
