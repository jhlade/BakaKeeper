package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Deklarativní pravidlo synchronizace.
 * Definuje, jaké atributy nastavit a do jakých skupin přidat
 * uživatele odpovídající danému rozsahu.
 *
 * <p>Nový formát YAML (pole atributů a skupin):
 * <pre>
 * - scope: CLASS
 *   match: "6.A"
 *   attributes:
 *     - attribute: extensionAttribute5
 *       value: "Zaci"
 *     - attribute: title
 *       value: "Žák 6.A"
 *   groups:
 *     - "CN=Skupina-Zaci,OU=Uzivatele,OU=Skupiny,OU=Skola,DC=skola,DC=local"
 * </pre>
 *
 * <p>Zpětně kompatibilní formát (jeden atribut):
 * <pre>
 * - scope: CLASS
 *   match: "6.A"
 *   attribute: extensionAttribute5
 *   value: "Zaci"
 * </pre>
 *
 * @author Jan Hladěna
 */
@Getter
@Setter
public class SyncRule {

    /** Rozsah pravidla (CLASS, GRADE, USER, CATEGORY, LEVEL, ...). */
    private SyncScope scope;

    /** Filtr – hodnota pro shodu v rámci rozsahu (např. "6.A", "UCITEL", "pepa.novak"). */
    private String match;

    /** Pole LDAP atributů a hodnot k nastavení. */
    private List<SyncRuleAttribute> attributes;

    /** Pole DN skupin, do kterých přidat odpovídající uživatele. */
    private List<String> groups;

    public SyncRule() {
    }

    /**
     * Konstruktor pro zpětnou kompatibilitu – jeden atribut, žádné skupiny.
     */
    public SyncRule(SyncScope scope, String match, String attribute, String value) {
        this.scope = scope;
        this.match = match;
        this.attributes = List.of(new SyncRuleAttribute(attribute, value));
        this.groups = List.of();
    }

    /**
     * Plný konstruktor – pole atributů a skupin.
     */
    public SyncRule(SyncScope scope, String match,
                    List<SyncRuleAttribute> attributes, List<String> groups) {
        this.scope = scope;
        this.match = match;
        this.attributes = (attributes != null) ? List.copyOf(attributes) : List.of();
        this.groups = (groups != null) ? List.copyOf(groups) : List.of();
    }

    /**
     * Vrátí první atribut (zpětná kompatibilita).
     *
     * @return název prvního atributu, nebo null
     */
    public String getAttribute() {
        return (attributes != null && !attributes.isEmpty()) ? attributes.get(0).attribute() : null;
    }

    /**
     * Vrátí první hodnotu (zpětná kompatibilita).
     *
     * @return hodnota prvního atributu, nebo null
     */
    public String getValue() {
        return (attributes != null && !attributes.isEmpty()) ? attributes.get(0).value() : null;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(scope).append("(").append(match).append("): ");
        if (attributes != null && !attributes.isEmpty()) {
            sb.append(attributes.stream()
                    .map(SyncRuleAttribute::toString)
                    .collect(Collectors.joining(", ")));
        }
        if (groups != null && !groups.isEmpty()) {
            sb.append(" + ").append(groups.size()).append(" skupin");
        }
        return sb.toString();
    }
}
