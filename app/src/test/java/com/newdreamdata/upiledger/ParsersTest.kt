package com.newdreamdata.upiledger

import com.newdreamdata.upiledger.data.PayApp
import com.newdreamdata.upiledger.parse.CsvStatement
import com.newdreamdata.upiledger.parse.Direction
import com.newdreamdata.upiledger.parse.ReceiptParser
import com.newdreamdata.upiledger.parse.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test fun icici_sms() {
        val p = SmsParser.parse("INR 250.00 debited from A/c no. XX1234 on 20-10-26 to VPA abc@okaxis (UPI Ref No 528312345678) - ICICI Bank")
        assertEquals(25000L, p.amountPaise)
        assertEquals("abc@okaxis", p.upiId)
        assertEquals("528312345678", p.txnId)
        assertEquals(Direction.PAID, p.direction)
    }

    @Test fun hdfc_sms() {
        val p = SmsParser.parse("Sent Rs.45.00 From HDFC Bank A/C *1234 To RAJU TEA STALL On 20/10/26 Ref 528312345678 Not You? Call 18002586161")
        assertEquals(4500L, p.amountPaise)
        assertEquals("RAJU TEA STALL", p.payee)
        assertEquals("528312345678", p.txnId)
    }

    @Test fun sbi_sms() {
        val p = SmsParser.parse("Dear UPI user A/C X1234 debited by 45.0 on date 20Oct26 trf to RAJU TEA STALL Refno 528312345678. If not u? call 1800111109. -SBI")
        assertEquals(4500L, p.amountPaise)
        assertEquals("RAJU TEA STALL", p.payee)
        assertEquals("528312345678", p.txnId)
        assertNotNull(p.timestamp)
    }

    @Test fun kotak_sms_with_upi() {
        val p = SmsParser.parse("Rs 120 debited from Kotak Bank A/c XX1234 to raju@ybl on 20-10-2026. UPI Ref 528312345678. Not you? kotak.com/fraud")
        assertEquals(12000L, p.amountPaise)
        assertEquals("raju@ybl", p.upiId)
        assertEquals(PayApp.PHONEPE, p.app)
    }

    @Test fun credit_is_detected() {
        val p = SmsParser.parse("Rs.500.00 credited to A/c XX1234 on 20-10-26 from VPA someone@okicici UPI Ref 528312345678")
        assertEquals(Direction.RECEIVED, p.direction)
    }

    @Test fun otp_is_not_payment() {
        assertFalse(SmsParser.looksLikePayment("Your OTP for Rs.4500 transaction is 123456. Do not share."))
    }

    @Test fun gpay_receipt() {
        val ocr = """
            ₹45
            Paid to
            Raju Tea Stall
            raju.tea@okicici
            Completed
            20 Oct 2026, 8:12 am
            UPI transaction ID
            528312345678
            Google Pay
        """.trimIndent()
        val p = ReceiptParser.parse(ocr)
        assertEquals(4500L, p.amountPaise)
        assertEquals("Raju Tea Stall", p.payee)
        assertEquals("raju.tea@okicici", p.upiId)
        assertEquals("528312345678", p.txnId)
        assertEquals(PayApp.GPAY, p.app)
    }

    @Test fun amazon_receipt_from_and_to() {
        val ocr = """
            Payment successful
            ₹1,250.00
            Paid to Fresh Mart Groceries
            UPI ID: freshmart@apl
            From: Me
            UPI ID: me@okhdfcbank
            Reference ID 528312345678
            Amazon Pay
        """.trimIndent()
        val p = ReceiptParser.parse(ocr)
        assertEquals(125000L, p.amountPaise)
        assertEquals("Fresh Mart Groceries", p.payee)
        assertEquals("freshmart@apl", p.upiId)
        assertEquals(PayApp.AMAZON_PAY, p.app)
    }

    @Test fun csv_hdfc_like() {
        val csv = """
            Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
            20/10/26,UPI-RAJU TEA STALL-raju.tea@okicici-ICIC0001234-528312345678-Payment,0000528312345678,20/10/26,45.00,,10000.00
            20/10/26,UPI-SALARY-emp@okaxis-UTIB0000001-528312345679-Credit,0000528312345679,20/10/26,,50000.00,60000.00
        """.trimIndent()
        val p = CsvStatement.parse(csv)
        assertTrue(p.headerFound)
        assertEquals(1, p.debitRows)
        assertEquals(1, p.creditRowsIgnored)
        val r = p.rows.first()
        assertEquals(4500L, r.amountPaise)
        assertEquals("raju.tea@okicici", r.upiId)
        assertEquals("RAJU TEA STALL", r.payee)
    }

    @Test fun csv_single_amount_column_with_type() {
        val csv = """
            Transaction Date,Description,Amount,Dr/Cr
            2026-10-20,UPI/528312345678/Payment/raju@ybl/Raju Tea,45,DR
            2026-10-21,UPI/528312345699/Refund/shop@paytm/Shop,100,CR
        """.trimIndent()
        val p = CsvStatement.parse(csv)
        assertEquals(1, p.debitRows)
        assertEquals("raju@ybl", p.rows.first().upiId)
        assertEquals("Raju Tea", p.rows.first().payee)
    }
}
