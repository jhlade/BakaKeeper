package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import lombok.Getter;
import lombok.Setter;

/**
 * Deklarativní pravidlo synchronizace.
 * Definuje, jaký atribut nastavit pro daný rozsah/shodu.
 *
 * Příklad v YAML:
 * <pre>
 * - scope: CLASS
 *   match: "6.A"
 *   attribute: extensionAttribute5
 *   value: "TRUE"
 * </pre>
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class SyncRule {

    /** Rozsah pravidla (CLASS, GRADE, ROLE, ...). */
    private SyncScope scope;

    /** Filtr – hodnota pro shodu v rámci rozsahu (např. "6.A", "teacher"). */
    private String match;

    /** LDAP atribut, který pravidlo nastaví. */
    private String attribute;

    /** Hodnota, na kterou se atribut nastaví. */
    private String value;

    public SyncRule() {
    }

    public SyncRule(SyncScope scope, String match, String attribute, String value) {
        this.scope = scope;
        this.match = match;
        this.attribute = attribute;
        this.value = value;
    }

    @Override
    public String toString() {
        return scope + "(" + match + "): " + attribute + " = " + value;
    }
}
