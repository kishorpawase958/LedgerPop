package app.ledgerpop.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {

    @Test
    fun testCanaraBankCredit() {
        val sms = "Dear Customer, Acct XXXX8608 credited with INR 100.00 on 10/08/26 from GAGAN DEEP H; UPI:658895748053; Bal INR 9,642.38-CanaraBank"
        val result = SmsParser.parse("AD-CANBNK", sms)
        assertNotNull(result)
        assertEquals(100.0, result?.amount)
        assertEquals("CREDIT", result?.type)
        assertEquals("GAGAN DEEP H", result?.merchant)
    }

    @Test
    fun testCanaraBankDebit() {
        val sms = "Dear Customer, Acct XXXX8608 Dr. INR 360.00 on 12/08/26 to D KARTHIK; UPI: 622471755861; Bal INR 8,467.70.Not you?SMS BLOCKUPI to 9901771222-CanaraBank"
        val result = SmsParser.parse("AD-CANBNK", sms)
        assertNotNull(result)
        assertEquals(360.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("D KARTHIK", result?.merchant)
    }

    @Test
    fun testSIBCardSwiggy() {
        val sms = "Tasty choice! Rs. 361.00 spent at Swiggy Pvt Ltd Food1 on your SIB One Credit Card xxXX8289. Reward points added. To dispute, click: https://1crd.in/OneCrd/HQL"
        val result = SmsParser.parse("SIB-CARD", sms)
        assertNotNull(result)
        assertEquals(361.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("Swiggy Pvt Ltd Food1", result?.merchant)
    }

    @Test
    fun testSIBCardZepto() {
        val sms = "Fresh picks! Rs. 518.00 spent at Zepto Marketplace Priv on your SIB One Credit Card xxXX8289. Reward points added. To dispute, click: https://1crd.in/OneCrd/HQL"
        val result = SmsParser.parse("SIB-CARD", sms)
        assertNotNull(result)
        assertEquals(518.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("Zepto Marketplace Priv", result?.merchant)
    }

    @Test
    fun testSIBCardFlipkart() {
        val sms = "You've paid Rs. 2,318.00 at Flipkart Internet Pvt with your SIB One Credit Card xxXX8289 and earned reward points. To dispute, click: https://1crd.in/OneCrd/HQL"
        val result = SmsParser.parse("SIB-CARD", sms)
        assertNotNull(result)
        assertEquals(2318.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("Flipkart Internet Pvt", result?.merchant)
    }

    @Test
    fun testICICISpendPolicyBazaar() {
        val sms = "INR 3,047.00 spent using ICICI Bank Card XX3006 on 04-Sep-26 on POLICYBAZAAR. Avl Limit: INR 1,96,953.00. If not you, call 1800 2662/SMS BLOCK 3006 to 9215676766."
        val result = SmsParser.parse("ICICIB", sms)
        assertNotNull(result)
        assertEquals(3047.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("POLICYBAZAAR", result?.merchant)
    }

    @Test
    fun testAxisBlinkit() {
        val sms = "Spent INR 484 Axis Bank Card no. XX4143 01-09-26 14:15:52 IST BLINKIT Avl Limit: INR 194818.26 Not you? SMS BLOCK 4143 to 919951860002"
        val result = SmsParser.parse("AXISBK", sms)
        assertNotNull(result)
        assertEquals(484.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("BLINKIT", result?.merchant)
    }

    @Test
    fun testSBIUPIDebitVikas() {
        val sms = "Dear UPI user A/C X3789 debited by 63.00 on date 11Sep26 trf to VIKAS TUKARAM BH Refno 244471042546 If not u? call-1800111109 for other services-18001234-SBI"
        val result = SmsParser.parse("SBI-UPI", sms)
        assertNotNull(result)
        assertEquals(63.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("VIKAS TUKARAM BH", result?.merchant)
    }

    @Test
    fun testAxisRTGSCredit() {
        val sms = "INR 200000.00 credited to A/c no. XX0991 on 10-09-26 at 16:40:00 IST. Info - RTGS/ICICR2202609101867836. Chk Bal https://ccm.axis.bank.in/AXISBK/m49XJ3KM - Axis Bank"
        val result = SmsParser.parse("AXIS-BK", sms)
        assertNotNull(result)
        assertEquals(200000.0, result?.amount)
        assertEquals("CREDIT", result?.type)
        assertEquals("ICICR2202609101867836", result?.merchant)
    }

    @Test
    fun testSBIUPIDebitPassportSeva() {
        val sms = "Dear UPI user A/C X3789 debited by 750.00 on date 09Sep26 trf to Passport Seva Pr Refno 646703382526 If not u? call-1800111109 for other services-18001234-SBI"
        val result = SmsParser.parse("SBI-UPI", sms)
        assertNotNull(result)
        assertEquals(750.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("Passport Seva Pr", result?.merchant)
    }

    @Test
    fun testICICIDMartLeadingDigits() {
        val sms = "INR 4,394.81 spent using ICICI Bank Card XX3006 on 12-Sep-26 on 4046 D MART BEL. Avl Limit: INR 1,92,558.19. If not you, call 1800 2662/SMS BLOCK 3006 to 9215676766."
        val result = SmsParser.parse("ICICI-BK", sms)
        assertNotNull(result)
        assertEquals(4394.81, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("4046 D MART BEL", result?.merchant)
    }

    @Test
    fun testKotakBankSentDebit() {
        val sms = "Sent Rs.1000.00 from Kotak Bank A/c X9112 to SHOBHA SOMNATH PAWAS on 14-09-26. UPI Ref 129608870794. Not done by you? Tap https://kotak.bank.in/KBANKT/Fraud"
        val result = SmsParser.parse("KOTAKB", sms)
        assertNotNull(result)
        assertEquals(1000.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("SHOBHA SOMNATH PAWAS", result?.merchant)
        assertEquals("9112", result?.accountLast4)
        assertEquals("Kotak Bank", result?.bank)
        assertEquals("129608870794", result?.refNo)
    }

    @Test
    fun testKotakBankReceivedCredit() {
        val sms = "Received Rs.54.00 in your Kotak Bank AC 9112 from Ashish Somnath Pawas on 15-09-26.UPI Ref:625809441973"
        val result = SmsParser.parse("KOTAKB", sms)
        assertNotNull(result)
        assertEquals(54.0, result?.amount)
        assertEquals("CREDIT", result?.type)
        assertEquals("Ashish Somnath Pawas", result?.merchant)
        assertEquals("9112", result?.accountLast4)
        assertEquals("Kotak Bank", result?.bank)
        assertEquals("625809441973", result?.refNo)
    }

    @Test
    fun testIgnorePolicySender() {
        val sms = "Kishor Pawase payment of Rs.3047 for your iPru policy no. J7525399 has been successfully debited on Sep 4, 2026. Team Policybazaar"
        val result = SmsParser.parse("POLBAZ", sms)
        assertNull(result)
    }

    @Test
    fun testIgnoreOTP() {
        val sms = "980000 is One-Time Password for INR 2.00 transaction towards POLICYBAZAA using ICICI Bank Credit Card XX3006. OTPs are SECRET. DO NOT disclose"
        val result = SmsParser.parse("ICICIB", sms)
        assertNull(result)
    }

    @Test
    fun testUnionBankDebit1() {
        val sms = "Union Bank of India A/c*9159 Dr Rs719.00 on 21-11-26 10:40 by MB 669106991321 Fv.Kisho, Bal Rs12358.91 Not U?Call 18002333/SMS BLOCK 9159 to 8879635472"
        val result = SmsParser.parse("AD-UBIN", sms)
        assertNotNull(result)
        assertEquals(719.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("Kisho", result?.merchant)
        assertEquals("9159", result?.accountLast4)
        assertEquals("Union Bank", result?.bank)
        assertEquals("669106991321", result?.refNo)
    }

    @Test
    fun testUnionBankDebit2() {
        val sms = "Union Bank of India A/c*9159 Dr Rs110.00 on 20-11-26 20:34 by MB 669021946324 Fv.RAMES, Bal Rs13077.91 Not U?Call 18002333/SMS BLOCK 9159 to 8879635472"
        val result = SmsParser.parse("AD-UBIN", sms)
        assertNotNull(result)
        assertEquals(110.0, result?.amount)
        assertEquals("DEBIT", result?.type)
        assertEquals("RAMES", result?.merchant)
        assertEquals("9159", result?.accountLast4)
        assertEquals("Union Bank", result?.bank)
        assertEquals("669021946324", result?.refNo)
    }
}
