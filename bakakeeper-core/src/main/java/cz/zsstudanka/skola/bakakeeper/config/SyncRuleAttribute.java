package cz.zsstudanka.skola.bakakeeper.config;

/**
 * Dvojice LDAP atributu a hodnoty v rámci deklarativního pravidla.
 *
 * Příklad v YAML:
 * <pre>
 * attributes:
 *   - attribute: extensionAttribute5
 *     value: "Zaci"
 *   - attribute: title
 *     value: "Žák 6.A"
 * </pre>
 *
 * @param attribute název LDAP atributu (např. extensionAttribute5, title)
 * @param value     hodnota, na kterou se atribut nastaví
 * @author Jan Hladěna
 */
public record SyncRuleAttribute(String attribute, String value) {

    @Override
    public String toString() {
        return attribute + " = " + value;
    }
}
