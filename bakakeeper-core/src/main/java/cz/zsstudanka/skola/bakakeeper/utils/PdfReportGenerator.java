package cz.zsstudanka.skola.bakakeeper.utils;

import com.itextpdf.barcodes.BarcodeQRCode;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfDocumentInfo;
import com.itextpdf.kernel.pdf.PdfVersion;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.kernel.pdf.WriterProperties;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;

import cz.zsstudanka.skola.bakakeeper.settings.Version;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;

/**
 * Generátor PDF sestav s přístupovými údaji žáků.
 * Nahrazuje předchozí LaTeX backend (LuaLaTeX) přímým generováním PDF
 * pomocí iText 8.
 *
 * <p>Sestava reprezentuje změny provedené na úrovni třídy a je adresována
 * třídnímu učiteli dané třídy a správci systému.</p>
 *
 * <p>Instance je bezpečně znovupoužitelná pro generování více sestav –
 * surová data písem se uchovávají v paměti a pro každý dokument
 * se vytvoří čerstvé {@link PdfFont} objekty (iText váže fonty
 * na konkrétní {@link PdfDocument}).</p>
 *
 * @author Jan Hladěna
 */
public class PdfReportGenerator {

    /** cesta k souboru písma CMU Roman (proporcionální) v classpath */
    private static final String FONT_ROMAN_PATH = "/font/cmunrm.ttf";
    /** cesta k souboru písma CMU Typewriter (monospace) v classpath */
    private static final String FONT_MONO_PATH = "/font/cmuntt.ttf";

    /** velikost písma v tabulce */
    private static final float TABLE_FONT_SIZE = 10f;
    /** velikost písma hlavičky tabulky */
    private static final float TABLE_HEADER_FONT_SIZE = 10f;
    /** velikost písma titulku */
    private static final float TITLE_FONT_SIZE = 14f;
    /** velikost písma poznámky pod tabulkou */
    private static final float FOOTNOTE_FONT_SIZE = 10f;
    /** velikost QR kódu v bodech (~14mm) */
    private static final float QR_SIZE = 40f;
    /** vnitřní padding datových buněk tabulky v bodech */
    private static final float CELL_PADDING = 4f;

    /** okraje stránky v bodech */
    private static final float MARGIN_TOP = 65f;
    private static final float MARGIN_BOTTOM = 65f;
    private static final float MARGIN_LEFT = 50f;
    private static final float MARGIN_RIGHT = 50f;

    /** surová data písma – proporcionální (CMU Roman) */
    private final byte[] fontRomanBytes;
    /** surová data písma – monospace (CMU Typewriter) */
    private final byte[] fontMonoBytes;
    /** volitelný text poznámky pod tabulkou – první řádek (UPN vysvětlení) */
    private String footnoteUpn = "UPN = User Principal Name, slouží jako přihlašovací jméno do\u00a0služeb Office\u00a0365 "
            + "a\u00a0zároveň jako platný tvar e\u2011mailové adresy.";
    /** volitelný text poznámky pod tabulkou – druhý řádek (portál pro změnu hesla) */
    private String footnotePassword = null;

    /**
     * Datový záznam jednoho žáka pro tabulku sestavy.
     *
     * @param classNumber číslo v třídním výkazu
     * @param surname     příjmení
     * @param givenName   jméno
     * @param upn         User Principal Name (přihlašovací jméno)
     * @param password    heslo
     * @param qrContent   obsah QR kódu (UPN + tab + heslo)
     */
    public record StudentReportRow(
            int classNumber,
            String surname,
            String givenName,
            String upn,
            String password,
            String qrContent
    ) {}

    /**
     * Konstruktor – načte surová data písem z classpath.
     * Písma se serializují do bytových polí a teprve při generování
     * každého dokumentu se z nich vytvoří nové {@link PdfFont} instance.
     *
     * @throws IOException chyba při načítání souborů písem
     */
    public PdfReportGenerator() throws IOException {
        this.fontRomanBytes = loadResource(FONT_ROMAN_PATH);
        this.fontMonoBytes = loadResource(FONT_MONO_PATH);
    }

    /**
     * Nastaví text poznámky o UPN pod tabulkou.
     * Pokud je {@code null}, poznámka se nevykreslí.
     *
     * @param text text poznámky
     */
    public void setFootnoteUpn(String text) {
        this.footnoteUpn = text;
    }

    /**
     * Nastaví text poznámky o změně hesla pod tabulkou.
     * Pokud je {@code null}, poznámka se nevykreslí.
     *
     * @param text text poznámky (např. "Žáci si mohou heslo změnit na portálu ...")
     */
    public void setFootnotePassword(String text) {
        this.footnotePassword = text;
    }

    /**
     * Vygeneruje PDF sestavu pro jednu třídu.
     *
     * @param outputPath       cesta k výstupnímu PDF souboru
     * @param classId          označení třídy (např. "1.A")
     * @param classTeacherName jméno třídního učitele
     * @param schoolYear       školní rok (např. "2025/2026")
     * @param students         seznam záznamů žáků pro tabulku (seřazený)
     * @param isDryRun         příznak zkušební sestavy
     * @throws IOException chyba při zápisu PDF
     */
    public void generateClassReport(String outputPath, String classId,
                                    String classTeacherName, String schoolYear,
                                    List<StudentReportRow> students,
                                    boolean isDryRun) throws IOException {

        // nové instance fontů pro tento dokument
        // (iText váže PdfFont na konkrétní PdfDocument – nelze reusovat mezi dokumenty)
        PdfFont fontRoman = PdfFontFactory.createFont(fontRomanBytes, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);
        PdfFont fontMono = PdfFontFactory.createFont(fontMonoBytes, PdfFontFactory.EmbeddingStrategy.PREFER_EMBEDDED);

        WriterProperties writerProperties = new WriterProperties().setPdfVersion(PdfVersion.PDF_1_4);
        PdfWriter writer = new PdfWriter(outputPath, writerProperties);
        PdfDocument pdfDoc = new PdfDocument(writer);

        // metadata dokumentu
        PdfDocumentInfo info = pdfDoc.getDocumentInfo();
        info.setTitle("Sestava přístupových údajů – třída " + classId);
        info.setAuthor(systemHostname());
        info.setSubject("Školní rok " + schoolYear);
        info.setCreator(Version.getInstance().getTag());
        info.setProducer(Version.getInstance().getTag() + " + iText");

        // nastavení stránky – immediateFlush=false je nutný, aby stránky
        // zůstaly zapisovatelné pro post-processingový zápis záhlaví a zápatí
        pdfDoc.setDefaultPageSize(PageSize.A4);
        Document document = new Document(pdfDoc, PageSize.A4, false);
        document.setMargins(MARGIN_TOP, MARGIN_RIGHT, MARGIN_BOTTOM, MARGIN_LEFT);

        // příznak zkušební sestavy
        if (isDryRun) {
            document.add(new Paragraph("ZKUŠEBNÍ SESTAVA")
                    .setFont(fontRoman)
                    .setFontSize(16f)
                    .setBold()
                    .setFontColor(ColorConstants.RED)
                    .setTextAlignment(TextAlignment.CENTER)
                    .setMarginBottom(5f));
        }

        // titulek
        document.add(new Paragraph("Třída " + classId)
                .setFont(fontRoman)
                .setFontSize(TITLE_FONT_SIZE)
                .setBold()
                .setMarginBottom(10f));

        // tabulka – 6 sloupců
        Table table = new Table(UnitValue.createPercentArray(new float[]{6, 21, 18, 30, 17, 8}))
                .useAllAvailableWidth();

        // hlavička tabulky
        table.addHeaderCell(createHeaderCell("Č.", fontRoman));
        table.addHeaderCell(createHeaderCell("Příjmení", fontRoman));
        table.addHeaderCell(createHeaderCell("Jméno", fontRoman));
        table.addHeaderCell(createHeaderCell("UPN", fontRoman));
        table.addHeaderCell(createHeaderCell("Heslo", fontRoman));
        table.addHeaderCell(createHeaderCell("", fontRoman));

        // řádky žáků
        for (StudentReportRow student : students) {
            // č. třídního výkazu
            table.addCell(new Cell()
                    .add(new Paragraph(String.valueOf(student.classNumber()))
                            .setFont(fontRoman).setFontSize(TABLE_FONT_SIZE))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(CELL_PADDING));

            // příjmení
            table.addCell(new Cell()
                    .add(new Paragraph(student.surname())
                            .setFont(fontRoman).setFontSize(TABLE_FONT_SIZE))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(CELL_PADDING));

            // jméno
            table.addCell(new Cell()
                    .add(new Paragraph(student.givenName())
                            .setFont(fontRoman).setFontSize(TABLE_FONT_SIZE))
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(CELL_PADDING));

            // UPN – monospace, rozdělený na dva řádky: jméno + @doména (ošetření null)
            Paragraph upnPar = new Paragraph().setFont(fontMono).setFontSize(TABLE_FONT_SIZE);
            if (student.upn() != null) {
                int atIdx = student.upn().indexOf('@');
                if (atIdx > 0) {
                    upnPar.add(new Text(student.upn().substring(0, atIdx) + "\n"));
                    upnPar.add(new Text(student.upn().substring(atIdx)));
                } else {
                    upnPar.add(student.upn());
                }
            }
            table.addCell(new Cell()
                    .add(upnPar)
                    .setTextAlignment(TextAlignment.RIGHT)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(CELL_PADDING));

            // heslo – monospace
            table.addCell(new Cell()
                    .add(new Paragraph(student.password())
                            .setFont(fontMono).setFontSize(TABLE_FONT_SIZE))
                    .setTextAlignment(TextAlignment.CENTER)
                    .setVerticalAlignment(VerticalAlignment.MIDDLE)
                    .setPadding(CELL_PADDING));

            // QR kód (přeskočit při chybějícím UPN)
            if (student.upn() != null && student.qrContent() != null) {
                BarcodeQRCode qrCode = new BarcodeQRCode(student.qrContent());
                Image qrImage = new Image(qrCode.createFormXObject(pdfDoc));
                qrImage.setWidth(QR_SIZE);
                qrImage.setHeight(QR_SIZE);

                table.addCell(new Cell()
                        .add(qrImage)
                        .setTextAlignment(TextAlignment.CENTER)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE));
            } else {
                table.addCell(new Cell());
            }
        }

        document.add(table);

        // poznámka pod tabulkou – UPN vysvětlení
        if (footnoteUpn != null && !footnoteUpn.isEmpty()) {
            document.add(new Paragraph(footnoteUpn)
                    .setFont(fontRoman)
                    .setFontSize(FOOTNOTE_FONT_SIZE)
                    .setMarginTop(15f));
        }

        // poznámka pod tabulkou – portál pro změnu hesla
        if (footnotePassword != null && !footnotePassword.isEmpty()) {
            document.add(new Paragraph(footnotePassword)
                    .setFont(fontRoman)
                    .setFontSize(FOOTNOTE_FONT_SIZE)
                    .setMarginTop(5f));
        }

        // záhlaví a zápatí – post-processing přes všechny stránky najednou
        // (iText 8 END_PAGE event se pro poslední stránku volá až uvnitř
        //  document.close(), proto nelze použít event handler)
        String headerLeft = "Třída " + classId + "  |  Školní rok " + schoolYear;
        String footerLeft = Version.getInstance().getTag() + " @ " + systemHostname() + " (" + systemOs() + ")";
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String footerRight = formatter.format(new Date());

        ReportHeaderFooterHandler headerFooter = new ReportHeaderFooterHandler(
                headerLeft, classTeacherName, footerLeft, footerRight, fontRoman);
        headerFooter.writeAll(pdfDoc);

        document.close();
    }

    /**
     * Vypočte aktuální školní rok.
     * Září a později = aktuální_rok/aktuální_rok+1,
     * jinak (leden–srpen) = aktuální_rok-1/aktuální_rok.
     *
     * @return školní rok ve formátu "YYYY/YYYY"
     */
    public static String currentSchoolYear() {
        return schoolYear(LocalDate.now());
    }

    /**
     * Vypočte školní rok pro dané datum.
     *
     * @param date datum pro výpočet
     * @return školní rok ve formátu "YYYY/YYYY"
     */
    static String schoolYear(LocalDate date) {
        int year = date.getYear();
        int month = date.getMonthValue();
        if (month >= 9) {
            return year + "/" + (year + 1);
        }
        return (year - 1) + "/" + year;
    }

    /**
     * Vytvoří buňku záhlaví tabulky (tučné písmo, zarovnání na střed).
     *
     * @param text text záhlaví
     * @param font písmo
     * @return formátovaná buňka
     */
    private static Cell createHeaderCell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text)
                        .setFont(font)
                        .setFontSize(TABLE_HEADER_FONT_SIZE)
                        .setBold())
                .setTextAlignment(TextAlignment.CENTER)
                .setVerticalAlignment(VerticalAlignment.MIDDLE)
                .setPadding(CELL_PADDING);
    }

    /**
     * Načte zdroj z classpath jako pole bytů.
     *
     * @param path cesta ke zdroji v classpath
     * @return pole bytů
     * @throws IOException zdroj nebyl nalezen nebo chyba při čtení
     */
    private byte[] loadResource(String path) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) {
                throw new IOException("Zdroj nenalezen v classpath: " + path);
            }
            return is.readAllBytes();
        }
    }

    /**
     * Získá hostname aktuálního počítače.
     *
     * @return hostname nebo "[unknown]"
     */
    private static String systemHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "[unknown]";
        }
    }

    /**
     * Získá identifikaci operačního systému.
     *
     * @return název a verze OS nebo "[unknown]"
     */
    private static String systemOs() {
        try {
            return System.getProperty("os.name") + " " + System.getProperty("os.version");
        } catch (Exception e) {
            return "[unknown]";
        }
    }
}
