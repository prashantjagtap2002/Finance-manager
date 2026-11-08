package com.example.financemanager.domain

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsRuleEngineTest {

    @After
    fun resetParserConfig() {
        SmsParser.config = SmsParserConfig()
    }

    private val kotakSent =
        "Sent Rs.40.00 from Kotak Bank A/c X2769 to CITY MART on 22-09-26. UPI Ref 526512345678. Not you, call 18602662666"

    @Test
    fun templateCapturesFields() {
        val rule = SmsRule(
            id = "1", name = "Kotak sent",
            pattern = "Sent Rs.{amount} from Kotak Bank A/c {any} to {counterparty} on {any} UPI Ref {reference}",
            accountName = "Kotak Salary"
        )
        val result = SmsRuleEngine.apply(listOf(rule), "AD-KOTAKB-S", kotakSent)

        val parsed = result?.parsed
        assertNotNull(parsed)
        assertEquals("debit", parsed!!.type)
        assertEquals("Rs.40.00", parsed.amount)
        assertEquals("CITY MART", parsed.counterparty)
        assertEquals("526512345678", parsed.reference)
        assertEquals("Kotak Salary", parsed.accountName)
    }

    @Test
    fun templateIgnoresCaseAndSpacing() {
        val rule = SmsRule(id = "1", name = "r", pattern = "sent   rs.{amount}  FROM kotak bank", action = SmsRuleAction.DEBIT)
        assertEquals("Rs.40.00", SmsRuleEngine.apply(listOf(rule), "X", kotakSent)?.parsed?.amount)
    }

    @Test
    fun trailingTextPlaceholderStopsAtSentenceBreak() {
        val rule = SmsRule(id = "1", name = "r", pattern = "Received Rs.{amount} from {counterparty}", action = SmsRuleAction.CREDIT)
        val parsed = SmsRuleEngine.apply(listOf(rule), "X", "Received Rs.1,500 from Rahul Sharma. Ref 12345")?.parsed

        assertEquals("credit", parsed?.type)
        assertEquals("Rs.1500", parsed?.amount)
        assertEquals("Rahul Sharma", parsed?.counterparty)
    }

    @Test
    fun regexRuleUsesNamedGroups() {
        val rule = SmsRule(
            id = "1", name = "r", isRegex = true,
            pattern = "Sent Rs\\.(?<amount>[0-9,.]+) from (?<account>Kotak Bank) A/c (X\\d+) to (?<counterparty>.+?) on"
        )
        val parsed = SmsRuleEngine.apply(listOf(rule), "X", kotakSent)?.parsed

        assertEquals("Rs.40.00", parsed?.amount)
        assertEquals("Kotak Bank", parsed?.accountName)
        assertEquals("CITY MART", parsed?.counterparty)
    }

    @Test
    fun senderFilterMustMatch() {
        val rule = SmsRule(id = "1", name = "r", senderContains = "HDFCBK", pattern = "Sent Rs.{amount}")
        assertNull(SmsRuleEngine.apply(listOf(rule), "AD-KOTAKB-S", kotakSent))
        assertNotNull(SmsRuleEngine.apply(listOf(rule), "VM-HDFCBK", kotakSent))
    }

    @Test
    fun disabledRuleIsSkipped() {
        val rule = SmsRule(id = "1", name = "r", pattern = "Sent Rs.{amount}", enabled = false)
        assertNull(SmsRuleEngine.apply(listOf(rule), "X", kotakSent))
    }

    @Test
    fun ignoreRuleDropsTheMessageBeforeBuiltIns() {
        SmsParser.config = SmsParserConfig(
            customRules = listOf(SmsRule(id = "1", name = "drop city mart", pattern = "to CITY MART", action = SmsRuleAction.IGNORE))
        )
        assertNull(SmsParser.parse("AD-KOTAKB-S", kotakSent))
        val source = SmsParser.parseDetailed("AD-KOTAKB-S", kotakSent).source
        assertTrue(source is SmsParseSource.Custom)
    }

    @Test
    fun customRuleOverridesBuiltIn() {
        SmsParser.config = SmsParserConfig(
            customRules = listOf(SmsRule(id = "1", name = "r", pattern = "Sent Rs.{amount}", action = SmsRuleAction.CREDIT, accountName = "Mine"))
        )
        val parsed = SmsParser.parse("AD-KOTAKB-S", kotakSent)
        assertEquals("credit", parsed?.type)
        assertEquals("Mine", parsed?.accountName)
    }

    @Test
    fun builtInIsUsedWithoutRules() {
        val result = SmsParser.parseDetailed("AD-KOTAKB-S", kotakSent)
        assertEquals(SmsParseSource.BuiltIn(BuiltInSmsParser.KOTAK), result.source)
        assertEquals("Kotak", result.parsed?.accountName)
    }

    @Test
    fun disablingBuiltInFallsThroughToGeneric() {
        SmsParser.config = SmsParserConfig(disabledBuiltIns = setOf(BuiltInSmsParser.KOTAK))
        val result = SmsParser.parseDetailed("AD-KOTAKB-S", kotakSent)
        assertEquals(SmsParseSource.BuiltIn(BuiltInSmsParser.GENERIC), result.source)
        assertEquals("Rs.40.00", result.parsed?.amount)
    }

    @Test
    fun disablingGenericLeavesUnknownBanksUnrecognised() {
        SmsParser.config = SmsParserConfig(disabledBuiltIns = setOf(BuiltInSmsParser.GENERIC))
        val result = SmsParser.parseDetailed(
            "VM-HDFCBK",
            "Rs.1,250.00 debited from a/c **1234 on 12-08-25 to VPA chaipoint@ybl. Avl bal Rs.500"
        )
        assertEquals(SmsParseSource.NotRecognised, result.source)
    }

    @Test
    fun compileRejectsBadRules() {
        fun error(rule: SmsRule) = SmsRuleEngine.compile(rule) as? SmsRuleEngine.Compilation.Error
        assertNotNull(error(SmsRule(id = "1", name = "r", pattern = "Sent Rs.500")))           // no {amount}
        assertNotNull(error(SmsRule(id = "1", name = "r", pattern = "Rs.{amount} {amount}"))) // twice
        assertNotNull(error(SmsRule(id = "1", name = "r", pattern = "Rs.{amt}")))             // unknown
        assertNotNull(error(SmsRule(id = "1", name = "r", pattern = "(?<amount>[0-9", isRegex = true)))
        assertNull(error(SmsRule(id = "1", name = "r", pattern = "OTP", action = SmsRuleAction.IGNORE)))
    }

    @Test
    fun templateKeepsNameEndingInLiteralLetters() {
        // "AMAZON" ends in "ON": with optional spaces around the literal " on ", the lazy name used
        // to stop at "AMAZ" and take "ON" as the word that follows it.
        val rule = SmsRule(id = "1", name = "r", pattern = "Sent Rs.{amount} from Kotak Bank A/c {any} to {counterparty} on {any}")
        val parsed = SmsRuleEngine.apply(
            listOf(rule), "AD-KOTAKB-S",
            "Sent Rs.99.00 from Kotak Bank A/c X2769 to AMAZON on 22-09-26. UPI Ref 526512345678"
        )?.parsed
        assertEquals("AMAZON", parsed?.counterparty)
    }

    @Test
    fun templateToleratesStrayEdgeSpaces() {
        val rule = SmsRule(id = "1", name = "r", pattern = "  Sent Rs.{amount} from Kotak Bank  ")
        assertEquals("Rs.40.00", SmsRuleEngine.apply(listOf(rule), "X", kotakSent)?.parsed?.amount)
    }

    @Test
    fun runawayRegexDoesNotCrash() {
        // Alternation under a star recurses once per repetition in java.util.regex; on a long
        // enough input that overflows the stack. The rule must just fail to match.
        val rule = SmsRule(id = "1", name = "r", pattern = "(?:a|aa)*(?<amount>[0-9]+)", isRegex = true)
        assertNull(SmsRuleEngine.apply(listOf(rule), "X", "a".repeat(200_000)))
    }
}
