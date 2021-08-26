package com.am.sbextracts.service.integration.utils;

import com.itextpdf.awt.geom.Rectangle2D;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.parser.ImageRenderInfo;
import com.itextpdf.text.pdf.parser.PdfReaderContentParser;
import com.itextpdf.text.pdf.parser.RenderListener;
import com.itextpdf.text.pdf.parser.TextRenderInfo;
import lombok.SneakyThrows;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.List;

public class GetCharLocationAndSize extends PDFTextStripper {

    public GetCharLocationAndSize() throws IOException {
    }

    /**
     * @throws IOException
     *             If there is an error parsing the document.
     */
    @SneakyThrows
    public static void getCoordinates(PDDocument document, InputStream pdf, byte[] pdfByte) throws IOException {

        // PDFText pdfText = new PDFText (pdf, null);
        // for (int pageIx = 0; pageIx < pdfText.getPageCount(); ++pageIx) {
        //
        // Vector<TextPosition> wordList = pdfText.getWordsWithPositions(pageIx);
        // for (TextPosition textPosition : wordList) {
        // if(textPosition.getText().equals("підпис")) {
        // System.out.println(textPosition.getText());
        // for(Point2D[] point2D: textPosition.getPDFQuadrilaterals()){
        // System.out.println(point2D[0].getX());
        // System.out.println(point2D[0].getY());
        //
        // }
        // }
        // }
        // }

        PDFTextStripper stripper = new GetCharLocationAndSize();
        stripper.setSortByPosition(true);
        stripper.setStartPage(0);
        stripper.setEndPage(document.getNumberOfPages());

        Writer dummy = new OutputStreamWriter(new ByteArrayOutputStream());
        stripper.writeText(document, dummy);

    }

    /**
     * Override the default functionality of PDFTextStripper.writeString()
     */
    @Override
    protected void writeString(String string, List<TextPosition> textPositions) throws IOException {
        for (TextPosition text : textPositions) {
            System.out.println(text.getUnicode() + " [(X=" + text.getXDirAdj() + ",Y=" + text.getYDirAdj() + ") height="
                    + text.getHeightDir() + " width=" + text.getWidthDirAdj() + "]");
        }
    }

    public static Integer[] getFontPosition(byte[] pdf, final String keyWord, Integer pageNum) throws IOException {
        final Integer[] result = new Integer[2];
        PdfReader pdfReader = new PdfReader(pdf);
        if (null == pageNum) {
            pageNum = pdfReader.getNumberOfPages();
        }
        new PdfReaderContentParser(pdfReader).processContent(pageNum, new RenderListener() {
            public void beginTextBlock() {

            }

            public void renderText(TextRenderInfo textRenderInfo) {
                String text = textRenderInfo.getText();
                if (text != null && text.equals(keyWord)) {
                    // The abscissa and ordinate of the text in the page
                    Rectangle2D.Float textFloat = textRenderInfo.getBaseline().getBoundingRectange();
                    textFloat.getCenterX();
                    float x = textFloat.x;
                    float y = textFloat.y;
                    result[0] = (int) x;
                    result[1] = (int) y;
                    System.out.println(String.format("The signature text field absolute position is x:%s, y:%s", x, y));
                }
            }

            public void endTextBlock() {

            }

            public void renderImage(ImageRenderInfo renderInfo) {

            }
        });
        return result;
    }
}
