package ltd.evilcorp.nao

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atlassian.onetime.core.HOTPGenerator
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import kotlinx.coroutines.delay
import ltd.evilcorp.nao.ui.theme.NaoTheme
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TotpRow(
    totp: TotpItem,
    onClick: (String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val generator =
        remember(totp.periodSeconds, totp.digest, totp.otpLength) {
            TOTPGenerator(
                timeStepSeconds = totp.periodSeconds,
                digest = asHMACDigest(totp.digest),
                otpLength = asOTPLength(totp.otpLength),
            )
        }
    var code by remember { mutableStateOf("000000") }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(totp.secret, generator) {
        val totpSecret = TOTPSecret.fromBase32EncodedString(totp.secret)
        while (true) {
            code = generator.generateCurrent(totpSecret).value

            val timeStepMs = totp.periodSeconds * 1000L
            val elapsedInStepMs = System.currentTimeMillis() % timeStepMs
            progress = 1f - (elapsedInStepMs.toFloat() / timeStepMs)

            // TODO(robinlinden): Scale this based on the period. 100ms looks fine for longer
            //  periods, but very silly for shorter ones.
            delay(100.milliseconds)
        }
    }

    Card(
        modifier = modifier.combinedClickable(
            onClick = { onClick(code) },
            onLongClick = onLongClick,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = totp.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = totp.extraInfo,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = formatCode(code),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
fun HotpRow(
    hotp: HotpItem,
    isRefreshed: Boolean,
    onClick: (String) -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val generator = remember(hotp.digest, hotp.otpLength) {
        HOTPGenerator(
            digest = asHMACDigest(hotp.digest),
            otpLength = asOTPLength(hotp.otpLength),
        )
    }
    val code = remember(hotp.counter, hotp.secret, generator) {
        val secretBytes = TOTPSecret.fromBase32EncodedString(hotp.secret).value
        generator.generate(secretBytes, hotp.counter).value
    }

    Card(
        modifier = modifier.combinedClickable(
            onClick = { onClick(code) },
            onLongClick = onLongClick,
        ),
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hotp.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = hotp.extraInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = formatCode(code),
                style = MaterialTheme.typography.headlineMedium,
                color = if (isRefreshed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = 0.5f,
                    )
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

fun formatCode(code: String) =
    when (code.length) {
        6 -> "${code.take(3)} ${code.takeLast(3)}"
        7 -> "${code.take(4)} ${code.takeLast(3)}"
        8 -> "${code.take(4)} ${code.takeLast(4)}"
        else -> code
    }

@Composable
fun OtpList(
    items: List<OtpItem>,
    refreshedInSession: Set<HotpItem>,
    onItemClick: (OtpItem, String) -> Unit,
    onLongClick: (OtpItem) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 16.dp + contentPadding.calculateStartPadding(layoutDirection),
            top = 16.dp + contentPadding.calculateTopPadding(),
            end = 16.dp + contentPadding.calculateEndPadding(layoutDirection),
            bottom = 80.dp + contentPadding.calculateBottomPadding(),
        ),
    ) {
        items(items) { item ->
            when (item) {
                is TotpItem -> {
                    TotpRow(
                        totp = item,
                        onClick = { code -> onItemClick(item, code) },
                        onLongClick = { onLongClick(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is HotpItem -> {
                    HotpRow(
                        hotp = item,
                        isRefreshed = refreshedInSession.contains(item),
                        onClick = { code -> onItemClick(item, code) },
                        onLongClick = { onLongClick(item) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun OtpListPreview() {
    val refreshedItem = HotpItem(
        name = "Lichess",
        extraInfo = "not_a_bot_i_swear",
        secret = "B2C3FFFF",
        counter = 3,
        Digest.Sha512,
    )

    val dummyEntries = listOf(
        TotpItem(
            name = "Google",
            extraInfo = "user@example.com",
            secret = "AAAAAAAAAA",
            periodSeconds = 3,
            Digest.Sha1,
        ),
        TotpItem(
            name = "GitHub",
            extraInfo = "ecorp-person",
            secret = "AAAAAAAABB",
            periodSeconds = 5,
            Digest.Sha256,
        ),
        HotpItem(
            name = "Discord",
            extraInfo = "nao_fan_92",
            secret = "AAAAAAAACC",
            counter = 3,
            Digest.Sha512,
        ),
        refreshedItem,
    )

    NaoTheme {
        OtpList(
            items = dummyEntries,
            refreshedInSession = setOf(refreshedItem),
            onItemClick = { _, _ -> },
            onLongClick = {},
        )
    }
}
