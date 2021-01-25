package sbextracts.publisher

import com.am.sbextracts.publisher.PublisherFactory
import com.am.sbextracts.publisher.XlsxUtil
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import spock.lang.Specification

class XlsxUtilTest extends Specification {

    def "test incorrect validation INVOICE"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/incorrect-xlsx/in-Dec.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.INVOICE, new XSSFWorkbook(inputStream))
        then:
        final UnsupportedOperationException exception = thrown()
    }

    def "test correct INVOICE"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/correct-xlsx/in-Dec.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.INVOICE, new XSSFWorkbook(inputStream))
        then:
        noExceptionThrown()
    }

    def "test incorrect validation TAX_PAYMENT"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/incorrect-xlsx/tp-insurance.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.TAX_PAYMENT, new XSSFWorkbook(inputStream))
        then:
        final UnsupportedOperationException exception = thrown()
    }

    def "test correct validation TAX_PAYMENT"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/correct-xlsx/tp-insurance.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.TAX_PAYMENT, new XSSFWorkbook(inputStream))
        then:
        noExceptionThrown()
    }

    def "test incorrect validation BROADCAST_MESSAGE"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/incorrect-xlsx/bm-extracts.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.BROADCAST_MESSAGE, new XSSFWorkbook(inputStream))
        then:
        final UnsupportedOperationException exception = thrown()
    }

    def "test correct validation PAYSLIP"(){
        when:
        InputStream inputStream =
                getClass().getResourceAsStream("/correct-xlsx/ps-Jan.xlsx")
        XlsxUtil.validateFile(PublisherFactory.Type.PAYSLIP, new XSSFWorkbook(inputStream))
        then:
        noExceptionThrown()
    }

}
