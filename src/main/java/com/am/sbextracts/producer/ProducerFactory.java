package com.am.sbextracts.producer;

import com.am.sbextracts.vo.SlackEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;

@Component
public class ProducerFactory {

    private final InvoicePublisher invoicePublisher;
    private final PayslipPublisher payslipPublisher;
    private final TaxPaymentPublisher taxPaymentPublisher;

    @Autowired
    public ProducerFactory(InvoicePublisher invoicePublisher,
                           PayslipPublisher payslipPublisher,
                           TaxPaymentPublisher taxPaymentPublisher) {
        this.invoicePublisher = invoicePublisher;
        this.payslipPublisher = payslipPublisher;
        this.taxPaymentPublisher = taxPaymentPublisher;
    }

    public Map<Type, Publisher> getPublishersMap(){
        Map<Type, Publisher> publisherMap = new EnumMap<>(Type.class);
        publisherMap.put(ProducerFactory.Type.INVOICE, invoicePublisher);
        publisherMap.put(ProducerFactory.Type.TAX_PAYMENT, taxPaymentPublisher);
        publisherMap.put(ProducerFactory.Type.PAYSLIP, payslipPublisher);
        return publisherMap;
    }

    public Publisher getProducer(SlackEvent.FileMetaInfo fileMetaInfo) {
        Type type = Type.getByFileName(fileMetaInfo.getName());
        return getPublishersMap().get(type);
    }

    private enum Type {
        TAX_PAYMENT("tp"),
        INVOICE("in") ,
        PAYSLIP("ps");

        public static Type getByFileName(String fileName){
            String suffix = fileName.split("-")[0];
            return Arrays.stream(Type.values())
                    .filter(t -> StringUtils.equalsIgnoreCase(suffix, t.suffix))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown purpose:" + suffix));
        }

        private final String suffix;

        Type(String suffix) {
            this.suffix = suffix;
        }
    }
}
