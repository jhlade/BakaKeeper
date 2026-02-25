package cz.zsstudanka.skola.bakakeeper.service;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.model.StudentRecord;
import cz.zsstudanka.skola.bakakeeper.repository.FacultyRepository;
import cz.zsstudanka.skola.bakakeeper.repository.StudentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Testy pro RangeSelector – parsování a vyhodnocení rozsahu výběru.
 *
 * @author Jan Hladěna
 */
@ExtendWith(MockitoExtension.class)
class RangeSelectorTest {

    @Mock StudentRepository studentRepo;
    @Mock FacultyRepository facultyRepo;

    // === Parsovací testy (bez DB) ===

    @Test
    void parseWildcard() {
        RangeSelector sel = RangeSelector.parse("*");
        // 9 ročníků × 5 písmen = 45 tříd
        assertEquals(45, sel.getClasses().size());
        assertTrue(sel.getClasses().contains("1.A"));
        assertTrue(sel.getClasses().contains("9.E"));
        assertTrue(sel.getIndividuals().isEmpty());
    }

    @Test
    void parseSingleYear() {
        RangeSelector sel = RangeSelector.parse("5");
        assertEquals(5, sel.getClasses().size());
        assertTrue(sel.getClasses().contains("5.A"));
        assertTrue(sel.getClasses().contains("5.E"));
        assertFalse(sel.getClasses().contains("4.A"));
        assertTrue(sel.getIndividuals().isEmpty());
    }

    @Test
    void parseSingleClass() {
        RangeSelector sel = RangeSelector.parse("5.A");
        assertEquals(1, sel.getClasses().size());
        assertEquals("5.A", sel.getClasses().get(0));
        assertTrue(sel.getIndividuals().isEmpty());
    }

    @Test
    void parseSingleClassLowercase() {
        RangeSelector sel = RangeSelector.parse("5.a");
        assertEquals(1, sel.getClasses().size());
        assertEquals("5.A", sel.getClasses().get(0));
    }

    @Test
    void parseIndividualStudent() {
        RangeSelector sel = RangeSelector.parse("novak.tomas@skola.cz");
        assertTrue(sel.getClasses().isEmpty());
        assertEquals(1, sel.getIndividuals().size());
        assertEquals("novak.tomas@skola.cz", sel.getIndividuals().get(0));
    }

    @Test
    void parseMixedQuery() {
        RangeSelector sel = RangeSelector.parse("5,6.B,novak.tomas@skola.cz");
        // ročník 5 = 5 tříd + třída 6.B = celkem 6 tříd
        assertEquals(6, sel.getClasses().size());
        assertTrue(sel.getClasses().contains("5.A"));
        assertTrue(sel.getClasses().contains("5.E"));
        assertTrue(sel.getClasses().contains("6.B"));
        // individuální žák
        assertEquals(1, sel.getIndividuals().size());
        assertEquals("novak.tomas@skola.cz", sel.getIndividuals().get(0));
    }

    @Test
    void parseDeduplicate() {
        // 5.A je ve výčtu dvakrát (jednou přímo, jednou přes ročník 5)
        RangeSelector sel = RangeSelector.parse("5.A,5");
        assertEquals(5, sel.getClasses().size());
        // 5.A se nesmí opakovat
        assertEquals(1, sel.getClasses().stream().filter("5.A"::equals).count());
    }

    @Test
    void parseMultipleIndividuals() {
        RangeSelector sel = RangeSelector.parse("novak.tomas@skola.cz,vesela.marie@skola.cz");
        assertTrue(sel.getClasses().isEmpty());
        assertEquals(2, sel.getIndividuals().size());
    }

    @Test
    void parseClassesSorted() {
        RangeSelector sel = RangeSelector.parse("9.A,1.C,5.B");
        assertEquals(List.of("1.C", "5.B", "9.A"), sel.getClasses());
    }

    @Test
    void parseEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> RangeSelector.parse(""));
        assertThrows(IllegalArgumentException.class, () -> RangeSelector.parse(null));
    }

    @Test
    void parseLoginWithoutDomain() {
        // bare login novak.tomas (bez @) se musí rozpoznat jako individuální
        RangeSelector sel = RangeSelector.parse("novak.tomas");
        assertTrue(sel.getClasses().isEmpty());
        assertEquals(1, sel.getIndividuals().size());
        assertEquals("novak.tomas", sel.getIndividuals().get(0));
    }

    @Test
    void parseIgnoresWhitespace() {
        RangeSelector sel = RangeSelector.parse(" 5.A , 6.B ");
        assertEquals(2, sel.getClasses().size());
        assertTrue(sel.getClasses().contains("5.A"));
        assertTrue(sel.getClasses().contains("6.B"));
    }

    // === Resolve testy (s mock repozitáři) ===

    @Test
    void resolveClassStudents() {
        // příprava třídního učitele
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        teacher5A.setSurname("Burgetová");
        teacher5A.setGivenName("Lada");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A));

        // příprava žáků 5.A
        StudentRecord student1 = createStudent("001", "Novák", "Tomáš", "5.A", "novak@skola.cz");
        StudentRecord student2 = createStudent("002", "Veselá", "Marie", "5.A", "vesela@skola.cz");
        when(studentRepo.findActive(5, "A")).thenReturn(List.of(student1, student2));

        ResolvedSelection result = RangeSelector.parse("5.A").resolve(studentRepo, facultyRepo);

        assertEquals(1, result.studentsByClass().size());
        assertTrue(result.studentsByClass().containsKey("5.A"));
        assertEquals(2, result.studentsByClass().get("5.A").size());
        assertEquals("Burgetová", result.classTeachers().get("5.A").getSurname());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void resolveSkipsNonExistentClass() {
        // třída 9.E nemá třídního učitele → přeskočit
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A));
        when(studentRepo.findActive(5, "A")).thenReturn(List.of());

        ResolvedSelection result = RangeSelector.parse("5.A,9.E").resolve(studentRepo, facultyRepo);

        // 9.E nemá třídního → není ve výsledku
        assertFalse(result.studentsByClass().containsKey("9.E"));
    }

    @Test
    void resolveIndividualStudent() {
        // žádné třídy, jen individuální žák
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A));

        StudentRecord student = createStudent("001", "Novák", "Tomáš", "5.A", "novak.tomas@skola.cz");
        when(studentRepo.findByEmail("novak.tomas@skola.cz")).thenReturn(student);

        ResolvedSelection result = RangeSelector.parse("novak.tomas@skola.cz").resolve(studentRepo, facultyRepo);

        assertEquals(1, result.studentsByClass().size());
        assertTrue(result.studentsByClass().containsKey("5.A"));
        assertEquals(1, result.studentsByClass().get("5.A").size());
        assertEquals("Novák", result.studentsByClass().get("5.A").get(0).getSurname());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void resolveIndividualNotFound() {
        when(facultyRepo.findActive(true)).thenReturn(List.of());
        when(studentRepo.findByEmail("neexistuje@skola.cz")).thenReturn(null);

        ResolvedSelection result = RangeSelector.parse("neexistuje@skola.cz").resolve(studentRepo, facultyRepo);

        assertTrue(result.studentsByClass().isEmpty());
        assertEquals(1, result.notFound().size());
        assertEquals("neexistuje@skola.cz", result.notFound().get(0));
    }

    @Test
    void resolveDeduplicatesStudents() {
        // žák novak je v 5.A a zároveň zadán individuálně
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A));

        StudentRecord student = createStudent("001", "Novák", "Tomáš", "5.A", "novak.tomas@skola.cz");
        when(studentRepo.findActive(5, "A")).thenReturn(List.of(student));
        when(studentRepo.findByEmail("novak.tomas@skola.cz")).thenReturn(student);

        ResolvedSelection result = RangeSelector.parse("5.A,novak.tomas@skola.cz")
                .resolve(studentRepo, facultyRepo);

        // žák se nesmí opakovat
        assertEquals(1, result.studentsByClass().get("5.A").size());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void resolveMixedClassAndIndividual() {
        // třída 5.A + individuální žák z jiné třídy (6.B)
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        FacultyRecord teacher6B = new FacultyRecord();
        teacher6B.setClassLabel("6.B");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A, teacher6B));

        StudentRecord stu5A = createStudent("001", "Novák", "Tomáš", "5.A", "novak@skola.cz");
        when(studentRepo.findActive(5, "A")).thenReturn(List.of(stu5A));

        StudentRecord stu6B = createStudent("002", "Veselá", "Marie", "6.B", "vesela.marie@skola.cz");
        when(studentRepo.findByEmail("vesela.marie@skola.cz")).thenReturn(stu6B);

        ResolvedSelection result = RangeSelector.parse("5.A,vesela.marie@skola.cz")
                .resolve(studentRepo, facultyRepo);

        assertEquals(2, result.studentsByClass().size());
        assertEquals(1, result.studentsByClass().get("5.A").size());
        assertEquals(1, result.studentsByClass().get("6.B").size());
        assertEquals("Veselá", result.studentsByClass().get("6.B").get(0).getSurname());
    }

    @Test
    void resolveLoginBezDomenyDoplniMailDomain() {
        // bare login "novak.tomas" (bez @) by měl být doplněn na "novak.tomas@skola.cz"
        FacultyRecord teacher5A = new FacultyRecord();
        teacher5A.setClassLabel("5.A");
        when(facultyRepo.findActive(true)).thenReturn(List.of(teacher5A));

        StudentRecord student = createStudent("001", "Novák", "Tomáš", "5.A", "novak.tomas@skola.cz");
        when(studentRepo.findByEmail("novak.tomas@skola.cz")).thenReturn(student);

        ResolvedSelection result = RangeSelector.parse("novak.tomas")
                .resolve(studentRepo, facultyRepo, "skola.cz");

        assertEquals(1, result.studentsByClass().size());
        assertTrue(result.studentsByClass().containsKey("5.A"));
        assertEquals("Novák", result.studentsByClass().get("5.A").get(0).getSurname());
        assertTrue(result.notFound().isEmpty());
    }

    @Test
    void resolveLoginBezDomenyBezMailDomainNenajde() {
        // bez mailDomain se bare login hledá tak, jak je – a nenajde se
        when(facultyRepo.findActive(true)).thenReturn(List.of());
        when(studentRepo.findByEmail("novak.tomas")).thenReturn(null);

        ResolvedSelection result = RangeSelector.parse("novak.tomas")
                .resolve(studentRepo, facultyRepo);

        assertTrue(result.studentsByClass().isEmpty());
        assertEquals(1, result.notFound().size());
        assertEquals("novak.tomas", result.notFound().get(0));
    }

    // === Pomocná metoda ===

    private static StudentRecord createStudent(String id, String surname, String givenName,
                                                String className, String email) {
        StudentRecord s = new StudentRecord();
        s.setInternalId(id);
        s.setSurname(surname);
        s.setGivenName(givenName);
        s.setClassName(className);
        s.setClassYear(Character.getNumericValue(className.charAt(0)));
        s.setClassLetter(String.valueOf(className.charAt(2)));
        s.setEmail(email);
        return s;
    }
}
