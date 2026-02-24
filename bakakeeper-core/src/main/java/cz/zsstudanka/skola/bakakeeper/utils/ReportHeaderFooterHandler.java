package cz.zsstudanka.skola.bakakeeper.utils;

import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.geom.Rectangle;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;

/**
 * Záhlaví a zápatí PDF sestavy.
 *
 * <p>Záhlaví:</p>
 * <ul>
 *   <li>vlevo: identifikace třídy a školní rok</li>
 *   <li>vpravo: jméno třídního učitele</li>
 * </ul>
 *
 * <p>Zápatí:</p>
 * <ul>
 *   <li>vlevo: identifikace aplikace (verze @ hostname)</li>
 *   <li>uprostřed: číslo stránky / celkový počet</li>
 *   <li>vpravo: datum a čas generování sestavy</li>
 * </ul>
 *
 * <p>Záhlaví i zápatí se zapisují jednorázově přes {@link #writeAll(PdfDocument)}
 * po dokončení veškerého obsahu dokumentu. Vyžaduje {@code immediateFlush=false}
 * na {@code Document}, aby stránky zůstaly zapisovatelné.</p>
 *
 * @author Jan Hladěna
 */
public class ReportHeaderFooterHandler {

    /** odsazení od okraje stránky v bodech */
    private static final float MARGIN = 50f;
    /** výška záhlaví od horního okraje stránky */
    private static final float HEADER_Y_OFFSET = 40f;
    /** výška zápatí od dolního okraje stránky */
    private static final float FOOTER_Y = 30f;
    /** velikost písma záhlaví/zápatí */
    private static final float FONT_SIZE = 8f;

    private final String headerLeft;
    private final String headerRight;
    private final String footerLeft;
    private final String footerRight;
    private final PdfFont font;

    /**
     * Konstruktor.
     *
     * @param headerLeft  text záhlaví vlevo (třída + školní rok)
     * @param headerRight text záhlaví vpravo (třídní učitel)
     * @param footerLeft  text zápatí vlevo (verze @ hostname)
     * @param footerRight text zápatí vpravo (datum/čas generování)
     * @param font        písmo pro záhlaví/zápatí
     */
    public ReportHeaderFooterHandler(String headerLeft, String headerRight,
                                     String footerLeft, String footerRight,
                                     PdfFont font) {
        this.headerLeft = headerLeft;
        this.headerRight = headerRight;
        this.footerLeft = footerLeft;
        this.footerRight = footerRight;
        this.font = font;
    }

    /**
     * Zapíše záhlaví a zápatí na všechny stránky dokumentu.
     * Volat po dokončení veškerého obsahu, ale před {@code document.close()}.
     * Vyžaduje, aby {@code Document} byl vytvořen s {@code immediateFlush=false}.
     *
     * @param pdfDoc PDF dokument
     */
    public void writeAll(PdfDocument pdfDoc) {
        int totalPages = pdfDoc.getNumberOfPages();
        for (int i = 1; i <= totalPages; i++) {
            PdfPage page = pdfDoc.getPage(i);
            writePage(pdfDoc, page, i, totalPages);
        }
    }

    /**
     * Zapíše záhlaví a zápatí na jednu stránku.
     */
    private void writePage(PdfDocument pdfDoc, PdfPage page, int currentPage, int totalPages) {
        Rectangle pageSize = page.getPageSize();
        float pageWidth = pageSize.getWidth();
        float pageHeight = pageSize.getHeight();

        PdfCanvas canvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);

        // --- záhlaví ---
        // vlevo
        canvas.beginText()
                .setFontAndSize(font, FONT_SIZE)
                .moveText(MARGIN, pageHeight - HEADER_Y_OFFSET)
                .showText(headerLeft)
                .endText();

        // vpravo
        float headerRightWidth = font.getWidth(headerRight, FONT_SIZE);
        canvas.beginText()
                .setFontAndSize(font, FONT_SIZE)
                .moveText(pageWidth - MARGIN - headerRightWidth, pageHeight - HEADER_Y_OFFSET)
                .showText(headerRight)
                .endText();

        // --- zápatí ---
        // vlevo
        canvas.beginText()
                .setFontAndSize(font, FONT_SIZE)
                .moveText(MARGIN, FOOTER_Y)
                .showText(footerLeft)
                .endText();

        // uprostřed: "N / M"
        String pageText = currentPage + " / " + totalPages;
        float pageTextWidth = font.getWidth(pageText, FONT_SIZE);
        float centerX = (pageWidth - pageTextWidth) / 2;
        canvas.beginText()
                .setFontAndSize(font, FONT_SIZE)
                .moveText(centerX, FOOTER_Y)
                .showText(pageText)
                .endText();

        // vpravo
        float footerRightWidth = font.getWidth(footerRight, FONT_SIZE);
        canvas.beginText()
                .setFontAndSize(font, FONT_SIZE)
                .moveText(pageWidth - MARGIN - footerRightWidth, FOOTER_Y)
                .showText(footerRight)
                .endText();

        canvas.release();
    }
}
