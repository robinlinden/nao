package ltd.evilcorp.nao

import android.content.Context
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.util.AtomicFile
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.atlassian.onetime.core.HMACDigest
import com.atlassian.onetime.core.OTPLength
import com.atlassian.onetime.core.TOTPGenerator
import com.atlassian.onetime.model.TOTPSecret
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ltd.evilcorp.nao.ui.theme.NaoTheme
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Date

private fun asHMACDigest(digest: Digest) =
    when (digest) {
        Digest.Sha1 -> HMACDigest.SHA1
        Digest.Sha256 -> HMACDigest.SHA256
        Digest.Sha512 -> HMACDigest.SHA512
    }

private fun asOTPLength(length: Int) =
    when (length) {
        6 -> OTPLength.SIX
        7 -> OTPLength.SEVEN
        8 -> OTPLength.EIGHT
        else -> OTPLength.SIX
    }

private fun isOtpAuthIntent(intent: Intent): Boolean = intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "otpauth"

private suspend fun saveItems(
    context: Context,
    items: List<TotpItem>,
) = withContext(Dispatchers.IO) {
    val array = JSONArray()
    items.forEach { array.put(it.toJson()) }

    val atomicFile = AtomicFile(File(context.filesDir, "nao.json"))
    var fos: FileOutputStream? = null
    try {
        fos = atomicFile.startWrite()
        fos.write(array.toString().toByteArray())
        atomicFile.finishWrite(fos)
    } catch (e: IOException) {
        Log.e("MainActivity", "Error saving items", e)
        atomicFile.failWrite(fos)
    }
}

private suspend fun loadItems(context: Context): List<TotpItem> =
    withContext(Dispatchers.IO) {
        val file = File(context.filesDir, "nao.json")
        if (!file.exists()) return@withContext emptyList()

        try {
            val atomicFile = AtomicFile(file)
            val jsonString = atomicFile.openRead().bufferedReader().use { it.readText() }
            val array = JSONArray(jsonString)
            val items = mutableListOf<TotpItem>()
            for (i in 0 until array.length()) {
                items.add(TotpItem.fromJson(array.getJSONObject(i)))
            }
            items
        } catch (e: Exception) {
            Log.e("MainActivity", "Error loading items", e)
            // If the file is corrupted, move it out of the way and let future Robin deal with it.
            val backupFile = File(context.filesDir, "nao-${Date().time}.json.old")
            file.renameTo(backupFile)
            emptyList()
        }
    }

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val totpArg = if (isOtpAuthIntent(intent)) {
            TotpItem.fromUrl(intent.data!!)
        } else {
            null
        }

        val initialItems = runBlocking {
            loadItems(this@MainActivity)
        }.also {
            Log.i("MainActivity", "Loaded ${it.size} items")
        }

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(TRANSPARENT, TRANSPARENT),
        )
        setContent {
            NaoTheme {
                var items by remember { mutableStateOf(initialItems) }
                var showAddSheet by remember { mutableStateOf(totpArg != null) }
                var itemToActions by remember { mutableStateOf<TotpItem?>(null) }
                var itemToEdit by remember { mutableStateOf<TotpItem?>(null) }
                var showMenu by remember { mutableStateOf(false) }

                val snackbarHostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()

                val exportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.CreateDocument("application/json"),
                ) { uri ->
                    if (uri == null) {
                        return@rememberLauncherForActivityResult
                    }

                    scope.launch(Dispatchers.IO) {
                        try {
                            val json = JSONArray().apply { items.forEach { put(it.toJson()) } }.toString(2)
                            contentResolver.openOutputStream(uri)?.use { os ->
                                os.write(json.toByteArray())
                            } ?: throw Exception("Failed to open output stream")

                            snackbarHostState.showSnackbar("Exported successfully")
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Export failed")
                            Log.e("MainActivity", "Export failed", e)
                        }
                    }
                }

                val importLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument(),
                ) { uri ->
                    if (uri == null) {
                        return@rememberLauncherForActivityResult
                    }

                    scope.launch(Dispatchers.IO) {
                        try {
                            val jsonString = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                                ?: throw Exception("Failed to open input stream")
                            val array = JSONArray(jsonString)
                            val newItems = mutableListOf<TotpItem>()
                            for (i in 0 until array.length()) {
                                newItems.add(TotpItem.fromJson(array.getJSONObject(i)))
                            }

                            withContext(Dispatchers.Main) {
                                val oldSize = items.size
                                items = (items + newItems).distinct()
                                val addedCount = items.size - oldSize
                                snackbarHostState.showSnackbar("Imported $addedCount new items")
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar("Import failed")
                            Log.e("MainActivity", "Import failed", e)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text("Nao") },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            ),
                            actions = {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vert),
                                        contentDescription = "More",
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Export JSON") },
                                        onClick = {
                                            showMenu = false
                                            exportLauncher.launch("nao.json")
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Import JSON") },
                                        onClick = {
                                            showMenu = false
                                            importLauncher.launch(arrayOf("application/json"))
                                        },
                                    )
                                }
                            },
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(onClick = { showAddSheet = true }) {
                            Icon(
                                painter = painterResource(android.R.drawable.ic_input_add),
                                contentDescription = "Add",
                            )
                        }
                    },
                ) { innerPadding ->
                    TotpList(
                        items = items,
                        onLongClick = { itemToActions = it },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    )

                    if (showAddSheet || itemToEdit != null) {
                        AddTotpSheet(
                            onDismiss = {
                                showAddSheet = false
                                itemToEdit = null
                            },
                            onSave = { newItem ->
                                items = if (itemToEdit != null) {
                                    items.map { if (it == itemToEdit) newItem else it }
                                } else {
                                    items + newItem
                                }
                                showAddSheet = false
                                itemToEdit = null
                            },
                            initialValues = itemToEdit ?: totpArg,
                            existingItems = items,
                            isEdit = itemToEdit != null,
                        )
                    }

                    if (itemToActions != null) {
                        TotpActionsSheet(
                            totp = itemToActions!!,
                            onDismiss = { itemToActions = null },
                            onEdit = {
                                itemToEdit = itemToActions
                                itemToActions = null
                            },
                            onDelete = {
                                items = items.filter { it != itemToActions }
                                itemToActions = null
                            },
                        )
                    }

                    LaunchedEffect(items) {
                        Log.i("MainActivity", "Saving ${items.size} items")
                        saveItems(this@MainActivity, items)
                    }
                }
            }
        }
    }
}

private const val DEFAULT_PERIOD = 30
private val DEFAULT_DIGEST = Digest.Sha1
private const val DEFAULT_OTP_LENGTH = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTotpSheet(
    onDismiss: () -> Unit,
    onSave: (TotpItem) -> Unit,
    initialValues: TotpItem?,
    existingItems: List<TotpItem>,
    isEdit: Boolean = false,
) {
    val initial = initialValues ?: TotpItem(
        name = "",
        extraInfo = "",
        secret = "",
        periodSeconds = DEFAULT_PERIOD,
        digest = DEFAULT_DIGEST,
        otpLength = DEFAULT_OTP_LENGTH,
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = initialValues != null)
    var name by remember { mutableStateOf(initial.name) }
    var extraInfo by remember { mutableStateOf(initial.extraInfo) }
    var secret by remember { mutableStateOf(initial.secret) }
    var period by remember { mutableStateOf(initial.periodSeconds.toString()) }
    var digest by remember { mutableStateOf(initial.digest) }
    var otpLength by remember { mutableIntStateOf(initial.otpLength) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var secretError by remember { mutableStateOf<String?>(null) }
    var periodError by remember { mutableStateOf<String?>(null) }

    var showAdvanced by remember {
        mutableStateOf(
            initial.periodSeconds != DEFAULT_PERIOD || initial.digest != DEFAULT_DIGEST || initial.otpLength != DEFAULT_OTP_LENGTH,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(if (isEdit) "Edit TOTP" else "Add New TOTP", style = MaterialTheme.typography.headlineSmall)
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
                    secretError = validateSecret(it, digest)
                },
                label = { Text("Secret (Base32)") },
                isError = secretError != null,
                supportingText = { secretError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!showAdvanced) {
                TextButton(onClick = { showAdvanced = true }) {
                    Text("Show advanced")
                }
            }

            if (showAdvanced) {
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

                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        value = digest.name.uppercase(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Algorithm") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        Digest.entries.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.name.uppercase()) },
                                onClick = {
                                    digest = selectionOption
                                    expanded = false
                                    secretError = validateSecret(secret, digest)
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }

                var lengthExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = lengthExpanded,
                    onExpandedChange = { lengthExpanded = !lengthExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        value = otpLength.toString(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("OTP Length") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = lengthExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = lengthExpanded,
                        onDismissRequest = { lengthExpanded = false },
                    ) {
                        listOf(6, 7, 8).forEach { selectionOption ->
                            DropdownMenuItem(
                                text = { Text(selectionOption.toString()) },
                                onClick = {
                                    otpLength = selectionOption
                                    lengthExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }

            val hasRequiredValues = name.isNotEmpty() && secret.isNotEmpty() && period.isNotEmpty()
            // TODO(robinlinden): Less strict comparison. Maybe something like:
            //   * <entry> already added w/ same secret
            //   * <entry> already added w/ different secret
            //   * secret already added as <entry>
            val isDuplicate = remember(name, extraInfo, secret, period, digest, otpLength) {
                val p = period.toIntOrNull() ?: return@remember false
                val candidate = TotpItem(name, extraInfo, secret, p, digest, otpLength)
                existingItems.any { it == candidate }
            }
            Button(
                onClick = {
                    onSave(TotpItem(name, extraInfo, secret, period.toInt(), digest, otpLength))
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = hasRequiredValues && nameError == null && secretError == null && periodError == null && !isDuplicate,
            ) {
                Text(if (isDuplicate) "Already added" else "Save")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TotpActionsSheet(
    totp: TotpItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showConfirmation by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            ListItem(
                headlineContent = { Text("Edit") },
                leadingContent = {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_edit),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.clickable {
                    onEdit()
                },
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
            ListItem(
                headlineContent = { Text("Delete") },
                leadingContent = {
                    Icon(
                        painter = painterResource(android.R.drawable.ic_menu_delete),
                        contentDescription = null,
                    )
                },
                modifier = Modifier.combinedClickable(
                    onClick = { showConfirmation = true },
                ),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    }

    if (showConfirmation) {
        AlertDialog(
            onDismissRequest = { showConfirmation = false },
            title = { Text("Delete TOTP") },
            text = { Text("Are you sure you want to delete ${totp.name}? This action cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmation = false
                    onDelete()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmation = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

private fun validateName(name: String): String? = if (name.isEmpty()) "Name may not be empty" else null

private fun isBase32Character(char: Char): Boolean = char in 'A'..'Z' || char in 'a'..'z' || char in '2'..'7' || char == '='

private fun validateSecret(
    secret: String,
    digest: Digest,
): String? {
    if (secret.any { !isBase32Character(it) }) return "Secret must be base32 encoded"
    if (secret.substringAfter('=', "").any { it != '=' }) return "Secret must be base32 encoded"

    // The RFC for this says that secrets MUST be at least 128 bits. That's sadly not
    // true in practice right now, so we allow secrets that are as weak as 80 bits.
    // https://datatracker.ietf.org/doc/html/rfc6238#section-3
    // https://datatracker.ietf.org/doc/html/rfc4226#section-4
    if (secret.length < 16) return "Secret must be at least 16 characters long"

    return try {
        val s = TOTPSecret.fromBase32EncodedString(secret)
        val generator = TOTPGenerator(digest = asHMACDigest(digest))
        generator.generateCurrent(s)
        null
    } catch (_: Exception) {
        // This should in theory be unreachable due to the prior checks, but 1time's
        // opinions about what's base32 and what isn't are interesting.
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
            delay(100)
        }
    }

    Card(
        modifier = modifier.combinedClickable(
            onClick = { },
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

private fun formatCode(code: String) =
    when (code.length) {
        6 -> "${code.take(3)} ${code.takeLast(3)}"
        7 -> "${code.take(4)} ${code.takeLast(3)}"
        8 -> "${code.take(4)} ${code.takeLast(4)}"
        else -> code
    }

@Composable
fun TotpList(
    items: List<TotpItem>,
    onLongClick: (TotpItem) -> Unit,
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
            TotpRow(
                totp = item,
                onLongClick = { onLongClick(item) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun GreetingPreview() {
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
        TotpItem(
            name = "Discord",
            extraInfo = "nao_fan_92",
            secret = "AAAAAAAACC",
            periodSeconds = 1,
            Digest.Sha512,
        ),
    )

    NaoTheme {
        TotpList(
            items = dummyEntries,
            onLongClick = {},
        )
    }
}
