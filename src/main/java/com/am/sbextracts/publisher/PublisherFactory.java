package com.am.sbextracts.publisher;

import com.am.sbextracts.vo.SlackEvent;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Arrays;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PublisherFactory {

    private final InvoicePublisher invoicePublisher;
    private final PayslipPublisher payslipPublisher;
    private final TaxPaymentPublisher taxPaymentPublisher;
    private final BMessagePublisher bMessagePublisher;

    private Map<Type, Publisher> publisherMap;

    @PostConstruct
    public void initMap() {
        publisherMap = Map.of(
                Type.INVOICE, invoicePublisher,
                Type.TAX_PAYMENT, taxPaymentPublisher,
                Type.TAX_PAYMENT_WITH_EMAL, taxPaymentPublisher,
                Type.PAYSLIP, payslipPublisher,
                Type.BROADCAST_MESSAGE, bMessagePublisher
        );
    }

    public Publisher getProducer(SlackEvent.FileMetaInfo fileMetaInfo) {
        Type type = Type.getByFileName(fileMetaInfo.getName());
        return publisherMap.get(type);
    }

    public enum Type {
        TAX_PAYMENT("tp"),
        TAX_PAYMENT_WITH_EMAL("tpwe"),
        INVOICE("in"),
        PAYSLIP("ps"),
        BROADCAST_MESSAGE("bm");

        public static Type getByFileName(String fileName) {
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

        @Override
        public String toString() {
            return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                    .append("name", this.name())
                    .append("suffix", this.suffix)
                    .toString();
        }
    }
}
