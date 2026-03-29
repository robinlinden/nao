package ltd.evilcorp.nao

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import kotlinx.coroutines.delay
import ltd.evilcorp.nao.ui.theme.NaoTheme

private fun isOtpAuthIntent(intent: Intent): Boolean = intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "otpauth"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val totpArg = if (isOtpAuthIntent(intent)) {
            TotpItem.fromUrl(intent.data!!)
        } else {
            null
        }

        enableEdgeToEdge()
        setContent {
            NaoTheme {
                var items by remember { mutableStateOf(listOf<TotpItem>()) }
                var showSheet by remember { mutableStateOf(totpArg != null) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showSheet = true }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_input_add),
                                contentDescription = "Add",
                            )
                        }
                    },
                ) { innerPadding ->
                    TotpList(
                        items = items,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                    )

                    if (showSheet) {
                        AddTotpSheet(
                            onDismiss = { showSheet = false },
                            onSave = { newItem ->
                                items = items + newItem
                                showSheet = false
                            },
                            totpArg,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTotpSheet(
    onDismiss: () -> Unit,
    onSave: (TotpItem) -> Unit,
    initialValues: TotpItem?,
) {
    val initial = initialValues ?: TotpItem(
        name = "",
        extraInfo = "",
        secret = "",
        periodSeconds = 30,
    )

    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(initial.name) }
    var extraInfo by remember { mutableStateOf(initial.extraInfo) }
    var secret by remember { mutableStateOf(initial.secret) }
    var period by remember { mutableStateOf(initial.periodSeconds.toString()) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var secretError by remember { mutableStateOf<String?>(null) }
    var periodError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Add New TOTP", style = MaterialTheme.typography.headlineSmall)
            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = validateName(it)
                },
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = extraInfo,
                onValueChange = { extraInfo = it },
                label = { Text("Extra Info") },
                modifier = Modifier.fillMaxWidth(),
                // Hack to give this TextField the same margins as the rest.
                supportingText = { },
            )
            TextField(
                value = secret,
                onValueChange = {
                    secret = it
                    secretError = validateSecret(it)
                },
                label = { Text("Secret (Base32)") },
                isError = secretError != null,
                supportingText = { secretError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = period,
                onValueChange = {
                    period = it
                    periodError = validatePeriod(it)
                },
                label = { Text("Period (seconds)") },
                isError = periodError != null,
                supportingText = { periodError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            val hasRequiredValues = name.isNotEmpty() && secret.isNotEmpty() && period.isNotEmpty()
            Button(
                onClick = {
                    onSave(TotpItem(name, extraInfo, secret, period.toInt()))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasRequiredValues && nameError == null && secretError == null && periodError == null,
            ) {
                Text("Save")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

private fun validateName(name: String): String? = if (name.isEmpty()) "Name may not be empty" else null

private fun isBase32Character(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z' || char in '2'..'7' || char == '='

private fun validateSecret(secret: String): String? {
    if (secret.any { !isBase32Character(it) }) return "Secret must be base32 encoded"
    if (secret.substringAfter('=', "").any { it != '=' }) return "Secret must be base32 encoded"

    // https://datatracker.ietf.org/doc/html/rfc4226#section-4
    if (secret.length < 16) return "Secret must be at least 16 characters long"

    return try {
        val s = TOTPSecret.fromBase32EncodedString(secret)
        val generator = TOTPGenerator()
        generator.generateCurrent(s)
        null
    } catch (_: Exception) {
        "Secret must be base32 encoded"
    }
}

private fun validatePeriod(period: String): String? {
    val p = period.toIntOrNull() ?: return "Must be an integer"
    if (p !in 1..3600) return "Period must be between 1 and 3600 seconds"
    return null
}

@Composable
fun TotpRow(
    totp: TotpItem,
    modifier: Modifier = Modifier,
) {
    val generator = remember { TOTPGenerator(timeStepSeconds = totp.periodSeconds) }
    var code by remember { mutableStateOf("000000") }

    LaunchedEffect(totp.secret) {
        val totpSecret = TOTPSecret.fromBase32EncodedString(totp.secret)
        while (true) {
            code = generator.generateCurrent(totpSecret).value
            delay(1000)
        }
    }

    Card(
        modifier = modifier,
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
    }
}

private fun formatCode(code: String) =
    if (code.length == 6) {
        "${code.take(3)} ${code.takeLast(3)}"
    } else {
        code
    }

@Composable
fun TotpList(
    items: List<TotpItem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 16.dp,
            end = 16.dp,
            // This is silly, but we have to make sure the FAB doesn't overlap the last item in the list.
            bottom = 80.dp,
        ),
    ) {
        items(items) { item ->
            TotpRow(
                totp = item,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    val dummyEntries = listOf(
        TotpItem(
            name = "Google",
            extraInfo = "user@example.com",
            secret = "AAAAAAAAAA",
            periodSeconds = 3,
        ),
        TotpItem(
            name = "GitHub",
            extraInfo = "ecorp-person",
            secret = "AAAAAAAABB",
            periodSeconds = 5,
        ),
        TotpItem(
            name = "Discord",
            extraInfo = "nao_fan_92",
            secret = "AAAAAAAACC",
            periodSeconds = 1,
        ),
    )

    NaoTheme {
        TotpList(
            items = dummyEntries,
        )
    }
}
