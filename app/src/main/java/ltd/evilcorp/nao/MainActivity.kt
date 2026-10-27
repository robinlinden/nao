package ltd.evilcorp.nao

import android.content.ClipData
import android.content.Intent
import android.graphics.Color.TRANSPARENT
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import ltd.evilcorp.nao.ui.theme.NaoTheme
import org.json.JSONArray

private fun isOtpAuthIntent(intent: Intent): Boolean = intent.action == Intent.ACTION_VIEW && intent.data?.scheme == "otpauth"

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val otpArg = if (isOtpAuthIntent(intent)) {
            OtpItem.fromUrl(intent.data!!)
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
                var refreshedInSession by rememberSaveable(stateSaver = HotpItemSetSaver) { mutableStateOf(emptySet()) }
                var showAddSheet by remember { mutableStateOf(otpArg != null) }
                var itemToActions by remember { mutableStateOf<OtpItem?>(null) }
                var itemToEdit by remember { mutableStateOf<OtpItem?>(null) }
                var showMenu by remember { mutableStateOf(false) }

                val exportSuccessMessage = stringResource(R.string.export_success)
                val exportFailedMessage = stringResource(R.string.export_failed)
                val importSuccessPattern = stringResource(R.string.import_success)
                val importFailedMessage = stringResource(R.string.import_failed)
                val copiedCodePattern = stringResource(R.string.copied_code)

                val clipboard = LocalClipboard.current
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

                            snackbarHostState.showSnackbar(exportSuccessMessage)
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(exportFailedMessage)
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
                            val newItems = mutableListOf<OtpItem>()
                            for (i in 0 until array.length()) {
                                newItems.add(OtpItem.fromJson(array.getJSONObject(i)))
                            }

                            withContext(Dispatchers.Main) {
                                val oldSize = items.size
                                items = (items + newItems).distinct()
                                val addedCount = items.size - oldSize
                                snackbarHostState.showSnackbar(importSuccessPattern.format(addedCount))
                            }
                        } catch (e: Exception) {
                            snackbarHostState.showSnackbar(importFailedMessage)
                            Log.e("MainActivity", "Import failed", e)
                        }
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_name)) },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            ),
                            actions = {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_more_vert),
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false },
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.export_json)) },
                                        onClick = {
                                            showMenu = false
                                            exportLauncher.launch("nao.json")
                                        },
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.import_json)) },
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
                                painter = painterResource(R.drawable.ic_add),
                                contentDescription = stringResource(R.string.add_item),
                            )
                        }
                    },
                ) { innerPadding ->
                    OtpList(
                        items = items,
                        refreshedInSession = refreshedInSession,
                        onItemClick = { item, code ->
                            scope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, code)))
                                snackbarHostState.showSnackbar(copiedCodePattern.format(item.name))
                            }
                        },
                        onLongClick = { itemToActions = it },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = innerPadding,
                    )

                    if (showAddSheet || itemToEdit != null) {
                        AddOtpSheet(
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

                                if (newItem is HotpItem) {
                                    refreshedInSession = refreshedInSession + newItem
                                }
                            },
                            initialValues = itemToEdit ?: otpArg,
                            existingItems = items,
                            isEdit = itemToEdit != null,
                        )
                    }

                    if (itemToActions != null) {
                        OtpActionsSheet(
                            otp = itemToActions!!,
                            onDismiss = { itemToActions = null },
                            onEdit = {
                                itemToEdit = itemToActions
                                itemToActions = null
                            },
                            onDelete = {
                                items = items.filter { it != itemToActions }
                                itemToActions = null
                            },
                            onRefresh = {
                                val currentItem = itemToActions
                                if (currentItem is HotpItem) {
                                    val updated = currentItem.copy(counter = currentItem.counter + 1)
                                    items = items.map { if (it == currentItem) updated else it }
                                    refreshedInSession = refreshedInSession + updated
                                }
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
