package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro SyncRule.
 *
 * @author Jan Hladěna
 */
class SyncRuleTest {

    @Test
    void constructorAndGetters_zpětněKompatibilní() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A", "extensionAttribute5", "TRUE");
        assertEquals(SyncScope.CLASS, rule.getScope());
        assertEquals("6.A", rule.getMatch());
        // zpětná kompatibilita – getAttribute()/getValue() vrací první atribut
        assertEquals("extensionAttribute5", rule.getAttribute());
        assertEquals("TRUE", rule.getValue());
        // vnitřně je to jednoprvkový seznam
        assertEquals(1, rule.getAttributes().size());
        assertTrue(rule.getGroups().isEmpty());
    }

    @Test
    void plnýKonstruktor() {
        List<SyncRuleAttribute> attrs = List.of(
                new SyncRuleAttribute("attr1", "val1"),
                new SyncRuleAttribute("attr2", "val2")
        );
        List<String> groups = List.of("CN=Skupina,DC=test,DC=local");

        SyncRule rule = new SyncRule(SyncScope.GRADE, "5", attrs, groups);
        assertEquals(SyncScope.GRADE, rule.getScope());
        assertEquals("5", rule.getMatch());
        assertEquals(2, rule.getAttributes().size());
        assertEquals("attr1", rule.getAttribute()); // první atribut
        assertEquals("val1", rule.getValue()); // první hodnota
        assertEquals(1, rule.getGroups().size());
    }

    @Test
    void defaultConstructor() {
        SyncRule rule = new SyncRule();
        assertNull(rule.getScope());
        assertNull(rule.getMatch());
        assertNull(rule.getAttribute()); // žádné atributy → null
        assertNull(rule.getValue());
    }

    @Test
    void setters() {
        SyncRule rule = new SyncRule();
        rule.setScope(SyncScope.GRADE);
        rule.setMatch("5");
        rule.setAttributes(List.of(new SyncRuleAttribute("attr", "val")));
        rule.setGroups(List.of("CN=G,DC=t,DC=l"));
        assertEquals(SyncScope.GRADE, rule.getScope());
        assertEquals("5", rule.getMatch());
        assertEquals("attr", rule.getAttribute());
        assertEquals("val", rule.getValue());
        assertEquals(1, rule.getGroups().size());
    }

    @Test
    void toStringReadable() {
        SyncRule rule = new SyncRule(SyncScope.TEACHERS, "all", "ext5", "YES");
        String str = rule.toString();
        assertTrue(str.contains("Učitelé"));
        assertTrue(str.contains("ext5"));
        assertTrue(str.contains("YES"));
    }

    @Test
    void toStringSeSkupinami() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A",
                List.of(new SyncRuleAttribute("attr", "val")),
                List.of("CN=G1,DC=t", "CN=G2,DC=t"));
        String str = rule.toString();
        assertTrue(str.contains("Třída"));
        assertTrue(str.contains("2 skupin"));
    }

    @Test
    void syncRuleAttribute_toString() {
        SyncRuleAttribute attr = new SyncRuleAttribute("extensionAttribute5", "Zaci");
        assertEquals("extensionAttribute5 = Zaci", attr.toString());
    }
}
