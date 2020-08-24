package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.vo.Invoice;
import com.itextpdf.text.Chunk;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class InvoiceListener implements ApplicationListener<Invoice> {

    private final Logger LOGGER = LoggerFactory.getLogger(InvoiceListener.class);
    private final ResponderService slackResponderService;
    private Font font;
    private Font fontBold;

    @Autowired
    public InvoiceListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void onApplicationEvent(Invoice invoice) {
        DateTimeFormatter formatterOutputEng = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter formatterOutputUkr = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter formatterMonthYear = DateTimeFormatter.ofPattern("M-yyyy");
        DateTimeFormatter formatterMonthFullYearEng = DateTimeFormatter.ofPattern("MMMM yyyy");
        DateTimeFormatter formatterMonthFullYearUkr = DateTimeFormatter.ofPattern("LLLL yyyy", new Locale("uk"));

        Document document = null;
        FileOutputStream fileOutputStream = null;
        String fileName = String.format("ML-%s_%s.pdf",
                invoice.getFullNameEng().replaceAll(" ", "").trim(),
                LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy")));
        try {
            document = new Document();
            fileOutputStream = new FileOutputStream(fileName);
            try {
                PdfWriter.getInstance(document, fileOutputStream);
            } catch (DocumentException e) {
                LOGGER.error("Error during document instance obtaining", e);
                return;
            }
            document.open();

            Paragraph preface = new Paragraph();
            Paragraph head1 = getParagraphBold(String.format("Invoice/ offer # %s  for Services Agreement No. %s of %s",
                    LocalDate.now().format(formatterMonthYear), invoice.getAgreementNumber(),
                    new SimpleDateFormat("MM/dd/yyyy").format(invoice.getAgreementIssueDate())));
            head1.setAlignment(Element.ALIGN_CENTER);
            Paragraph head2 = getParagraphBold(String.format("Рахунок - оферта № %s згідно договору № %s від %s",
                    LocalDate.now().format(formatterMonthYear), invoice.getAgreementNumber(),
                    new SimpleDateFormat("dd.MM.yyyy").format(invoice.getAgreementIssueDate())));
            head2.setAlignment(Element.ALIGN_CENTER);
            preface.add(head1);
            preface.add(head2);
            addEmptyLine(preface, 1);

            PdfPTable table1 = new PdfPTable(2);
            table1.setHorizontalAlignment(Element.ALIGN_JUSTIFIED_ALL);
            table1.setWidthPercentage(100);
            table1.addCell(getParagraphWithBoldAndRegularText("Date and Place: ", String.format("%s, Kyiv",
                    LocalDate.now().withDayOfMonth(15).format(formatterOutputEng))));
            table1.addCell(getParagraphWithBoldAndRegularText("Дата та місце: ", String.format("%s, м.Київ",
                    LocalDate.now().withDayOfMonth(15).format(formatterOutputUkr))));

            table1.addCell(getParagraphWithBoldAndRegularText("Supplier: ", String.format("Private Entrepreneur %s",
                    invoice.getFullNameEng())));
            table1.addCell(getParagraphWithBoldAndRegularText("Виконавець: ", String.format("ФОП - %s", invoice.getFullNameUkr())));

            table1.addCell(getParagraphWithBoldAndRegularText("Address: ", String.format("Address: %s", invoice.getAddressEng())));
            table1.addCell(getParagraphWithBoldAndRegularText("що зареєстрований за адресою: ", invoice.getAddressUrk()));

            table1.addCell(getParagraphWithBoldAndRegularText("Individual Tax Number - ", invoice.getIpn()));
            table1.addCell(getParagraphWithBoldAndRegularText("ІПН - ", invoice.getIpn()));

            table1.addCell(getParagraphWithBoldAndRegularText("Customer: ","Mustard Labs, LLC \n" +
                    "Address: 1151 Eagle Dr #178, Loveland, CO 80537 \n" +
                    "Represented by CEO Elias Parker"));
            table1.addCell(getParagraphWithBoldAndRegularText("Замовник: ",  "Mustard Labs, LLC \n" +
                    "Адреса: 1151 Ігл Др. №178, Ловленд, КО 80537 \n" +
                    "в особі Виконавчого Директора Еліаса Паркера"));

            table1.addCell(getParagraphWithBoldAndRegularText("Subject matter: ", invoice.getServiceEng()));
            table1.addCell(getParagraphWithBoldAndRegularText("Предмет: ", invoice.getServiceUkr()));

            table1.addCell(getParagraphWithBoldAndRegularText("Period of providing services: ",
                    LocalDate.now().format(formatterMonthFullYearEng)));
            table1.addCell(getParagraphWithBoldAndRegularText("Період надання послуги: ",
                    LocalDate.now().format(formatterMonthFullYearUkr)));

            table1.addCell(getParagraphWithBoldAndRegularText("Currency: ","USD"));
            table1.addCell(getParagraphWithBoldAndRegularText("Валюта: ", "Долар США"));

            table1.addCell(getParagraphWithBoldAndRegularText("Price (amount) of the works/services: ", invoice.getPrice()));
            table1.addCell(getParagraphWithBoldAndRegularText("Ціна (загальна вартість) робіт/послуг: ", invoice.getPrice()));

            table1.addCell(getParagraphWithBoldAndRegularText("Terms of payments and acceptation: ",
                    "Postpayment of 100% upon the rendered services delivery."));
            table1.addCell(getParagraphWithBoldAndRegularText("Умови оплати та передачі: ", "100% післяоплата за фактом надання послуг."));

            table1.addCell(getParagraphWithBoldAndRegularText("Customer Bank information:\n",
                    "Beneficiary: Mustard Labs, LLC \n" +
                    "Account #: 7100005029 \n" +
                    "Beneficiary’s bank: Wells Fargo Bank \n" +
                    "Bank Address: 1102 Lincoln Avenue, Fort Collins, CO 80524, USA \n" +
                    "SWIFT code: WFBIUS6WFFX"));
            table1.addCell(getParagraphWithBoldAndRegularText("Supplier Bank information:\n" ,
                    String.format(
                            "Beneficiary: %s \n" +
                            "Account #: %s \n" +
                            "Beneficiary’s bank: %s \n" +
                            "Bank Address: %s \n" +
                            "SWIFT code: %s",
                    invoice.getFullNameEng(),
                    invoice.getAccountNumberUsd(),
                    invoice.getBankNameEng(),
                    invoice.getBankAddress(),
                    invoice.getSwiftNumber()
            )));

            table1.addCell(getParagraphBold("This Invoice/offer is the primary document"));
            table1.addCell(getParagraphBold("Цей Рахунок-оферта є первинним документом"));
            preface.add(table1);

            addEmptyLine(preface, 1);

            PdfPTable table2 = new PdfPTable(4);
            table2.setWidthPercentage(100);
            table2.setWidths(new float[]{10, 60, 15, 15});
            table2.setHorizontalAlignment(Element.ALIGN_JUSTIFIED_ALL);

            table2.addCell(getAlignedCenterCell("#/№"));
            table2.addCell(getAlignedCenterCell("Description /\nОпис"));
            table2.addCell(getAlignedCenterCell("Quantity of services /\nКількість послуг"));
            table2.addCell(getAlignedCenterCell("Amount, USD / \nЗагальна вартість, Долар США"));
            table2.setHeaderRows(1);

            table2.addCell(getAlignedCenterCell("1"));
            table2.addCell(getAlignedCenterCell(String.format("%s/%s", invoice.getServiceEng(), invoice.getServiceUkr())));
            table2.addCell(getAlignedCenterCell("1"));
            table2.addCell(getAlignedCenterCell(invoice.getPrice()));

            PdfPCell cell = new PdfPCell(getPhrase("Total to pay / Усього до сплати"));
            cell.setColspan(3);
            table2.addCell(cell);
            table2.addCell(getAlignedCenterCell(invoice.getPrice()));

            preface.add(table2);

            addEmptyLine(preface, 1);
            preface.add(getParagraphNormal("All charges of correspondent banks are at the Supplier’s expenses. " +
                    "/ Усі комісії банків-кореспондентів сплачує виконавець."));
            addEmptyLine(preface, 1);
            preface.add(getParagraphNormal("This Invoice/offer indicates, that payment " +
                    "according hereto at the same time is the evidence of the service delivery " +
                    "in full scope, acceptation thereof and the confirmation of final mutual " +
                    "installments between Parties. / Цей Рахунок-оферта вказує, що оплата згідно " +
                    "цього Рахунку-оферти одночасно є засвідченням надання послуг в повному обсязі, " +
                    "їх прийняття, а також підтвердженням кінцевих розрахунків між Сторонами."));
            addEmptyLine(preface, 1);
            preface.add(getParagraphNormal("Payment according hereto Invoice/offer shall " +
                    "be also the confirmation that Parties have no claims to each other and have no intention " +
                    "to submit any claims and shall not include penalty and fine clauses. / Оплата згідно цього Рахунку-оферти є " +
                    "підтвердженням того, що Сторони не мають взаємних претензій та " +
                    "не мають наміру направляти рекламації (претензії) та не передбачають штрафних санкцій."));
            addEmptyLine(preface, 2);
            preface.add(getParagraphNormal(String.format("Supplier/Виконавець: _______________________ (%s/ %s)",
                    invoice.getFullNameEng(), invoice.getFullNameUkr())));

            document.add(preface);


        } catch (IOException | DocumentException e) {
            LOGGER.error("Error during document creation", e);
            return;
        } finally {
            if (document != null) {
                document.close();
            }
            if (fileOutputStream != null) {
                try {
                    fileOutputStream.close();
                } catch (IOException e) {
                    LOGGER.error("Error during document closing", e);
                }
            }
        }

        slackResponderService.sendFile( fileName, invoice.getUserEmail(), invoice.getAuthorSlackId());
        slackResponderService.sendCompletionMessage(invoice.getAuthorSlackId(), invoice.getFullNameEng(),
                invoice.getUserEmail());
    }

    private Phrase getPhrase(String text) throws IOException, DocumentException {
        return new Phrase(text, getNormalFont());
    }

    private PdfPCell getAlignedCenterCell(String text) throws IOException, DocumentException {
        PdfPCell cell = new PdfPCell(getPhrase(text));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private Paragraph getParagraphNormal(String text) throws IOException, DocumentException {
        return new Paragraph(text, getNormalFont());
    }

    private Paragraph getParagraphBold(String text) throws IOException, DocumentException {
        return new Paragraph(text, getBoldFont());
    }

    private Paragraph getParagraphWithBoldAndRegularText(String boldText, String regularText) throws IOException,
            DocumentException {
        Paragraph paragraph = new Paragraph();
        paragraph.add(new Chunk(boldText, getBoldFont()));
        paragraph.add(new Chunk(regularText, getNormalFont()));
        return paragraph;
    }

    private Font getNormalFont() throws IOException, DocumentException {
        if (this.font != null) {
            return font;
        }
        BaseFont bf = BaseFont.createFont("/font/clear-sans.regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        this.font = new Font(bf, 11, Font.NORMAL);
        return font;
    }

    private Font getBoldFont() throws IOException, DocumentException {
        if (this.fontBold != null) {
            return fontBold;
        }
        BaseFont bf = BaseFont.createFont("/font/clear-sans.bold.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        this.fontBold = new Font(bf, 11, Font.NORMAL);
        return fontBold;
    }

    private static void addEmptyLine(Paragraph paragraph, int number) {
        for (int i = 0; i < number; i++) {
            paragraph.add(new Paragraph(" "));
        }
    }
}
