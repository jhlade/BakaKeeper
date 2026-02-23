package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.model.SyncScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy pro SyncRule.
 *
 * @author Jan Hladěna
 */
class SyncRuleTest {

    @Test
    void constructorAndGetters() {
        SyncRule rule = new SyncRule(SyncScope.CLASS, "6.A", "extensionAttribute5", "TRUE");
        assertEquals(SyncScope.CLASS, rule.getScope());
        assertEquals("6.A", rule.getMatch());
        assertEquals("extensionAttribute5", rule.getAttribute());
        assertEquals("TRUE", rule.getValue());
    }

    @Test
    void defaultConstructor() {
        SyncRule rule = new SyncRule();
        assertNull(rule.getScope());
        assertNull(rule.getMatch());
    }

    @Test
    void setters() {
        SyncRule rule = new SyncRule();
        rule.setScope(SyncScope.GRADE);
        rule.setMatch("5");
        rule.setAttribute("attr");
        rule.setValue("val");
        assertEquals(SyncScope.GRADE, rule.getScope());
        assertEquals("5", rule.getMatch());
    }

    @Test
    void toStringReadable() {
        SyncRule rule = new SyncRule(SyncScope.TEACHERS, "all", "ext5", "YES");
        String str = rule.toString();
        assertTrue(str.contains("Učitelé"));
        assertTrue(str.contains("ext5"));
        assertTrue(str.contains("YES"));
    }
}
