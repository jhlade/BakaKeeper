package cz.zsstudanka.skola.bakakeeper.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testy generátoru PDF sestav.
 */
class PdfReportGeneratorTest {

    @Test
    void fontLoading_succeeds() {
        assertDoesNotThrow(PdfReportGenerator::new,
                "Načtení CMU fontů z classpath musí proběhnout bez chyby");
    }

    @Test
    void schoolYear_septemberOnward() {
        // září = začátek nového školního roku
        assertEquals("2025/2026", PdfReportGenerator.schoolYear(LocalDate.of(2025, 9, 1)));
        assertEquals("2025/2026", PdfReportGenerator.schoolYear(LocalDate.of(2025, 12, 31)));
        assertEquals("2026/2027", PdfReportGenerator.schoolYear(LocalDate.of(2026, 9, 15)));
    }

    @Test
    void schoolYear_beforeSeptember() {
        // leden–srpen = konec školního roku
        assertEquals("2025/2026", PdfReportGenerator.schoolYear(LocalDate.of(2026, 1, 15)));
        assertEquals("2025/2026", PdfReportGenerator.schoolYear(LocalDate.of(2026, 6, 30)));
        assertEquals("2025/2026", PdfReportGenerator.schoolYear(LocalDate.of(2026, 8, 31)));
        assertEquals("2024/2025", PdfReportGenerator.schoolYear(LocalDate.of(2025, 3, 1)));
    }

    @Test
    void generateClassReport_createsValidPdf(@TempDir Path tempDir) throws IOException {
        PdfReportGenerator gen = new PdfReportGenerator();
        Path pdfPath = tempDir.resolve("test-report.pdf");

        List<PdfReportGenerator.StudentReportRow> students = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Novák", "Tomáš",
                        "novak.tomas@skola.cz", "No.To.260001",
                        "novak.tomas@skola.cz\tNo.To.260001"),
                new PdfReportGenerator.StudentReportRow(
                        2, "Veselá", "Marie",
                        "vesela.marie@skola.cz", "Ve.Ma.260002",
                        "vesela.marie@skola.cz\tVe.Ma.260002"),
                new PdfReportGenerator.StudentReportRow(
                        3, "Černý", "Jiří",
                        "cerny.jiri@skola.cz", "Ce.Ji.260003",
                        "cerny.jiri@skola.cz\tCe.Ji.260003")
        );

        gen.generateClassReport(
                pdfPath.toString(), "1.A", "Lada Burgetová",
                "2025/2026", students, false);

        assertTrue(Files.exists(pdfPath), "PDF soubor musí existovat");
        assertTrue(Files.size(pdfPath) > 0, "PDF soubor nesmí být prázdný");

        // ověření PDF hlavičky (%PDF)
        byte[] header = Files.readAllBytes(pdfPath);
        assertEquals('%', (char) header[0]);
        assertEquals('P', (char) header[1]);
        assertEquals('D', (char) header[2]);
        assertEquals('F', (char) header[3]);
    }

    @Test
    void generateClassReport_dryRun(@TempDir Path tempDir) throws IOException {
        PdfReportGenerator gen = new PdfReportGenerator();
        Path pdfPath = tempDir.resolve("test-dryrun.pdf");

        List<PdfReportGenerator.StudentReportRow> students = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Test", "Žák",
                        "test.zak@skola.cz", "Te.Za.260001",
                        "test.zak@skola.cz\tTe.Za.260001")
        );

        assertDoesNotThrow(() ->
                gen.generateClassReport(
                        pdfPath.toString(), "2.B", "Eva Testová",
                        "2025/2026", students, true));

        assertTrue(Files.exists(pdfPath), "PDF soubor musí existovat i v dry-run režimu");
    }

    @Test
    void generateClassReport_emptyStudentList(@TempDir Path tempDir) throws IOException {
        PdfReportGenerator gen = new PdfReportGenerator();
        Path pdfPath = tempDir.resolve("test-empty.pdf");

        // prázdná třída – sestava se má vygenerovat bez chyby
        gen.generateClassReport(
                pdfPath.toString(), "9.E", "Petr Prázdný",
                "2025/2026", List.of(), false);

        assertTrue(Files.exists(pdfPath), "PDF soubor musí existovat i pro prázdnou třídu");
    }

    @Test
    void generateClassReport_czechDiacritics(@TempDir Path tempDir) throws IOException {
        PdfReportGenerator gen = new PdfReportGenerator();
        Path pdfPath = tempDir.resolve("test-diacritics.pdf");

        // diakritika ve všech polích
        List<PdfReportGenerator.StudentReportRow> students = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Řehoř", "Šťěpán",
                        "rehor.stepan@skola.cz", "Re.St.260001",
                        "rehor.stepan@skola.cz\tRe.St.260001"),
                new PdfReportGenerator.StudentReportRow(
                        2, "Žlůťoučký", "Příšerně",
                        "zlutoucky.priserne@skola.cz", "Zl.Pr.260002",
                        "zlutoucky.priserne@skola.cz\tZl.Pr.260002")
        );

        assertDoesNotThrow(() ->
                gen.generateClassReport(
                        pdfPath.toString(), "3.Č", "Ďáblová Ňůžková",
                        "2025/2026", students, false));

        assertTrue(Files.exists(pdfPath), "PDF s diakritikou musí být vytvořen bez chyby");
    }

    @Test
    void generateClassReport_multipleDocumentsFromSameGenerator(@TempDir Path tempDir) throws IOException {
        // ověření, že jedna instance generátoru zvládne vytvořit více dokumentů za sebou
        // (PdfFont objekty se nesmí reusovat mezi PdfDocument instancemi)
        PdfReportGenerator gen = new PdfReportGenerator();

        List<PdfReportGenerator.StudentReportRow> studentsA = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Novák", "Jan",
                        "novak.jan@skola.cz", "No.Ja.260001",
                        "novak.jan@skola.cz\tNo.Ja.260001")
        );

        List<PdfReportGenerator.StudentReportRow> studentsB = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Králová", "Marie",
                        "kralova.marie@skola.cz", "Kr.Ma.260001",
                        "kralova.marie@skola.cz\tKr.Ma.260001")
        );

        Path pdfA = tempDir.resolve("class-1a.pdf");
        Path pdfB = tempDir.resolve("class-1b.pdf");

        gen.generateClassReport(pdfA.toString(), "1.A", "Jan Novotný", "2025/2026", studentsA, false);
        gen.generateClassReport(pdfB.toString(), "1.B", "Eva Králová", "2025/2026", studentsB, false);

        assertTrue(Files.exists(pdfA), "První PDF musí existovat");
        assertTrue(Files.exists(pdfB), "Druhé PDF musí existovat");
        assertTrue(Files.size(pdfA) > 0, "První PDF nesmí být prázdné");
        assertTrue(Files.size(pdfB) > 0, "Druhé PDF nesmí být prázdné");
    }

    @Test
    void generateClassReport_nullUpnHandled(@TempDir Path tempDir) throws IOException {
        // žák s null UPN – nesmí vyhodit výjimku, QR kód se přeskočí
        PdfReportGenerator gen = new PdfReportGenerator();
        Path pdfPath = tempDir.resolve("test-null-upn.pdf");

        List<PdfReportGenerator.StudentReportRow> students = List.of(
                new PdfReportGenerator.StudentReportRow(
                        1, "Vrabec", "Milisálek",
                        null, "Vr.Mi.260001",
                        null)
        );

        assertDoesNotThrow(() ->
                gen.generateClassReport(
                        pdfPath.toString(), "1.A", "Jan Novotný",
                        "2025/2026", students, false));

        assertTrue(Files.exists(pdfPath), "PDF s null UPN musí být vytvořen bez chyby");
    }
}
