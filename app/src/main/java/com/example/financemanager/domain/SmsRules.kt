package com.example.financemanager.domain

/**
 * User-written SMS parsing rules, edited in Settings → SMS Rules.
 *
 * The built-in parser is compiled code, so changing it means a new build. These rules are data:
 * they run before the built-in parser, so a rule can add a bank the app doesn't know, fix a
 * template the built-in logic gets wrong, or drop a message the app should never have picked up —
 * all from the phone.
 */
data class SmsRule(
    val id: String,
    val name: String,
    /** Case-insensitive substring of the sender id (`KOTAKB`, `57575`), or blank for any sender. */
    val senderContains: String = "",
    /**
     * In template mode, literal text plus `{amount}`, `{balance}`, `{counterparty}`,
     * `{reference}`, `{account}` and `{any}` placeholders. In regex mode, a regular expression
     * whose named groups carry the same names.
     */
    val pattern: String,
    val isRegex: Boolean = false,
    val action: SmsRuleAction = SmsRuleAction.DEBIT,
    /** Account name to file the alert under. Ignored when the pattern captures `{account}`. */
    val accountName: String = "",
    val enabled: Boolean = true
)

enum class SmsRuleAction(val label: String) {
    DEBIT("Debit"),
    CREDIT("Credit"),
    /** Drop the message: never shown, never logged. For alerts the app misreads as transactions. */
    IGNORE("Ignore")
}

/** The compiled-in parsers, each of which can be switched off from Settings. */
enum class BuiltInSmsParser(val label: String, val description: String) {
    PROMO_FILTER("Promotional filter", "Skip offers, cashback and marketing messages"),
    TJSB("TJSB Bank", "UPI, NEFT, clearing and IMPS alerts"),
    SBI("SBI", "UPI, card and account alerts"),
    KOTAK("Kotak Bank", "UPI sent and received alerts"),
    AMAZON_PAY("Amazon Pay", "Amazon Pay balance and Juspay alerts"),
    GENERIC("Other banks (generic)", "Best-effort reading of any other bank or wallet alert")
}

data class SmsParserConfig(
    val customRules: List<SmsRule> = emptyList(),
    val disabledBuiltIns: Set<BuiltInSmsParser> = emptySet()
) {
    fun isEnabled(parser: BuiltInSmsParser) = parser !in disabledBuiltIns
}

/** What handled a message, for the test panel in SMS Rules. */
sealed class SmsParseSource {
    data class Custom(val rule: SmsRule) : SmsParseSource()
    data class BuiltIn(val parser: BuiltInSmsParser) : SmsParseSource()
    object PromoFiltered : SmsParseSource()
    object NotRecognised : SmsParseSource()
    /** Shaped like an alert, but from a shop, an app or a phone number rather than a bank. */
    object NotFromBank : SmsParseSource()
}

data class SmsParseResult(val parsed: ParsedSms?, val source: SmsParseSource)

object SmsRuleEngine {

    /** The fields a rule can capture. */
    val FIELDS = listOf("amount", "balance", "counterparty", "reference", "account")

    private const val AMOUNT = "([0-9][0-9,]*(?:\\.[0-9]{1,2})?)"
    private val placeholderRegex = Regex("\\{(\\w+|\\*)\\}")

    /** A rule turned into something that can be run: the regex, and which group holds which field. */
    class Compiled(val rule: SmsRule, val regex: Regex, val groupIndex: Map<String, Int>)

    sealed class Compilation {
        data class Ok(val compiled: Compiled) : Compilation()
        data class Error(val message: String) : Compilation()
    }

    fun compile(rule: SmsRule): Compilation {
        if (rule.pattern.isBlank()) return Compilation.Error("The pattern is empty")
        val (source, groups) = try {
            if (rule.isRegex) fromRegex(rule.pattern) else fromTemplate(rule.pattern)
        } catch (e: IllegalArgumentException) {
            return Compilation.Error(e.message ?: "Invalid pattern")
        }
        if (rule.action != SmsRuleAction.IGNORE && "amount" !in groups) {
            return Compilation.Error(
                if (rule.isRegex) "Add an (?<amount>…) group — a debit or credit needs an amount"
                else "Add {amount} — a debit or credit needs an amount"
            )
        }
        val regex = try {
            Regex(source, RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            return Compilation.Error("Invalid regex: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        return Compilation.Ok(Compiled(rule, regex, groups))
    }

    /**
     * Runs the enabled rules in order and returns the first match. A rule whose sender filter
     * doesn't match, or whose pattern doesn't compile, is skipped.
     */
    fun apply(rules: List<SmsRule>, sender: String, message: String): SmsParseResult? {
        for (rule in rules) {
            if (!rule.enabled) continue
            if (rule.senderContains.isNotBlank() &&
                !sender.contains(rule.senderContains.trim(), ignoreCase = true)
            ) continue
            val compiled = (compile(rule) as? Compilation.Ok)?.compiled ?: continue
            // A user's regex can backtrack deep enough to overflow the stack on a long message.
            // That is an Error, not an Exception, and it used to escape into the SMS receiver's
            // coroutine and take the whole process down. A rule that can't run simply doesn't match.
            val match = try {
                compiled.regex.find(message)
            } catch (e: StackOverflowError) {
                null
            } catch (e: RuntimeException) {
                null
            } ?: continue
            if (rule.action == SmsRuleAction.IGNORE) {
                return SmsParseResult(null, SmsParseSource.Custom(rule))
            }
            fun field(name: String): String =
                compiled.groupIndex[name]?.let { match.groups[it]?.value?.trim() }.orEmpty()

            val amount = field("amount").replace(",", "")
            if (amount.toDoubleOrNull() == null) continue
            val balance = field("balance").replace(",", "")
            val parsed = ParsedSms(
                accountName = field("account").ifEmpty { rule.accountName.trim() }.ifEmpty { sender },
                type = if (rule.action == SmsRuleAction.CREDIT) "credit" else "debit",
                amount = "Rs.$amount",
                balance = if (balance.toDoubleOrNull() != null) "Rs.$balance" else "",
                counterparty = field("counterparty").trimEnd('.', ','),
                reference = field("reference"),
                rawSender = sender,
                rawMessage = message
            )
            return SmsParseResult(parsed, SmsParseSource.Custom(rule))
        }
        return null
    }

    /**
     * Template → regex. Literal text matches case-insensitively with any run of whitespace
     * standing for any other, so a template copied from one message survives the next one's
     * spacing.
     */
    private fun fromTemplate(template: String): Pair<String, Map<String, Int>> {
        val out = StringBuilder()
        val groups = mutableMapOf<String, Int>()
        var last = 0
        val tokens = placeholderRegex.findAll(template).toList()
        tokens.forEachIndexed { i, token ->
            out.append(literal(template.substring(last, token.range.first), atStart = last == 0, atEnd = false))
            val name = token.groupValues[1].lowercase()
            val isLastToken = i == tokens.lastIndex && template.substring(token.range.last + 1).isBlank()
            when (name) {
                "any", "*" -> out.append("[\\s\\S]*?")
                in FIELDS -> {
                    require(name !in groups) { "{$name} is used twice" }
                    groups[name] = groups.size + 1
                    out.append(
                        when (name) {
                            "amount", "balance" -> AMOUNT
                            "reference" -> "([A-Za-z0-9]+)"
                            // Lazy text at the very end would stop after one character, so there
                            // it runs to the next sentence break instead.
                            else -> if (isLastToken) "(.+?)(?=[.,;]?\\s*$|[.,;]\\s)" else "(.+?)"
                        }
                    )
                }
                else -> throw IllegalArgumentException(
                    "Unknown placeholder {$name}. Use {amount}, {balance}, {counterparty}, {reference}, {account} or {any}"
                )
            }
            last = token.range.last + 1
        }
        out.append(literal(template.substring(last), atStart = last == 0, atEnd = true))
        return out.toString() to groups
    }

    /**
     * One run of literal template text. Whitespace inside it, or on an edge that touches a
     * placeholder, must match at least one space: `to {counterparty} on` has to find " on " as a
     * word, or the lazy name stops at "AMAZ" because "ON" alone satisfies an optional-space
     * pattern. Only at the template's very start or end is the edge left loose, so a template
     * copied with a stray leading or trailing space still matches.
     */
    private fun literal(text: String, atStart: Boolean, atEnd: Boolean): String {
        if (text.isEmpty()) return ""
        val body = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            .joinToString("\\s+") { Regex.escape(it) }
        if (body.isEmpty()) return if (atStart || atEnd) "\\s*" else "\\s+"
        val lead = when {
            !text.first().isWhitespace() -> ""
            atStart -> "\\s*"
            else -> "\\s+"
        }
        val trail = when {
            !text.last().isWhitespace() -> ""
            atEnd -> "\\s*"
            else -> "\\s+"
        }
        return lead + body + trail
    }

    /**
     * Rewrites `(?<name>…)` groups as plain ones, remembering which index each name landed on.
     * Named-group lookups need Android 8, and the app still runs on 7.
     */
    private fun fromRegex(pattern: String): Pair<String, Map<String, Int>> {
        val out = StringBuilder()
        val groups = mutableMapOf<String, Int>()
        var index = 0
        var i = 0
        var inClass = false
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> {
                    out.append(pattern, i, minOf(i + 2, pattern.length)); i += 2; continue
                }
                inClass -> if (c == ']') inClass = false
                c == '[' -> inClass = true
                c == '(' -> {
                    val rest = pattern.substring(i + 1)
                    if (!rest.startsWith("?")) {
                        index++
                    } else if (rest.startsWith("?<") && !rest.startsWith("?<=") && !rest.startsWith("?<!")) {
                        val close = rest.indexOf('>')
                        require(close > 2) { "Unclosed group name" }
                        val name = rest.substring(2, close).lowercase()
                        require(name in FIELDS) {
                            "Unknown group <$name>. Use ${FIELDS.joinToString { "<$it>" }}"
                        }
                        require(name !in groups) { "<$name> is used twice" }
                        index++
                        groups[name] = index
                        out.append('(')
                        i += close + 2
                        continue
                    }
                }
            }
            out.append(c)
            i++
        }
        return out.toString() to groups
    }
}
