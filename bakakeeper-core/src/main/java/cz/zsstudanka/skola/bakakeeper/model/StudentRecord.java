package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

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

    /**
     * Aktuální hodnoty atributů spravovaných pravidly (extensionAttribute3-15, title).
     * Naplněno z LDAP při fromLDAP() pro konvergentní rekonciliaci –
     * umožňuje zjistit, zda je potřeba atribut vyčistit.
     */
    private Map<String, String> ruleAttributes = new HashMap<>();

    /** interní ID primárního zákonného zástupce */
    private String guardianInternalId;

    /** příjmení zákonného zástupce (z SQL joinu) */
    private String guardianSurname;

    /** jméno zákonného zástupce (z SQL joinu) */
    private String guardianGivenName;

    /** telefon zákonného zástupce (z SQL joinu) */
    private String guardianPhone;

    /** e-mail zákonného zástupce (z SQL joinu) */
    private String guardianEmail;

    /** datum konce evidence (EVID_DO) */
    private String expired;

    /**
     * Vrátí aktuální hodnotu atributu spravovaného pravidly z LDAP.
     *
     * @param attrName název LDAP atributu (např. "extensionAttribute5")
     * @return aktuální hodnota, nebo null pokud atribut není nastaven
     */
    public String getRuleAttribute(String attrName) {
        return ruleAttributes != null ? ruleAttributes.get(attrName) : null;
    }
}
