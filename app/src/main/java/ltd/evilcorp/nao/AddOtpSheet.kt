package ltd.evilcorp.nao

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddOtpSheet(
    onDismiss: () -> Unit,
    onSave: (OtpItem) -> Unit,
    initialValues: OtpItem?,
    existingItems: List<OtpItem>,
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

    var isHotp by remember { mutableStateOf(initial is HotpItem) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = initialValues != null)
    var name by remember { mutableStateOf(initial.name) }
    var extraInfo by remember { mutableStateOf(initial.extraInfo) }
    var secret by remember { mutableStateOf(initial.secret) }
    var period by remember { mutableStateOf(if (initial is TotpItem) initial.periodSeconds.toString() else DEFAULT_PERIOD.toString()) }
    var counter by remember { mutableStateOf(if (initial is HotpItem) initial.counter.toString() else "0") }
    var digest by remember { mutableStateOf(initial.digest) }
    var otpLength by remember { mutableIntStateOf(initial.otpLength) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var secretError by remember { mutableStateOf<String?>(null) }
    var periodError by remember { mutableStateOf<String?>(null) }
    var counterError by remember { mutableStateOf<String?>(null) }

    var showAdvanced by remember {
        val periodWasChanged = initial is TotpItem && initial.periodSeconds != DEFAULT_PERIOD
        val counterWasChanged = initial is HotpItem && initial.counter != 0L
        mutableStateOf(periodWasChanged || counterWasChanged || initial.digest != DEFAULT_DIGEST || initial.otpLength != DEFAULT_OTP_LENGTH)
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
            val nameEmptyError = stringResource(R.string.error_name_empty)
            val secretBase32Error = stringResource(R.string.error_secret_base32)
            val secretLengthError = stringResource(R.string.error_secret_length)
            val periodIntegerError = stringResource(R.string.error_period_integer)
            val periodRangeError = stringResource(R.string.error_period_range)
            val counterIntegerError = stringResource(R.string.error_counter_integer)

            Text(
                if (isEdit) {
                    if (isHotp) stringResource(R.string.edit_hotp) else stringResource(R.string.edit_totp)
                } else {
                    if (isHotp) stringResource(R.string.add_hotp) else stringResource(R.string.add_totp)
                },
                style = MaterialTheme.typography.headlineSmall,
            )

            if (!isEdit) {
                var typeExpanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextField(
                        value = if (isHotp) stringResource(R.string.type_hotp) else stringResource(R.string.type_totp),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.label_type)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors(),
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.type_totp)) },
                            onClick = {
                                isHotp = false
                                typeExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.type_hotp)) },
                            onClick = {
                                isHotp = true
                                typeExpanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                        )
                    }
                }
            }

            TextField(
                value = name,
                onValueChange = {
                    name = it
                    nameError = validateName(it, nameEmptyError)
                },
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it) } },
                label = { Text(stringResource(R.string.label_name)) },
                modifier = Modifier.fillMaxWidth(),
            )
            TextField(
                value = extraInfo,
                onValueChange = { extraInfo = it },
                label = { Text(stringResource(R.string.label_extra_info)) },
                modifier = Modifier.fillMaxWidth(),
                // Hack to give this TextField the same margins as the rest.
                supportingText = { },
            )
            TextField(
                value = secret,
                onValueChange = {
                    secret = it
                    secretError = validateSecret(it, digest, secretBase32Error, secretLengthError)
                },
                label = { Text(stringResource(R.string.label_secret)) },
                isError = secretError != null,
                supportingText = { secretError?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!showAdvanced) {
                TextButton(onClick = { showAdvanced = true }) {
                    Text(stringResource(R.string.show_advanced))
                }
            }

            if (isHotp && showAdvanced) {
                TextField(
                    value = counter,
                    onValueChange = {
                        counter = it
                        counterError = validateCounter(it, counterIntegerError)
                    },
                    label = { Text(stringResource(R.string.label_counter)) },
                    isError = counterError != null,
                    supportingText = { counterError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!isHotp && showAdvanced) {
                TextField(
                    value = period,
                    onValueChange = {
                        period = it
                        periodError = validatePeriod(it, periodIntegerError, periodRangeError)
                    },
                    label = { Text(stringResource(R.string.label_period)) },
                    isError = periodError != null,
                    supportingText = { periodError?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (showAdvanced) {
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
                        label = { Text(stringResource(R.string.label_algorithm)) },
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
                                    secretError = validateSecret(secret, digest, secretBase32Error, secretLengthError)
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
                        label = { Text(stringResource(R.string.label_otp_length)) },
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

            val hasRequiredValues = name.isNotEmpty() && secret.isNotEmpty() && (if (isHotp) counter.isNotEmpty() else period.isNotEmpty())
            // TODO(robinlinden): Less strict comparison. Maybe something like:
            //   * <entry> already added w/ same secret
            //   * <entry> already added w/ different secret
            //   * secret already added as <entry>
            val isDuplicate = remember(isHotp, name, extraInfo, secret, period, counter, digest, otpLength) {
                val candidate = if (isHotp) {
                    val c = counter.toLongOrNull() ?: return@remember false
                    HotpItem(name, extraInfo, secret, c, digest, otpLength)
                } else {
                    val p = period.toIntOrNull() ?: return@remember false
                    TotpItem(name, extraInfo, secret, p, digest, otpLength)
                }
                existingItems.any { it == candidate }
            }
            Button(
                onClick = {
                    val newItem = if (isHotp) {
                        HotpItem(name, extraInfo, secret, counter.toLong(), digest, otpLength)
                    } else {
                        TotpItem(name, extraInfo, secret, period.toInt(), digest, otpLength)
                    }
                    onSave(newItem)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled =
                    hasRequiredValues && nameError == null && secretError == null &&
                        (if (isHotp) counterError == null else periodError == null) &&
                        !isDuplicate,
            ) {
                Text(if (isDuplicate) stringResource(R.string.already_added) else stringResource(R.string.save))
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
