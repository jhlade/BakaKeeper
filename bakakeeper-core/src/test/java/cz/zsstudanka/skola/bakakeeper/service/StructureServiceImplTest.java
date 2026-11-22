package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.config.YamlAppConfig;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy definic AD struktury – výchozí konvence musí přesně odpovídat
 * původním pevně daným názvům (zpětná kompatibilita s produkčním AD).
 *
 * @author Jan Hladěna
 */
class StructureServiceImplTest {

    private static YamlAppConfig loadConfig(String structureYaml) {
        InputStream is = StructureServiceImplTest.class.getResourceAsStream("/test-config.yml");
        assertNotNull(is);
        Map<String, Object> data = new Yaml().load(is);
        if (structureYaml != null) {
            Map<String, Object> s = new Yaml().load(structureYaml);
            data.put("structure", s.get("structure"));
        }
        YamlAppConfig config = new YamlAppConfig(data);
        assertTrue(config.isValid(), "konfigurace musí být platná: " + config.getStructureErrors());
        return config;
    }

    /** Původní (pevně daná) definice OU – referenční implementace. */
    private static List<String> legacyOUs(YamlAppConfig c) {
        List<String> ous = new ArrayList<>();
        ous.add(c.getLdapBaseFaculty());
        ous.add(c.getLdapBaseStudents());
        for (int r = 1; r <= 9; r++) {
            String rocnikOu = "OU=Rocnik-" + r + "," + c.getLdapBaseStudents();
            ous.add(rocnikOu);
            for (char t = 'A'; t <= 'E'; t++) {
                ous.add("OU=Trida-" + t + "," + rocnikOu);
            }
        }
        ous.add(c.getLdapBaseAlumni());
        ous.add(c.getLdapBaseStudentGroups());
        ous.add(c.getLdapBaseGlobalGroups());
        ous.add(c.getLdapBaseContacts());
        ous.add(c.getLdapBaseDistributionLists());
        return ous;
    }

    /** Původní (pevně daná) definice skupin – referenční implementace [cn, ou, parent, mail]. */
    private static List<List<String>> legacyGroups(YamlAppConfig c) {
        List<List<String>> g = new ArrayList<>();
        String sg = c.getLdapBaseStudentGroups();
        String gg = c.getLdapBaseGlobalGroups();
        String dl = c.getLdapBaseDistributionLists();
        String md = c.getMailDomain();

        g.add(List.of("Skupina-Zaci", gg, "", "zaci-vsichni@" + md));
        g.add(List.of("Zaci-Vsichni", sg, "CN=Skupina-Zaci," + gg, "zaci@" + md));
        g.add(List.of("Rodice-Vsichni", dl, "", "rodice-cela-skola@" + md));
        g.add(List.of("Ucitele-Tridni", dl, "", "ucitele-tridni@" + md));
        for (int s = 1; s <= 2; s++) {
            g.add(List.of("Zaci-Stupen-" + s, sg, "CN=Zaci-Vsichni," + sg, "zaci-stupen-" + s + "@" + md));
            g.add(List.of("Rodice-Stupen-" + s, dl, "CN=Rodice-Vsichni," + dl, "rodice-stupen-" + s + "@" + md));
            g.add(List.of("Ucitele-Tridni-Stupen-" + s, dl, "CN=Ucitele-Tridni," + dl, "tridni-stupen-" + s + "@" + md));
        }
        for (int r = 1; r <= 9; r++) {
            String st = (r <= 5) ? "1" : "2";
            g.add(List.of("Zaci-Rocnik-" + r, sg, "CN=Zaci-Stupen-" + st + "," + sg, "zaci-rocnik-" + r + "@" + md));
            g.add(List.of("Rodice-Rocnik-" + r, dl, "CN=Rodice-Stupen-" + st + "," + dl, "rodice-rocnik-" + r + "@" + md));
            g.add(List.of("Ucitele-Tridni-Rocnik-" + r, dl, "CN=Ucitele-Tridni-Stupen-" + st + "," + dl, "tridni-rocnik-" + r + "@" + md));
            for (char t = 'A'; t <= 'E'; t++) {
                String id = "" + r + t;
                g.add(List.of("Zaci-Trida-" + id, sg, "CN=Zaci-Rocnik-" + r + "," + sg, ("zaci-" + id + "@" + md).toLowerCase()));
                g.add(List.of("Rodice-Trida-" + id, dl, "CN=Rodice-Rocnik-" + r + "," + dl, ("rodice-trida-" + id + "@" + md).toLowerCase()));
                g.add(List.of("Ucitele-Tridni-" + id, dl, "CN=Ucitele-Tridni-Rocnik-" + r + "," + dl, ("tridni-" + id + "@" + md).toLowerCase()));
            }
        }
        return g;
    }

    private static List<List<String>> actualGroups(StructureServiceImpl service) {
        List<List<String>> g = new ArrayList<>();
        for (StructureServiceImpl.GroupDef d : service.buildGroupDefinitions()) {
            g.add(List.of(d.cn(), d.ou(), d.parentGroupDn() == null ? "" : d.parentGroupDn(), d.mail()));
        }
        return g;
    }

    @Test
    void vychoziOuOdpovidajiPuvodnim() {
        YamlAppConfig c = loadConfig(null);
        StructureServiceImpl service = new StructureServiceImpl(c, null);

        List<String> actual = service.buildOUDefinitions().stream().map(o -> o[0]).toList();
        assertEquals(61, actual.size());
        assertEquals(legacyOUs(c), actual);
    }

    @Test
    void vychoziSkupinyOdpovidajiPuvodnim() {
        YamlAppConfig c = loadConfig(null);
        StructureServiceImpl service = new StructureServiceImpl(c, null);

        List<List<String>> actual = actualGroups(service);
        assertEquals(172, actual.size());
        assertEquals(legacyGroups(c), actual);
    }

    @Test
    void explicitniVychoziSekceNicNemeni() {
        // explicitně uvedené výchozí hodnoty (jako v produkčním popisu) = beze změny
        YamlAppConfig c = loadConfig("""
                structure:
                  ou: { grade: "Rocnik-{rocnik}", class: "Trida-{trida}", alumni_year: "{rok}" }
                  groups:
                    students_class: { cn: "Zaci-Trida-{rocnik}{trida}", mail: "zaci-{rocnik}{trida}" }
                """);
        StructureServiceImpl service = new StructureServiceImpl(c, null);
        assertEquals(legacyGroups(c), actualGroups(service));
        assertEquals(legacyOUs(c), service.buildOUDefinitions().stream().map(o -> o[0]).toList());
    }

    @Test
    void vlastniKonvenceSePromitnouDoOuISkupin() {
        YamlAppConfig c = loadConfig("""
                structure:
                  ou:
                    grade: "R{rocnik}"
                    class: "T{trida}"
                  groups:
                    students_grade: { cn: "Zaci-R{rocnik}" }
                    students_class: { cn: "Zaci-{rocnik}{trida}", mail: "z{rocnik}{trida}" }
                """);
        StructureServiceImpl service = new StructureServiceImpl(c, null);

        List<String> ous = service.buildOUDefinitions().stream().map(o -> o[0]).toList();
        assertTrue(ous.contains("OU=TB,OU=R7," + c.getLdapBaseStudents()));
        assertFalse(ous.stream().anyMatch(o -> o.contains("Rocnik-") || o.contains("Trida-")));

        Map<String, StructureServiceImpl.GroupDef> byCn = new LinkedHashMap<>();
        service.buildGroupDefinitions().forEach(d -> byCn.put(d.cn(), d));
        StructureServiceImpl.GroupDef cls = byCn.get("Zaci-7B");
        assertNotNull(cls);
        // rodičovská skupina třídy odkazuje na přejmenovanou skupinu ročníku
        assertEquals("CN=Zaci-R7," + c.getLdapBaseStudentGroups(), cls.parentGroupDn());
        assertEquals("z7b@test.ext", cls.mail());
        // neuvedené druhy skupin zůstávají výchozí
        assertTrue(byCn.containsKey("Rodice-Trida-7B"));
    }
}
