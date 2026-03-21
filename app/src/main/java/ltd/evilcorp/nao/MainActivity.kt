package ltd.evilcorp.nao

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import kotlinx.coroutines.delay
import ltd.evilcorp.nao.ui.theme.NaoTheme

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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NaoTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TotpList(
                        items = dummyEntries,
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                    )
                }
            }
        }
    }
}

data class TotpItem(
    val name: String,
    val extraInfo: String,
    val secret: String,
    val periodSeconds: Int,
)

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

private fun formatCode(code: String) = if (code.length == 6) {
    "${code.take(3)} ${code.takeLast(3)}"
} else {
    code
}

@Composable
fun TotpList(items: List<TotpItem>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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
    NaoTheme {
        TotpList(
            items = dummyEntries,
        )
    }
}