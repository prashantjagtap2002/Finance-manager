package com.example.financemanager.ui.screens

import com.example.financemanager.ui.components.formatDate
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.financemanager.data.Account
import com.example.financemanager.data.Category
import com.example.financemanager.data.SmsTransaction
import com.example.financemanager.domain.SmsAmountFormatter
import com.example.financemanager.theme.*
import com.example.financemanager.ui.components.EmptyState
import com.example.financemanager.ui.components.IosButton
import com.example.financemanager.ui.components.IosButtonSize
import com.example.financemanager.ui.components.IosButtonVariant
import com.example.financemanager.ui.components.moneyString
import com.example.financemanager.ui.viewmodel.FinanceViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Pending bank alerts one card at a time: swipe right (or tap Log) to log it under the envelope
 * shown, swipe left (or Skip) to ignore it. The envelope and account come pre-picked from how this
 * merchant was filed before, so a repeat merchant is a single swipe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsReviewScreen(
    viewModel: FinanceViewModel,
    onNavigateBack: () -> Unit
) {
    val pending by viewModel.pendingSmsTransactions.collectAsStateWithLifecycle()
    val accounts by viewModel.accounts.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val approvalError by viewModel.smsApprovalError.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Ids already acted on here. The database flow catches up a moment later; without this the
    // card that was just swiped away would flash back in for a frame.
    var handled by remember { mutableStateOf(setOf<Long>()) }
    var reviewedCount by remember { mutableIntStateOf(0) }
    val queue = remember(pending, handled) { pending.filter { it.id !in handled } }
    val current = queue.firstOrNull()
    val total = reviewedCount + queue.size

    var categoryId by remember { mutableLongStateOf(0L) }
    var accountId by remember { mutableLongStateOf(0L) }

    // Re-prefill whenever a new card comes to the top.
    LaunchedEffect(current?.id, accounts, categories) {
        val tx = current ?: return@LaunchedEffect
        val learned = viewModel.learnedRuleFor(tx.counterparty)
        categoryId = learned?.categoryId?.takeIf { id -> categories.any { it.id == id } }
            ?: categories.firstOrNull()?.id ?: 0L
        accountId = learned?.accountId?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.find { tx.accountName.isNotBlank() && it.name.contains(tx.accountName, ignoreCase = true) }?.id
            ?: accounts.firstOrNull()?.id ?: 0L
    }

    LaunchedEffect(approvalError) {
        approvalError?.let {
            snackbarHostState.showSnackbar(it, withDismissAction = true)
            viewModel.clearSmsApprovalError()
        }
    }

    fun done(tx: SmsTransaction, message: String) {
        handled = handled + tx.id
        reviewedCount++
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(message, actionLabel = "Undo", duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.returnSmsToPending(tx.id)
                handled = handled - tx.id
                reviewedCount--
            }
        }
    }

    /**
     * Logs [tx] and returns whether it was taken. A card already acted on is refused: the swipe's
     * exit animation runs before its callback, so a tap on "Log it" mid-throw used to approve the
     * same alert twice from two stale copies — two ledger rows, the balance moved twice, and an
     * Undo that could only reach one of them.
     */
    fun approve(tx: SmsTransaction): Boolean {
        if (tx.id in handled) return false
        if (categoryId <= 0 || accountId <= 0) {
            scope.launch { snackbarHostState.showSnackbar("Pick an envelope and an account first", withDismissAction = true) }
            return false
        }
        // The ViewModel refuses an alert with no readable amount and says why. Calling done() here
        // anyway announced "Logged", dismissed that explanation and hid a card still pending.
        if ((SmsAmountFormatter.number(tx.amount).toDoubleOrNull() ?: 0.0) <= 0.0) {
            viewModel.approveSmsTransaction(tx, categoryId, accountId)
            return false
        }
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        viewModel.approveSmsTransaction(tx, categoryId, accountId)
        done(tx, "Logged")
        return true
    }

    fun skip(tx: SmsTransaction, message: String = "Skipped"): Boolean {
        if (tx.id in handled) return false
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        viewModel.ignoreSmsTransaction(tx)
        done(tx, message)
        return true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Review alerts", style = Typography.titleLarge.copy(color = TextPrimary))
                        if (current != null) {
                            Text(
                                "${reviewedCount + 1} of $total",
                                style = Typography.labelMedium.copy(color = TextSecondary)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBackground)
            )
        },
        containerColor = DeepBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else reviewedCount.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = SecondaryTeal,
                    trackColor = SubtleSurface
                )
            }

            if (current == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Default.DoneAll,
                        title = "All caught up",
                        message = if (reviewedCount > 0) "You reviewed $reviewedCount alert${if (reviewedCount == 1) "" else "s"}. New ones will show up here."
                                  else "No bank alerts are waiting for you.",
                        accent = AccentGreen,
                        actionLabel = "Done",
                        actionIcon = Icons.Default.Check,
                        onAction = onNavigateBack
                    )
                }
                return@Column
            }

            val existing = remember(current.id) { viewModel.existingMatchFor(current) }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                // The next card peeks out behind, so the stack reads as a stack.
                queue.getOrNull(1)?.let { next ->
                    ReviewCard(
                        tx = next,
                        modifier = Modifier.graphicsLayer {
                            scaleX = 0.94f
                            scaleY = 0.94f
                            translationY = 18.dp.toPx()
                            alpha = 0.6f
                        }
                    )
                }
                key(current.id) {
                    SwipeableReviewCard(
                        tx = current,
                        onSwipeRight = { approve(current) },
                        onSwipeLeft = { skip(current) }
                    )
                }
            }

            if (existing != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarningAmber.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(18.dp))
                    Text(
                        "Looks like you already logged this: \"${existing.note.ifBlank { "Transaction" }}\" at " +
                            formatDate(existing.date, "h:mm a"),
                        style = Typography.labelMedium.copy(color = TextPrimary),
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { skip(current, "Skipped — already logged") }) {
                        Text("Skip it", color = WarningAmber, fontWeight = FontWeight.SemiBold)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            PickerLabel("Envelope")
            ChipRow(categories, selectedId = categoryId, label = { it.name }, id = { it.id }) { categoryId = it }
            Spacer(modifier = Modifier.height(8.dp))
            PickerLabel("Account")
            ChipRow(accounts, selectedId = accountId, label = { it.name }, id = { it.id }) { accountId = it }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IosButton(
                    onClick = { skip(current) },
                    variant = IosButtonVariant.Outlined,
                    size = IosButtonSize.Large,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Skip")
                }
                IosButton(
                    onClick = { approve(current) },
                    size = IosButtonSize.Large,
                    accentColor = AccentGreen,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Log it")
                }
            }
        }
    }
}

private val exitSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
private val returnSpring = spring<Float>(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)

/**
 * Where a release at [velocity] px/s would come to rest, using the exponential deceleration iOS
 * applies to scroll views. 0.99 is the snappier of Apple's two rates: a card is not a page and
 * shouldn't coast as far.
 */
private fun projectMomentum(velocity: Float, decelerationRate: Float = 0.99f): Float =
    (velocity / 1000f) * decelerationRate / (1f - decelerationRate)

@Composable
private fun SwipeableReviewCard(
    tx: SmsTransaction,
    /** Returns whether the card was taken; false springs it back into place. */
    onSwipeRight: () -> Boolean,
    onSwipeLeft: () -> Boolean
) {
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var width by remember { mutableFloatStateOf(1f) }
    var pastThreshold by remember { mutableStateOf(false) }
    val logColor = AccentGreen
    val skipColor = AlertRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(tx.id) {
                val velocityTracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = { velocityTracker.resetTracking() },
                    onDragEnd = {
                        val threshold = width * 0.33f
                        val velocity = velocityTracker.calculateVelocity().x
                        // Decide on where the card is *going*, not where the finger let go: a short,
                        // fast flick commits just as a long slow drag does. The card then leaves at
                        // the finger's own speed, so there is no seam between dragging and flying.
                        val projected = offset.value + projectMomentum(velocity)
                        scope.launch {
                            when {
                                // A refused swipe (no envelope picked, no readable amount) brings
                                // the card back, or it would sit off-screen with nothing to tap.
                                projected > threshold -> {
                                    offset.animateTo(width * 1.5f, exitSpring, initialVelocity = velocity)
                                    if (!onSwipeRight()) offset.animateTo(0f, returnSpring)
                                }
                                projected < -threshold -> {
                                    offset.animateTo(-width * 1.5f, exitSpring, initialVelocity = velocity)
                                    if (!onSwipeLeft()) offset.animateTo(0f, returnSpring)
                                }
                                // Returning home after a throw is the one place a little bounce is
                                // earned: the momentum came from the user.
                                else -> offset.animateTo(0f, returnSpring, initialVelocity = velocity)
                            }
                        }
                    },
                    onDragCancel = { scope.launch { offset.animateTo(0f, returnSpring) } }
                ) { change, dragAmount ->
                    change.consume()
                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                    // snapTo cancels any spring still in flight, so a card can be caught mid-throw
                    // and dragged back from exactly where it is on screen. A catch also cancels
                    // the pending log/skip, since that only runs once the exit spring completes.
                    scope.launch { offset.snapTo(offset.value + dragAmount) }
                    val past = abs(offset.value) > width * 0.33f
                    if (past != pastThreshold) {
                        pastThreshold = past
                        if (past) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                }
            }
            // Offset, tilt and the verdict stamps are all read here, in the draw phase, so a drag
            // never recomposes the card.
            .graphicsLayer {
                translationX = offset.value
                rotationZ = offset.value / width * 8f
            }
    ) {
        ReviewCard(tx = tx)
        Stamp("LOG", logColor, Alignment.TopStart) { (offset.value / (width * 0.33f)).coerceIn(0f, 1f) }
        Stamp("SKIP", skipColor, Alignment.TopEnd) { (-offset.value / (width * 0.33f)).coerceIn(0f, 1f) }
    }
}

@Composable
private fun BoxScope.Stamp(text: String, color: Color, alignment: Alignment, alpha: () -> Float) {
    Text(
        text,
        style = Typography.titleMedium.copy(color = color, fontWeight = FontWeight.Black),
        modifier = Modifier
            .align(alignment)
            .padding(20.dp)
            .graphicsLayer { this.alpha = alpha() }
            .border(2.dp, color, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun ReviewCard(tx: SmsTransaction, modifier: Modifier = Modifier) {
    val isCredit = tx.type == "credit"
    val amount = SmsAmountFormatter.number(tx.amount).toDoubleOrNull()
    val accent = if (isCredit) AccentGreen else TextPrimary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(DarkSurface)
            .border(1.dp, BorderColor, RoundedCornerShape(24.dp))
            .padding(22.dp)
    ) {
        Text(
            if (isCredit) "MONEY IN" else "MONEY OUT",
            style = iOSCaption2.copy(color = if (isCredit) AccentGreen else TextMuted),
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(SubtleSurface)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            amount?.let { (if (isCredit) "+" else "−") + moneyString(it) } ?: tx.amount.ifBlank { "Amount unclear" },
            style = Typography.displaySmall.copy(color = accent, fontWeight = FontWeight.Bold),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            tx.counterparty.ifBlank { tx.sender },
            style = Typography.titleMedium.copy(color = TextPrimary),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            listOfNotNull(tx.accountName.ifBlank { null }, formatDate(tx.rawTimestamp, "d MMM, h:mm a"))
                .joinToString(" • "),
            style = Typography.labelMedium.copy(color = TextSecondary)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            tx.body,
            style = Typography.bodySmall.copy(color = TextMuted),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SubtleSurface)
                .padding(12.dp)
        )
    }
}

@Composable
private fun PickerLabel(text: String) {
    Text(text.uppercase(), style = iOSCaption2.copy(color = TextMuted), modifier = Modifier.padding(start = 2.dp, bottom = 6.dp))
}

@Composable
private fun <T> ChipRow(
    items: List<T>,
    selectedId: Long,
    label: (T) -> String,
    id: (T) -> Long,
    onSelect: (Long) -> Unit
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    // Bring the pre-picked chip into view, or a learned envelope far down the list stays hidden.
    LaunchedEffect(selectedId, items.size) {
        val index = items.indexOfFirst { id(it) == selectedId }
        if (index >= 0) state.animateScrollToItem(index)
    }
    LazyRow(state = state, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(items, key = { id(it) }) { item ->
            val selected = id(item) == selectedId
            Text(
                label(item),
                style = Typography.labelLarge.copy(
                    color = if (selected) DeepBackground else TextPrimary,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) SecondaryTeal else SubtleSurface)
                    .clickable { onSelect(id(item)) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
}
