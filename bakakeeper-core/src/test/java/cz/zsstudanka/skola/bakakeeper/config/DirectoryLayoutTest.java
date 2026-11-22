package cz.zsstudanka.skola.bakakeeper.config;

import cz.zsstudanka.skola.bakakeeper.config.DirectoryLayout.ClassRef;
import cz.zsstudanka.skola.bakakeeper.config.DirectoryLayout.GroupKind;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy konvencí pojmenování OU a skupin.
 *
 * @author Jan Hladěna
 */
class DirectoryLayoutTest {

    private static final String STUDENTS = "OU=Zaci,OU=Uzivatele,OU=Skola,DC=skola,DC=local";
    private static final String ALUMNI = "OU=StudiumUkonceno," + STUDENTS;

    private static DirectoryLayout parse(String yaml) {
        Object structure = ((Map<?, ?>) new Yaml().load(yaml)).get("structure");
        DirectoryLayout.ParseResult result = DirectoryLayout.parse(structure);
        assertEquals(java.util.List.of(), result.errors(), "neočekávané chyby");
        return result.layout();
    }

    private static java.util.List<String> errorsOf(String yaml) {
        Object structure = ((Map<?, ?>) new Yaml().load(yaml)).get("structure");
        return DirectoryLayout.parse(structure).errors();
    }

    // --- výchozí konvence (shodné s původními pevně danými názvy) ---

    @Test
    void vychoziOu() {
        DirectoryLayout l = DirectoryLayout.defaults();
        assertEquals("OU=Rocnik-5," + STUDENTS, l.gradeOuDn(5, STUDENTS));
        assertEquals("OU=Trida-B,OU=Rocnik-5," + STUDENTS, l.classOuDn(5, "b", STUDENTS));
        assertEquals("OU=2026," + ALUMNI, l.alumniYearOuDn("2026", ALUMNI));
    }

    @Test
    void vychoziSkupiny() {
        DirectoryLayout l = DirectoryLayout.defaults();
        assertEquals("Skupina-Zaci", l.groupCn(GroupKind.STUDENTS_GLOBAL));
        assertEquals("zaci-vsichni@skola.ext", l.groupMail(GroupKind.STUDENTS_GLOBAL, "skola.ext"));
        assertEquals("Zaci-Stupen-2", l.levelGroupCn(GroupKind.STUDENTS_LEVEL, 2));
        assertEquals("Ucitele-Tridni-Rocnik-7", l.gradeGroupCn(GroupKind.CLASS_TEACHERS_GRADE, 7));
        assertEquals("tridni-rocnik-7@skola.ext", l.gradeGroupMail(GroupKind.CLASS_TEACHERS_GRADE, 7, "skola.ext"));
        assertEquals("Zaci-Trida-5A", l.classGroupCn(GroupKind.STUDENTS_CLASS, 5, "a"));
        assertEquals("zaci-5a@skola.ext", l.classGroupMail(GroupKind.STUDENTS_CLASS, 5, "A", "skola.ext"));
        assertEquals("Rodice-Trida-9E", l.classGroupCn(GroupKind.PARENTS_CLASS, 9, "E"));
        assertEquals("Ucitele-Tridni-1C", l.classGroupCn(GroupKind.CLASS_TEACHERS_CLASS, 1, "C"));
    }

    @Test
    void chybejiciSekceJeVychozi() {
        DirectoryLayout.ParseResult r = DirectoryLayout.parse(null);
        assertTrue(r.errors().isEmpty());
        assertTrue(r.layout().isDefault());
        assertSame(DirectoryLayout.defaults(), DirectoryLayout.of(null));
    }

    @Test
    void explicitniVychoziHodnotyJsouVychozi() {
        // úplná sekce se shodnými hodnotami (jako produkční popis v settings) = výchozí konvence
        DirectoryLayout l = DirectoryLayout.parse(DirectoryLayout.defaults().toMap()).layout();
        assertTrue(l.isDefault());
    }

    // --- odvození třídy z DN ---

    @Test
    void tridaZDnVychozi() {
        DirectoryLayout l = DirectoryLayout.defaults();
        Optional<ClassRef> c = l.parseClass("CN=Novák Jan,OU=Trida-A,OU=Rocnik-6," + STUDENTS);
        assertTrue(c.isPresent());
        assertEquals(6, c.get().year());
        assertEquals("A", c.get().letter());
        assertEquals("6.A", c.get().label());
    }

    @Test
    void tridaZDnSCarkouVeJmene() {
        // escapovaná čárka v CN nesmí rozbít rozpoznání (původní poziční logika selhávala)
        assertEquals("3.C", DirectoryLayout.defaults()
                .classLabel("CN=Novák\\, Jan,OU=Trida-C,OU=Rocnik-3," + STUDENTS));
    }

    @Test
    void tridaZDnMimoTridu() {
        DirectoryLayout l = DirectoryLayout.defaults();
        String alumni = "CN=Novák Jan,OU=2025," + ALUMNI;
        assertTrue(l.parseClass(alumni).isEmpty());
        assertNull(l.classLetter(alumni));
        assertThrows(NumberFormatException.class, () -> l.classYear(alumni));
        assertTrue(l.parseClass(null).isEmpty());
    }

    // --- kategorie ---

    @Test
    void vychoziKategorie() {
        DirectoryLayout l = DirectoryLayout.defaults();
        assertTrue(l.matchesCategory("CN=X,OU=Trida-A,OU=Rocnik-1," + STUDENTS, "zak"));
        assertTrue(l.isStudentDn("CN=X,OU=Rocnik-1,DC=skola,DC=local"));
        assertTrue(l.matchesCategory("CN=X,OU=Ucitele,OU=Zamestnanci,DC=skola,DC=local", "UCITEL"));
        assertTrue(l.matchesCategory("CN=X,OU=Vychovatelky,OU=Zamestnanci,DC=skola,DC=local", "VYCHOVATELKA"));
        assertFalse(l.matchesCategory("CN=X,OU=Ucitele,OU=Zamestnanci,DC=skola,DC=local", "VEDENI"));
        assertFalse(l.matchesCategory("CN=X,OU=Ucitele,DC=skola,DC=local", "NEZNAMA"));
        assertFalse(l.matchesCategory(null, "ZAK"));
    }

    // --- vlastní konvence ---

    @Test
    void vlastniKonvence() {
        DirectoryLayout l = parse("""
                structure:
                  ou:
                    grade: "{rocnik}. rocnik"
                    class: "{rocnik}{trida}"
                    alumni_year: "Absolventi {rok}"
                  categories:
                    UCITEL: "Pedagogove"
                    ICT: "Technici"
                  groups:
                    students_class: { cn: "Trida {rocnik}.{trida}", mail: "trida-{rocnik}{trida}" }
                    parents_class:  { cn: "Rodice {rocnik}.{trida}" }
                """);

        assertFalse(l.isDefault());
        assertEquals("OU=5B,OU=5. rocnik," + STUDENTS, l.classOuDn(5, "b", STUDENTS));
        assertEquals("OU=Absolventi 2026," + ALUMNI, l.alumniYearOuDn("2026", ALUMNI));
        assertEquals("5.B", l.classLabel("CN=Žák,OU=5B,OU=5. rocnik," + STUDENTS));
        // ročník v OU třídy musí odpovídat OU ročníku
        assertTrue(l.parseClass("CN=Žák,OU=4B,OU=5. rocnik," + STUDENTS).isEmpty());
        // původní konvence už neplatí
        assertTrue(l.parseClass("CN=Žák,OU=Trida-B,OU=Rocnik-5," + STUDENTS).isEmpty());

        assertTrue(l.matchesCategory("CN=X,OU=Pedagogove,DC=skola,DC=local", "UCITEL"));
        assertFalse(l.matchesCategory("CN=X,OU=Ucitele,DC=skola,DC=local", "UCITEL"));
        assertTrue(l.matchesCategory("CN=X,OU=Technici,DC=skola,DC=local", "ict"));
        assertTrue(l.isStudentDn("CN=X,OU=5. rocnik,DC=skola,DC=local"));

        assertEquals("Trida 5.B", l.classGroupCn(GroupKind.STUDENTS_CLASS, 5, "b"));
        assertEquals("trida-5b@skola.ext", l.classGroupMail(GroupKind.STUDENTS_CLASS, 5, "B", "skola.ext"));
        // neuvedený mail zůstává výchozí
        assertEquals("Rodice 5.B", l.classGroupCn(GroupKind.PARENTS_CLASS, 5, "B"));
        assertEquals("rodice-trida-5b@skola.ext", l.classGroupMail(GroupKind.PARENTS_CLASS, 5, "B", "skola.ext"));
        // neuvedené skupiny zůstávají výchozí
        assertEquals("Skupina-Zaci", l.groupCn(GroupKind.STUDENTS_GLOBAL));
    }

    @Test
    void toMapZachovaVlastniKonvence() {
        DirectoryLayout l = parse("""
                structure:
                  ou:
                    grade: "R{rocnik}"
                  groups:
                    students_grade: { cn: "Zaci-R{rocnik}" }
                """);
        DirectoryLayout copy = DirectoryLayout.parse(l.toMap()).layout();
        assertEquals(l, copy);
    }

    // --- validace ---

    @Test
    void chybejiciZastupnySymbol() {
        assertFalse(errorsOf("""
                structure:
                  ou:
                    class: "Trida"
                """).isEmpty());
        assertFalse(errorsOf("""
                structure:
                  groups:
                    students_class: { cn: "Zaci-{rocnik}" }
                """).isEmpty());
    }

    @Test
    void nepovolenyZastupnySymbol() {
        assertFalse(errorsOf("""
                structure:
                  groups:
                    students_all: { cn: "Zaci-{rocnik}" }
                """).isEmpty());
        assertFalse(errorsOf("""
                structure:
                  ou:
                    grade: "Rocnik-{rocnik}-{neznamy}"
                """).isEmpty());
    }

    @Test
    void nepovoleneZnakyANeznameKlice() {
        assertFalse(errorsOf("""
                structure:
                  ou:
                    grade: "Rocnik,{rocnik}"
                """).isEmpty());
        assertFalse(errorsOf("""
                structure:
                  groups:
                    students_all: { mail: "zaci@skola.ext" }
                """).isEmpty());
        assertFalse(errorsOf("""
                structure:
                  neznamy: 1
                """).isEmpty());
        assertFalse(errorsOf("""
                structure:
                  groups:
                    neznama_skupina: { cn: "X" }
                """).isEmpty());
    }

    @Test
    void nevalidniPolozkaPouzijeVychozi() {
        Object structure = ((Map<?, ?>) new Yaml().load("""
                structure:
                  ou:
                    grade: "Bez symbolu"
                    class: "T-{trida}"
                """)).get("structure");
        DirectoryLayout.ParseResult r = DirectoryLayout.parse(structure);
        assertEquals(1, r.errors().size());
        assertEquals("Rocnik-{rocnik}", r.layout().gradeOuTemplate());
        assertEquals("T-{trida}", r.layout().classOuTemplate());
    }
}
