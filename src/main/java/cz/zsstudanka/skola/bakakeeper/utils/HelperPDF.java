package cz.zsstudanka.skola.bakakeeper.utils;

import com.itextpdf.io.font.FontProgram;
import com.itextpdf.io.font.FontProgramFactory;
import com.itextpdf.io.font.PdfEncodings;
import com.itextpdf.kernel.events.Event;
import com.itextpdf.kernel.events.PdfDocumentEvent;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfPage;
import com.itextpdf.kernel.pdf.canvas.PdfCanvas;
import cz.zsstudanka.skola.bakakeeper.routines.Export;

import java.io.IOException;
import java.io.InputStream;

public class HelperPDF implements com.itextpdf.kernel.events.IEventHandler {

    private int targetPage;

    private String headerLeft;
    private String headerRight;
    private String footerLeft;
    private String footerRight;

    public HelperPDF(int page, String headerLeft, String headerRight, String footerLeft, String footerRight) {
        this.targetPage = page;

        this.headerLeft = (headerLeft != null) ? headerLeft : "";
        this.headerRight = (headerRight != null) ? headerRight : "";
        this.footerLeft = (footerLeft != null) ? footerLeft : "";
        this.footerRight = (footerRight != null) ? footerRight : "";
    }

    @Override
    public void handleEvent(Event event) {
        if (event instanceof PdfDocumentEvent) {
            PdfDocumentEvent docEvent = (PdfDocumentEvent) event;
            PdfDocument pdfDoc = docEvent.getDocument();
            PdfPage page = docEvent.getPage();

            // Check if current page is the target page
            if (pdfDoc.getPageNumber(page) == targetPage) {
                // Create a canvas to write on the PDF
                PdfCanvas pdfCanvas = new PdfCanvas(page.newContentStreamBefore(), page.getResources(), pdfDoc);

                InputStream fontNormalTTFStream = Export.class.getResourceAsStream("/font/cmunrm.ttf");

                FontProgram fontProgramNormal = null;
                try {
                    fontProgramNormal = FontProgramFactory.createFont(fontNormalTTFStream.readAllBytes());
                } catch (IOException e) {
                    // TODO
                }
                PdfFont fontNormal = PdfFontFactory.createFont(fontProgramNormal, PdfEncodings.IDENTITY_H);

                // Set font and size
                pdfCanvas.setFontAndSize(fontNormal, 11);

                float mvOffset = 50;

                // Draw header - left
                pdfCanvas.beginText()
                        .moveText(mvOffset, page.getPageSize().getTop() - mvOffset)
                        .showText(headerLeft)
                        .endText();

                // Draw header - right
                pdfCanvas.beginText()
                        .moveText(page.getPageSize().getWidth() - 3*mvOffset, page.getPageSize().getHeight() - mvOffset)
                        .showText(headerRight)
                        .endText();

                // Draw footer - left
                pdfCanvas.beginText()
                        .moveText(mvOffset, mvOffset)
                        .showText(footerLeft)
                        .endText();

                // Draw footer - right
                pdfCanvas.beginText()
                        .moveText(page.getPageSize().getWidth() - 3*mvOffset, mvOffset)
                        .showText(footerRight)
                        .endText();
            }
        }
    }

}
