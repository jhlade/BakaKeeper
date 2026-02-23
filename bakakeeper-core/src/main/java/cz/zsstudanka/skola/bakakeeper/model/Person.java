package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

/**
 * Abstraktní základ pro všechny typy osob v systému.
 * Obsahuje společná pole sdílená mezi žáky, zákonnými zástupci, učiteli a absolventy.
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public abstract class Person {

    /** interní kód v evidenci Bakaláři (INTERN_KOD), uložen i v extensionAttribute1 */
    private String internalId;

    /** příjmení */
    private String surname;

    /** křestní jméno */
    private String givenName;

    /** zobrazované jméno */
    private String displayName;

    /** e-mailová adresa */
    private String email;

    /** distinguished name v Active Directory */
    private String dn;

    /** záznam je spárován (SQL ↔ LDAP) */
    private boolean paired;

    /** záznam obsahuje pouze parciální data (jen SQL nebo jen LDAP) */
    private boolean partial;
}
