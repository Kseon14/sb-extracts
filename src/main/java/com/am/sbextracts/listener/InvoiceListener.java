package com.am.sbextracts.listener;

import com.am.sbextracts.service.ResponderService;
import com.am.sbextracts.vo.Invoice;
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
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.Dsl;
import org.asynchttpclient.Request;
import org.asynchttpclient.RequestBuilder;
import org.asynchttpclient.request.body.multipart.FilePart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@Component
public class InvoiceListener implements ApplicationListener<Invoice> {

    private final Logger LOGGER = LoggerFactory.getLogger(InvoiceListener.class);
    private final ResponderService slackResponderService;
    private Font font;

    @Autowired
    public InvoiceListener(ResponderService slackResponderService) {
        this.slackResponderService = slackResponderService;
    }

    @Override
    public void onApplicationEvent(Invoice invoice) {
        DateTimeFormatter formatterInput = DateTimeFormatter.ofPattern("M/d/yy");
        DateTimeFormatter formatterOutputEng = DateTimeFormatter.ofPattern("MM/dd/yyyy");
        DateTimeFormatter formatterOutputUkr = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        DateTimeFormatter formatterMonthYear = DateTimeFormatter.ofPattern("M-yyyy");
        DateTimeFormatter formatterMonthFullYearEng = DateTimeFormatter.ofPattern("MMMM yyyy");
        DateTimeFormatter formatterMonthFullYearUkr = DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("uk"));

        Document document = null;
        FileOutputStream fileOutputStream = null;
        String fileName = String.format("ML-%s_%s.pdf",
                invoice.getFullNameEng().replaceAll(" ", "").trim(),
                LocalDate.now().format(DateTimeFormatter.ofPattern("MM-yyyy")));
        try {
            document = new Document();
            fileOutputStream =
                    new FileOutputStream(fileName);
            try {
                PdfWriter.getInstance(document, fileOutputStream);
            } catch (DocumentException e) {
                LOGGER.error("Error during document creation", e);
                return;
            }
            document.open();

            Paragraph preface = new Paragraph();
            Paragraph head1 = getParagraph(String.format("Invoice/ offer # %s  for Services Agreement No. %s of %s",
                    LocalDate.now().format(formatterMonthYear), invoice.getAgreementNumber(),
                    getFormattedDate(invoice.getAgreementIssueDate(), formatterInput, formatterOutputEng)));
            head1.setAlignment(Element.ALIGN_CENTER);
            Paragraph head2 = getParagraph(String.format("Рахунок - оферта № %s згідно договору № %s від %s",
                    LocalDate.now().format(formatterMonthYear), invoice.getAgreementNumber(),
                    getFormattedDate(invoice.getAgreementIssueDate(), formatterInput, formatterOutputUkr)));
            head2.setAlignment(Element.ALIGN_CENTER);
            preface.add(head1);
            preface.add(head2);
            addEmptyLine(preface, 1);

            PdfPTable table1 = new PdfPTable(2);
            table1.setHorizontalAlignment(Element.ALIGN_JUSTIFIED_ALL);
            table1.setWidthPercentage(100);
            table1.addCell(getPhrase(String.format("Date and Place: %s, Kyiv",
                    LocalDate.now().withDayOfMonth(15).format(formatterOutputEng))));
            table1.addCell(getPhrase(String.format("Дата та місце: %s, м.Київ",
                    LocalDate.now().withDayOfMonth(15).format(formatterOutputUkr))));

            table1.addCell(getPhrase(String.format("Supplier: Private Entrepreneur %s", invoice.getFullNameEng())));
            table1.addCell(getPhrase(String.format("Виконавець: ФОП - %s", invoice.getFullNameUkr())));

            table1.addCell(getPhrase(String.format("Address: %s", invoice.getAddressEng())));
            table1.addCell(getPhrase(String.format("що зареєстрований за адресою: %s", invoice.getAddressUrk())));

            table1.addCell(getPhrase(String.format("Individual Tax Number - %s", invoice.getIpn())));
            table1.addCell(getPhrase(String.format("ІПН - %s", invoice.getIpn())));

            table1.addCell(getPhrase("Customer: Mustard Labs, LLC \n" +
                    "Address: 1151 Eagle Dr #178, Loveland, CO 80537 \n" +
                    "Represented by CEO Elias Parker"));
            table1.addCell(getPhrase("Замовник: Mustard Labs, LLC \n" +
                    "Адреса: 1151 Ігл Др. №178, Ловленд, КО 80537 \n" +
                    "в особі Виконавчого Директора Еліаса Паркера"));

            table1.addCell(getPhrase(String.format("Subject matter: %s", invoice.getServiceEng())));
            table1.addCell(getPhrase(String.format("Предмет: %s", invoice.getServiceUkr())));

            table1.addCell(getPhrase(String.format("Period of providing services: %s",
                    LocalDate.now().format(formatterMonthFullYearEng))));
            table1.addCell(getPhrase(String.format("Період надання послуги: %s",
                    LocalDate.now().format(formatterMonthFullYearUkr))));

            table1.addCell(getPhrase("Currency: USD"));
            table1.addCell(getPhrase("Валюта: Долар США"));

            table1.addCell(getPhrase(String.format("Price (amount) of the works/services: %s", invoice.getPrice())));
            table1.addCell(getPhrase(String.format("Ціна (загальна вартість) робіт/послуг: %s", invoice.getPrice())));

            table1.addCell(getPhrase("Terms of payments and acceptation:" +
                    "Postpayment of 100% upon the rendered services delivery."));
            table1.addCell(getPhrase("Умови оплати та передачі: 100% післяоплата за фактом надання послуг."));

            table1.addCell(getPhrase("Customer Bank information:\n" +
                    "Beneficiary: Mustard Labs, LLC \n" +
                    "Account #: 7100005029 \n" +
                    "Beneficiary’s bank: Wells Fargo Bank \n" +
                    "Bank Address: 1102 Lincoln Avenue, Fort Collins, CO 80524, USA \n" +
                    "SWIFT code: WFBIUS6WFFX"));
            table1.addCell(getPhrase(String.format("Supplier Bank information:\n" +
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

            table1.addCell(getPhrase("This Invoice/offer is the primary document"));
            table1.addCell(getPhrase("Цей Рахунок-оферта є первинним документом"));
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
            preface.add(getParagraph("All charges of correspondent banks are at the Supplier’s expenses. " +
                    "/ Усі комісії банків-кореспондентів сплачує виконавець."));
            addEmptyLine(preface, 1);
            preface.add(getParagraph("This Invoice/offer indicates, that payment " +
                    "according hereto at the same time is the evidence of the service delivery " +
                    "in full scope, acceptation thereof and the confirmation of final mutual " +
                    "installments between Parties./ Цей Рахунок-оферта вказує, що оплата згідно " +
                    "цього Рахунку-оферти одночасно є засвідченням надання послуг в повному обсязі, " +
                    "їх прийняття, а також підтвердженням кінцевих розрахунків між Сторонами."));
            addEmptyLine(preface, 1);
            preface.add(getParagraph("Payment according hereto Invoice/offer shall " +
                    "be also the confirmation that Parties have no claims to each other and have no intention " +
                    "to submit any claims and shall not include penalty and fine clauses. /Оплата згідно цього Рахунку-оферти є " +
                    "підтвердженням того, що Сторони не мають взаємних претензій та " +
                    "не мають наміру направляти рекламації (претензії) та не передбачають штрафних санкцій."));
            addEmptyLine(preface, 2);
            preface.add(getParagraph(String.format("Supplier/Виконавець: _______________________ (%s/ %s)",
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

        slackResponderService.sendFile( fileName, invoice.getUserEmail());
        slackResponderService.sendCompletionMessage(invoice.getAuthorSlackId(), invoice.getFullNameEng(),
                invoice.getUserEmail());
    }

    private Phrase getPhrase(String text) throws IOException, DocumentException {
        return new Phrase(text, getFont());
    }

    private PdfPCell getAlignedCenterCell(String text) throws IOException, DocumentException {
        PdfPCell cell = new PdfPCell(getPhrase(text));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private Paragraph getParagraph(String text) throws IOException, DocumentException {
        return new Paragraph(text, getFont());
    }

    private Font getFont() throws IOException, DocumentException {
        if (this.font != null) {
            return font;
        }
        BaseFont bf = BaseFont.createFont("/font/clear-sans.regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        this.font = new Font(bf, 11, Font.NORMAL);
        return font;
    }

    private static void addEmptyLine(Paragraph paragraph, int number) {
        for (int i = 0; i < number; i++) {
            paragraph.add(new Paragraph(" "));
        }
    }

    private String getFormattedDate(String inputDate, DateTimeFormatter formatterInput,
                                    DateTimeFormatter formatterOutput) {
        LocalDate parsedDate = LocalDate.parse(inputDate, formatterInput);
        return parsedDate.format(formatterOutput);
    }
}
