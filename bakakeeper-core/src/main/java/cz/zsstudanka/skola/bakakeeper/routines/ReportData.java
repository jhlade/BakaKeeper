package cz.zsstudanka.skola.bakakeeper.routines;

import cz.zsstudanka.skola.bakakeeper.model.FacultyRecord;
import cz.zsstudanka.skola.bakakeeper.utils.PdfReportGenerator;

import java.util.List;
import java.util.Map;

/**
 * Agregovaná data sestavy seskupená podle tříd.
 * Výstup fáze {@link Export#buildReportData}, vstup fáze {@link Export#sendReports}.
 *
 * @param reportRows data pro PDF tabulky (klíč = label třídy, hodnota = řádky žáků)
 * @param classTeachers třídní učitelé (klíč = label třídy)
 * @param resetPerformed zda byl proveden reset hesel
 * @param successCount počet úspěšně zpracovaných žáků
 * @param failureCount počet neúspěšných operací
 * @author Jan Hladěna
 */
public record ReportData(
        Map<String, List<PdfReportGenerator.StudentReportRow>> reportRows,
        Map<String, FacultyRecord> classTeachers,
        boolean resetPerformed,
        int successCount,
        int failureCount
) {}
