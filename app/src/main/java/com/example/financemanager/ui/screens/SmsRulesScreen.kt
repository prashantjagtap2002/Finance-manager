package com.example.financemanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.financemanager.ui.components.IosTopAppBar
import com.example.financemanager.ui.components.rememberLargeTitleBehavior
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financemanager.core.SmsRuleStore
import com.example.financemanager.domain.BuiltInSmsParser
import com.example.financemanager.domain.SmsParseResult
import com.example.financemanager.domain.SmsParseSource
import com.example.financemanager.domain.SmsParser
import com.example.financemanager.domain.SmsRule
import com.example.financemanager.domain.SmsRuleAction
import com.example.financemanager.domain.SmsRuleEngine
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.IosButton
import com.example.financemanager.ui.components.IosButtonSize
import com.example.financemanager.ui.components.IosButtonVariant
import com.example.financemanager.ui.components.IosCard
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Settings → SMS Rules. The user's own parsing rules, a switch per built-in parser, and a test
 * panel that shows what the current setup makes of any message — so a bank changing its SMS
 * wording is something the user can fix on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsRulesScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val config by SmsRuleStore.config.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message, withDismissAction = true) }
    }

    var testSender by remember { mutableStateOf("") }
    var testMessage by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<SmsRule?>(null) }
    var showHelp by remember { mutableStateOf(false) }

    val testResult = remember(testSender, testMessage, config) {
        if (testMessage.isBlank()) null else SmsParser.parseDetailed(testSender.trim(), testMessage.trim(), config)
    }

    val titleBehavior = rememberLargeTitleBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(titleBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            IosTopAppBar(
                title = "SMS Rules",
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                actions = {
                    IconButton(onClick = { showHelp = !showHelp }) {
                        Icon(Icons.Default.HelpOutline, contentDescription = "How rules work", tint = SecondaryTeal)
                    }
                },
                scrollBehavior = titleBehavior
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            if (showHelp) {
                item { HelpCard() }
            }

            // --- Test panel ---
            item {
                IosCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Test a message", style = Typography.titleMedium.copy(color = TextPrimary))
                        Text(
                            "Paste a bank SMS to see what the app reads from it with your current rules.",
                            style = Typography.labelMedium.copy(color = TextSecondary)
                        )
                        OutlinedTextField(
                            value = testSender,
                            onValueChange = { testSender = it },
                            label = { Text("Sender (e.g. AD-KOTAKB-S)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ruleFieldColors()
                        )
                        OutlinedTextField(
                            value = testMessage,
                            onValueChange = { testMessage = it },
                            label = { Text("Message") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ruleFieldColors()
                        )
                        testResult?.let { ParseResultView(it) }
                        if (testMessage.isNotBlank()) {
                            IosButton(
                                onClick = {
                                    editing = SmsRule(
                                        id = UUID.randomUUID().toString(),
                                        name = "",
                                        senderContains = senderToken(testSender),
                                        pattern = testMessage.trim()
                                    )
                                },
                                variant = IosButtonVariant.Tinted,
                                accentColor = SecondaryTeal,
                                size = IosButtonSize.Small
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("New rule from this message")
                            }
                        }
                    }
                }
            }

            // --- Custom rules ---
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Your rules", style = Typography.titleMedium.copy(color = TextPrimary))
                        Text(
                            "Checked top to bottom before the built-in logic. The first match wins.",
                            style = Typography.labelMedium.copy(color = TextSecondary)
                        )
                    }
                    IosButton(
                        onClick = {
                            editing = SmsRule(id = UUID.randomUUID().toString(), name = "", pattern = "")
                        },
                        variant = IosButtonVariant.Accent,
                        accentColor = SecondaryTeal,
                        size = IosButtonSize.Small
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
            if (config.customRules.isEmpty()) {
                item {
                    IosCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "No rules yet. Paste a message above and tap \"New rule from this message\", " +
                                "then swap the parts that change for {amount}, {counterparty} and so on.",
                            style = Typography.bodyMedium.copy(color = TextSecondary),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
            items(config.customRules, key = { it.id }) { rule ->
                val index = config.customRules.indexOf(rule)
                RuleCard(
                    rule = rule,
                    canMoveUp = index > 0,
                    canMoveDown = index < config.customRules.lastIndex,
                    onClick = { editing = rule },
                    onToggle = { SmsRuleStore.saveRule(context, rule.copy(enabled = it)) },
                    onMove = { SmsRuleStore.moveRule(context, rule.id, it) }
                )
            }
            item {
                IosButton(
                    onClick = { viewModel.recheckPendingSms { notify(it) } },
                    variant = IosButtonVariant.Outlined,
                    accentColor = SecondaryTeal,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Re-check pending alerts with these rules")
                }
            }

            // --- Built-in parsers ---
            item {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text("Built-in logic", style = Typography.titleMedium.copy(color = TextPrimary))
                    Text(
                        "Switch one off to stop the app's own reading of that bank. Its alerts then " +
                            "fall through to your rules and the generic reader.",
                        style = Typography.labelMedium.copy(color = TextSecondary)
                    )
                }
            }
            item {
                IosCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        BuiltInSmsParser.entries.forEachIndexed { i, parser ->
                            if (i > 0) HorizontalDivider(color = BorderColor)
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(parser.label, style = Typography.bodyLarge.copy(color = TextPrimary))
                                    Text(parser.description, style = Typography.labelMedium.copy(color = TextSecondary))
                                }
                                Switch(
                                    checked = config.isEnabled(parser),
                                    onCheckedChange = { SmsRuleStore.setBuiltInEnabled(context, parser, it) },
                                    colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { rule ->
        RuleEditorDialog(
            initial = rule,
            isNew = config.customRules.none { it.id == rule.id },
            testSender = testSender,
            testMessage = testMessage,
            onDismiss = { editing = null },
            onSave = {
                SmsRuleStore.saveRule(context, it)
                editing = null
                notify("Rule saved. New SMS use it straight away.")
            },
            onDelete = {
                SmsRuleStore.deleteRule(context, rule.id)
                editing = null
                notify("Rule deleted")
            }
        )
    }
}

/** `AD-KOTAKB-S` → `KOTAKB`: the part of a DLT sender id that stays the same across operators. */
private fun senderToken(sender: String): String =
    sender.uppercase().split("-", ".", "_").filter { it.length > 2 }.maxByOrNull { it.length }.orEmpty()

@Composable
private fun HelpCard() {
    IosCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("How rules work", style = Typography.titleSmall.copy(color = TextPrimary))
            Text(
                "Write the message as it arrives, and put a placeholder wherever it changes. " +
                    "Spacing and capitals don't matter.",
                style = Typography.bodySmall.copy(color = TextSecondary)
            )
            listOf(
                "{amount}" to "the amount moved — required for debit and credit",
                "{balance}" to "the balance after it",
                "{counterparty}" to "who it was paid to or received from",
                "{reference}" to "the UPI / UTR reference",
                "{account}" to "the account name, if the message has one",
                "{any}" to "skip over anything (dates, card digits)"
            ).forEach { (token, meaning) ->
                Row {
                    Text(token, style = Typography.bodySmall.copy(color = SecondaryTeal, fontFamily = FontFamily.Monospace))
                    Text("  $meaning", style = Typography.bodySmall.copy(color = TextSecondary))
                }
            }
            Text(
                "Example:  Sent Rs.{amount} from Kotak Bank A/c {any} to {counterparty} on",
                style = Typography.bodySmall.copy(color = TextPrimary, fontFamily = FontFamily.Monospace)
            )
            Text(
                "Regex mode takes a regular expression with named groups instead, e.g. " +
                    "(?<amount>[0-9,.]+). Ignore rules drop matching messages entirely.",
                style = Typography.bodySmall.copy(color = TextSecondary)
            )
        }
    }
}

@Composable
private fun ParseResultView(result: SmsParseResult) {
    val (label, color) = when (val source = result.source) {
        is SmsParseSource.Custom ->
            (if (result.parsed == null) "Ignored by your rule \"${source.rule.name}\"" else "Your rule \"${source.rule.name}\"") to SecondaryTeal
        is SmsParseSource.BuiltIn -> "Built-in: ${source.parser.label}" to AccentGreen
        SmsParseSource.PromoFiltered -> "Skipped as promotional" to WarningAmber
        SmsParseSource.NotRecognised -> "Not recognised as a transaction" to AlertRed
        SmsParseSource.NotFromBank -> "Skipped: not from a bank or wallet" to WarningAmber
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeepBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(label, style = Typography.labelLarge.copy(color = color, fontWeight = FontWeight.SemiBold))
        result.parsed?.let { p ->
            FieldRow("Type", p.type)
            FieldRow("Amount", p.amount)
            FieldRow("Account", p.accountName)
            FieldRow("Counterparty", p.counterparty)
            FieldRow("Balance", p.balance)
            FieldRow("Reference", p.reference)
        }
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row {
        Text(label, style = Typography.bodySmall.copy(color = TextSecondary), modifier = Modifier.width(100.dp))
        Text(value.ifEmpty { "—" }, style = Typography.bodySmall.copy(color = TextPrimary))
    }
}

@Composable
private fun RuleCard(
    rule: SmsRule,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onMove: (Int) -> Unit
) {
    val error = (SmsRuleEngine.compile(rule) as? SmsRuleEngine.Compilation.Error)?.message
    IosCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        rule.name.ifBlank { "Untitled rule" },
                        style = Typography.bodyLarge.copy(color = TextPrimary, fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    ActionChip(rule.action)
                }
                Text(
                    rule.pattern,
                    style = Typography.bodySmall.copy(color = TextSecondary, fontFamily = FontFamily.Monospace),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (rule.senderContains.isNotBlank()) {
                    Text("Sender contains ${rule.senderContains}", style = Typography.labelSmall.copy(color = TextSecondary))
                }
                error?.let { Text(it, style = Typography.labelSmall.copy(color = AlertRed)) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Switch(
                    checked = rule.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(checkedTrackColor = AccentGreen)
                )
                Row {
                    IconButton(onClick = { onMove(-1) }, enabled = canMoveUp, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = if (canMoveUp) TextPrimary else BorderColor)
                    }
                    IconButton(onClick = { onMove(1) }, enabled = canMoveDown, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = if (canMoveDown) TextPrimary else BorderColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(action: SmsRuleAction) {
    val color = when (action) {
        SmsRuleAction.DEBIT -> AlertRed
        SmsRuleAction.CREDIT -> AccentGreen
        SmsRuleAction.IGNORE -> TextSecondary
    }
    Text(
        action.label,
        style = Typography.labelSmall.copy(color = color, fontWeight = FontWeight.SemiBold),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun RuleEditorDialog(
    initial: SmsRule,
    isNew: Boolean,
    testSender: String,
    testMessage: String,
    onDismiss: () -> Unit,
    onSave: (SmsRule) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var sender by remember { mutableStateOf(initial.senderContains) }
    var pattern by remember { mutableStateOf(initial.pattern) }
    var isRegex by remember { mutableStateOf(initial.isRegex) }
    var action by remember { mutableStateOf(initial.action) }
    var account by remember { mutableStateOf(initial.accountName) }
    var confirmDelete by remember { mutableStateOf(false) }

    val draft = initial.copy(
        name = name.trim(),
        senderContains = sender.trim(),
        pattern = pattern,
        isRegex = isRegex,
        action = action,
        accountName = account.trim(),
        enabled = true
    )
    val compilation = remember(draft) { SmsRuleEngine.compile(draft) }
    // Run the draft on its own against the test message, so the preview shows this rule's reading
    // rather than whichever rule or built-in parser happens to win.
    val preview = remember(draft, testSender, testMessage) {
        if (testMessage.isBlank() || compilation !is SmsRuleEngine.Compilation.Ok) null
        else SmsRuleEngine.apply(listOf(draft), testSender.trim(), testMessage.trim())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurface,
        title = { Text(if (isNew) "New SMS rule" else "Edit SMS rule", color = TextPrimary) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = ruleFieldColors()
                )
                Text("What it means", style = Typography.labelMedium.copy(color = TextSecondary))
                SegmentedChoice(SmsRuleAction.entries.map { it.label }, action.ordinal) {
                    action = SmsRuleAction.entries[it]
                }
                OutlinedTextField(
                    value = sender, onValueChange = { sender = it },
                    label = { Text("Sender contains (optional)") }, singleLine = true,
                    supportingText = { Text("e.g. KOTAKB. Blank matches every sender.") },
                    modifier = Modifier.fillMaxWidth(), colors = ruleFieldColors()
                )
                if (action != SmsRuleAction.IGNORE) {
                    OutlinedTextField(
                        value = account, onValueChange = { account = it },
                        label = { Text("Account name") }, singleLine = true,
                        supportingText = { Text("Used unless the pattern captures {account}") },
                        modifier = Modifier.fillMaxWidth(), colors = ruleFieldColors()
                    )
                }
                SegmentedChoice(listOf("Template", "Regex"), if (isRegex) 1 else 0) { isRegex = it == 1 }
                OutlinedTextField(
                    value = pattern, onValueChange = { pattern = it },
                    label = { Text(if (isRegex) "Regular expression" else "Message template") },
                    minLines = 3,
                    textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(), colors = ruleFieldColors()
                )
                when (compilation) {
                    is SmsRuleEngine.Compilation.Error ->
                        Text(compilation.message, style = Typography.labelMedium.copy(color = AlertRed))
                    is SmsRuleEngine.Compilation.Ok -> when {
                        testMessage.isBlank() ->
                            Text("Valid. Paste a message in the test panel to preview it.", style = Typography.labelMedium.copy(color = TextSecondary))
                        preview == null ->
                            Text("Valid, but doesn't match the test message.", style = Typography.labelMedium.copy(color = WarningAmber))
                        else -> ParseResultView(preview)
                    }
                }
                if (!isNew) {
                    TextButton(onClick = { confirmDelete = true }) {
                        Text("Delete rule", color = AlertRed)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft.copy(name = draft.name.ifBlank { "Rule" }, enabled = initial.enabled || isNew)) },
                enabled = compilation is SmsRuleEngine.Compilation.Ok
            ) { Text("Save", color = if (compilation is SmsRuleEngine.Compilation.Ok) SecondaryTeal else TextSecondary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = DarkSurface,
            title = { Text("Delete this rule?", color = TextPrimary) },
            text = { Text("Alerts it already read keep what it read. New ones go back to the built-in logic.", color = TextSecondary) },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete", color = AlertRed) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = TextSecondary) } }
        )
    }
}

@Composable
private fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(DeepBackground)
            .padding(3.dp)
    ) {
        options.forEachIndexed { i, label ->
            val isSelected = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) SecondaryTeal.copy(alpha = 0.2f) else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = Typography.labelLarge.copy(
                        color = if (isSelected) SecondaryTeal else TextSecondary,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                    )
                )
            }
        }
    }
}

@Composable
private fun ruleFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedContainerColor = DeepBackground,
    unfocusedContainerColor = DeepBackground,
    focusedBorderColor = SecondaryTeal,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = SecondaryTeal,
    unfocusedLabelColor = TextSecondary,
    cursorColor = SecondaryTeal
)
