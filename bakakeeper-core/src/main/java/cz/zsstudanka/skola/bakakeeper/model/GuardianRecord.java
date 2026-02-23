package cz.zsstudanka.skola.bakakeeper.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * Záznam zákonného zástupce – typovaná entita slučující SQL evidenci a LDAP kontakt.
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class GuardianRecord extends Person {

    /** mobilní telefon */
    private String phone;

    /** skrytí v globálním adresáři (GAL) */
    private boolean galHidden;

    /** požadovaná autentizace pro příjem mailů */
    private boolean requireAuth;

    /** seznam DN distribučních skupin */
    private List<String> distributionLists = new ArrayList<>();
}
