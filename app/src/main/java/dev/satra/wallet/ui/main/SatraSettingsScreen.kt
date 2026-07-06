package dev.satra.wallet.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.satra.wallet.R
import dev.satra.wallet.data.assets.SupportedAssetCatalog
import dev.satra.wallet.data.db.AddressBookEntryRecord
import dev.satra.wallet.data.db.AppSettingsRecord
import dev.satra.wallet.data.db.AppSettingsUpdate
import dev.satra.wallet.data.db.DEFAULT_LOCAL_CURRENCY_CODE
import dev.satra.wallet.data.db.NewAddressBookEntryRecord
import dev.satra.wallet.data.db.SatraWalletRepository
import dev.satra.wallet.data.db.WalletBackupRecord
import dev.satra.wallet.data.db.WalletKeyType
import dev.satra.wallet.data.db.WalletPrivateKeyBackupRecord
import dev.satra.wallet.data.db.WalletRecord
import dev.satra.wallet.ui.components.RecoveryPhraseWordGrid
import dev.satra.wallet.ui.components.SatraButton
import dev.satra.wallet.ui.components.SatraButtonDefaults
import dev.satra.wallet.ui.components.SatraButtonVariant
import dev.satra.wallet.ui.components.SatraLtrContent
import dev.satra.wallet.ui.components.SatraPasscodeScreen
import dev.satra.wallet.ui.components.satraDoneKeyboardActions
import dev.satra.wallet.ui.components.satraDoneKeyboardOptions
import dev.satra.wallet.ui.components.satraLtr
import dev.satra.wallet.ui.components.satraSingleLineInput
import dev.satra.wallet.settings.SatraSettings
import dev.satra.wallet.settings.SatraSettingsDefaults
import dev.satra.wallet.settings.SatraThemePreference
import kotlinx.coroutines.launch
import java.util.Currency
import java.util.Locale

@Composable
internal fun SatraSettingsRootScreen(
    walletRepository: SatraWalletRepository,
    localCurrencyCode: String,
    onNavigate: (String) -> Unit,
) {
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository, localCurrencyCode) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(titleRes = R.string.settings_screen_title) {
        item { SettingsSectionTitle(R.string.settings_section_wallet_management) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_wallet,
                    title = stringResource(R.string.settings_wallet_management_title),
                    body = stringResource(R.string.settings_wallet_management_body),
                    onClick = { onNavigate(SatraMainRoute.WalletManagement) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_wallet) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_address_book,
                    title = stringResource(R.string.settings_address_book_title),
                    body = stringResource(R.string.settings_address_book_body),
                    onClick = { onNavigate(SatraMainRoute.AddressBook) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_app) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_settings,
                    title = stringResource(R.string.settings_preferences_title),
                    body = appSettings?.localCurrencyCode ?: stringResource(R.string.settings_preferences_body),
                    onClick = { onNavigate(SatraMainRoute.Preferences) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_security,
                    title = stringResource(R.string.settings_security_title),
                    body = if (appSettings?.passcodeEnabled == true) {
                        stringResource(R.string.settings_security_body_enabled)
                    } else {
                        stringResource(R.string.settings_security_body_disabled)
                    },
                    onClick = { onNavigate(SatraMainRoute.SecurityPasscodeGate) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_bell,
                    title = stringResource(R.string.settings_notifications_title),
                    body = stringResource(R.string.settings_notifications_body),
                    onClick = { onNavigate(SatraMainRoute.Notifications) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_info) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_info,
                    title = stringResource(R.string.settings_about_title),
                    body = stringResource(R.string.settings_about_body),
                    onClick = { onNavigate(SatraMainRoute.About) },
                )
            }
        }
        item { SettingsSectionTitle(R.string.settings_section_danger) }
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_danger_title),
                    body = stringResource(R.string.settings_danger_body),
                    onClick = { onNavigate(SatraMainRoute.DangerZone) },
                    isDanger = true,
                )
            }
        }
    }
}

@Composable
internal fun SatraWalletManagementScreen(
    walletRepository: SatraWalletRepository,
    refreshKey: Int,
    onBack: () -> Unit,
    onRemoveWallet: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wallets by remember { mutableStateOf<List<WalletRecord>?>(null) }
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var walletSearchQuery by rememberSaveable { mutableStateOf("") }
    var backupRecord by remember { mutableStateOf<WalletBackupRecord?>(null) }
    var backupMode by remember { mutableStateOf<WalletBackupMode?>(null) }
    var pendingBackupRequest by remember { mutableStateOf<WalletBackupRequest?>(null) }
    var backupPasscodeResetNonce by remember { mutableStateOf(0) }
    var backupPasscodeError by remember { mutableStateOf<String?>(null) }
    val loadedWallets = wallets.orEmpty()
    val visibleWallets = remember(loadedWallets, walletSearchQuery) {
        loadedWallets.filter { wallet -> wallet.matchesWalletSearch(walletSearchQuery) }
    }

    fun reloadWallets() {
        scope.launch {
            wallets = walletRepository.getWallets()
        }
    }

    fun loadBackup(
        walletId: String,
        mode: WalletBackupMode,
    ) {
        scope.launch {
            runCatching {
                walletRepository.getWalletBackup(walletId)
            }.onSuccess { backup ->
                if (backup == null) {
                    Toast.makeText(context, R.string.settings_wallet_management_wallet_missing, Toast.LENGTH_SHORT).show()
                } else {
                    backupRecord = backup
                    backupMode = mode
                }
            }.onFailure {
                Toast.makeText(context, R.string.settings_wallet_management_backup_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(walletRepository, refreshKey) {
        wallets = walletRepository.getWallets()
        appSettings = walletRepository.getAppSettings()
    }

    pendingBackupRequest?.let { request ->
        val titleRes = when (request.mode) {
            WalletBackupMode.RecoveryPhrase -> R.string.settings_wallet_management_export_phrase
            WalletBackupMode.PrivateKeys -> R.string.settings_wallet_management_backup_private_keys
        }
        SatraPasscodeScreen(
            passcodeLength = appSettings?.passcodeLength ?: 6,
            title = stringResource(titleRes),
            body = stringResource(R.string.settings_wallet_management_backup_verify_body),
            settings = appSettings.toPasscodeUiSettings(),
            resetNonce = backupPasscodeResetNonce,
            errorMessage = backupPasscodeError,
            biometricsEnabled = false,
            onPasscodeComplete = { passcode ->
                scope.launch {
                    val verified = walletRepository.verifyAppPasscode(passcode)
                    if (verified) {
                        pendingBackupRequest = null
                        backupPasscodeError = null
                        loadBackup(request.walletId, request.mode)
                    } else {
                        backupPasscodeError = context.getString(R.string.settings_security_wrong_passcode)
                        backupPasscodeResetNonce += 1
                    }
                }
            },
        )
        return
    }

    backupRecord?.let { backup ->
        val mode = backupMode
        if (mode != null) {
            WalletBackupSheet(
                backup = backup,
                mode = mode,
                onDismiss = {
                    backupRecord = null
                    backupMode = null
                },
            )
        }
    }

    SettingsScaffold(
        titleRes = R.string.settings_wallet_management_title,
        onBack = onBack,
    ) {
        when {
            wallets == null -> {
                item {
                    SettingsEmptyCard(
                        titleRes = R.string.settings_wallet_management_loading_title,
                        bodyRes = R.string.settings_wallet_management_loading_body,
                    )
                }
            }

            loadedWallets.isEmpty() -> {
                item {
                    SettingsEmptyCard(
                        titleRes = R.string.settings_wallet_management_empty_title,
                        bodyRes = R.string.settings_wallet_management_empty_body,
                    )
                }
            }

            else -> {
                item {
                    WalletManagementSearchField(
                        query = walletSearchQuery,
                        onQueryChange = { query -> walletSearchQuery = query },
                    )
                }
                if (visibleWallets.isEmpty()) {
                    item {
                        SettingsEmptyCard(
                            titleRes = R.string.settings_wallet_management_no_search_results_title,
                            bodyRes = R.string.settings_wallet_management_no_search_results_body,
                        )
                    }
                } else {
                    items(visibleWallets, key = { wallet -> wallet.walletId }) { wallet ->
                        SettingsCard {
                            WalletManagementCard(
                                wallet = wallet,
                                onSetActive = {
                                    scope.launch {
                                        walletRepository.setActiveWallet(wallet.walletId)
                                        reloadWallets()
                                        Toast.makeText(
                                            context,
                                            R.string.settings_wallet_management_active_toast,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onExportRecoveryPhrase = {
                                    if (appSettings?.passcodeEnabled == true) {
                                        backupPasscodeError = null
                                        backupPasscodeResetNonce = 0
                                        pendingBackupRequest = WalletBackupRequest(wallet.walletId, WalletBackupMode.RecoveryPhrase)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.settings_wallet_management_backup_no_passcode_body,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onBackupPrivateKeys = {
                                    if (appSettings?.passcodeEnabled == true) {
                                        backupPasscodeError = null
                                        backupPasscodeResetNonce = 0
                                        pendingBackupRequest = WalletBackupRequest(wallet.walletId, WalletBackupMode.PrivateKeys)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            R.string.settings_wallet_management_backup_no_passcode_body,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                },
                                onRemoveWallet = {
                                    onRemoveWallet(wallet.walletId)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WalletManagementSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    @StringRes placeholderRes: Int = R.string.settings_wallet_management_search_placeholder,
    @StringRes clearContentDescriptionRes: Int = R.string.settings_wallet_management_search_clear,
) {
    val keyboardActions = satraDoneKeyboardActions()
    TextField(
        value = query,
        onValueChange = { onQueryChange(it.satraSingleLineInput()) },
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth)
            .height(56.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        ),
        placeholder = {
            Text(
                text = stringResource(placeholderRes),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(clearContentDescriptionRes),
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        },
        singleLine = true,
        keyboardOptions = satraDoneKeyboardOptions(),
        keyboardActions = keyboardActions,
        shape = RoundedCornerShape(100.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

@Composable
private fun WalletManagementCard(
    wallet: WalletRecord,
    onSetActive: () -> Unit,
    onExportRecoveryPhrase: () -> Unit,
    onBackupPrivateKeys: () -> Unit,
    onRemoveWallet: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(iconRes = if (wallet.isWatchOnly) R.drawable.ic_brand_scan else R.drawable.ic_brand_wallet)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = wallet.walletName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = walletManagementSubtitle(wallet),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        WalletManagementActionsMenu(
            wallet = wallet,
            onSetActive = onSetActive,
            onExportRecoveryPhrase = onExportRecoveryPhrase,
            onBackupPrivateKeys = onBackupPrivateKeys,
            onRemoveWallet = onRemoveWallet,
        )
    }
}

@Composable
private fun WalletManagementActionsMenu(
    wallet: WalletRecord,
    onSetActive: () -> Unit,
    onExportRecoveryPhrase: () -> Unit,
    onBackupPrivateKeys: () -> Unit,
    onRemoveWallet: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Card(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            IconButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxSize(),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.settings_wallet_management_actions_content_description),
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (!wallet.isActive) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_wallet_management_make_active)) },
                    onClick = {
                        expanded = false
                        onSetActive()
                    },
                )
            }
            if (wallet.walletKeyType == WalletKeyType.Mnemonic.value) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_wallet_management_export_phrase)) },
                    onClick = {
                        expanded = false
                        onExportRecoveryPhrase()
                    },
                )
            }
            if (!wallet.isWatchOnly) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.settings_wallet_management_backup_private_keys)) },
                    onClick = {
                        expanded = false
                        onBackupPrivateKeys()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(R.string.settings_wallet_management_remove_wallet),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onRemoveWallet()
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletBackupSheet(
    backup: WalletBackupRecord,
    mode: WalletBackupMode,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var privateKeySearchQuery by rememberSaveable(backup.wallet.walletId, mode.name) { mutableStateOf("") }
    val visiblePrivateKeys = remember(backup.privateKeys, privateKeySearchQuery) {
        backup.privateKeys.filter { privateKey ->
            privateKey.matchesPrivateKeyBackupSearch(privateKeySearchQuery)
        }
    }
    val titleRes = when (mode) {
        WalletBackupMode.RecoveryPhrase -> R.string.settings_wallet_management_export_phrase
        WalletBackupMode.PrivateKeys -> R.string.settings_wallet_management_backup_private_keys
    }
    val recoveryPhraseLabel = stringResource(R.string.settings_wallet_management_recovery_phrase_label)
    val passphraseLabel = stringResource(R.string.settings_wallet_management_passphrase_label)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(titleRes),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_wallet_management_backup_warning),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            when (mode) {
                WalletBackupMode.RecoveryPhrase -> {
                    val recoveryPhrase = backup.recoveryPhrase
                    if (recoveryPhrase == null) {
                        Text(
                            text = stringResource(R.string.settings_wallet_management_no_phrase),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        RecoveryPhraseBackupCard(
                            title = recoveryPhraseLabel,
                            body = stringResource(R.string.settings_wallet_management_recovery_phrase_body),
                            recoveryPhrase = recoveryPhrase,
                            onCopy = {
                                copySecretToClipboard(
                                    context = context,
                                    label = recoveryPhraseLabel,
                                    value = recoveryPhrase,
                                )
                            },
                        )
                        backup.passphrase?.takeIf(String::isNotBlank)?.let { passphrase ->
                            SecretValueCard(
                                title = passphraseLabel,
                                body = stringResource(R.string.settings_wallet_management_passphrase_body),
                                secret = passphrase,
                                onCopy = {
                                    copySecretToClipboard(
                                        context = context,
                                        label = passphraseLabel,
                                        value = passphrase,
                                    )
                                },
                            )
                        }
                    }
                }

                WalletBackupMode.PrivateKeys -> {
                    if (backup.privateKeys.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_wallet_management_no_private_keys),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        WalletManagementSearchField(
                            query = privateKeySearchQuery,
                            onQueryChange = { privateKeySearchQuery = it },
                            placeholderRes = R.string.settings_wallet_management_search_private_keys,
                        )
                        if (visiblePrivateKeys.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_wallet_management_no_private_key_matches),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        visiblePrivateKeys.forEach { privateKey ->
                            PrivateKeyBackupCard(
                                privateKey = privateKey,
                                onCopy = {
                                    copySecretToClipboard(
                                        context = context,
                                        label = privateKey.networkName,
                                        value = privateKey.backupValue,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun RecoveryPhraseBackupCard(
    title: String,
    body: String,
    recoveryPhrase: String,
    onCopy: () -> Unit,
) {
    val words = remember(recoveryPhrase) {
        recoveryPhrase.split(Regex("\\s+")).filter(String::isNotBlank)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
            )
            RecoveryPhraseWordGrid(words = words)
            SatraButton(
                text = stringResource(R.string.settings_wallet_management_copy_secret),
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                variant = SatraButtonVariant.Secondary,
                height = SatraButtonDefaults.CompactHeight,
            )
        }
    }
}

@Composable
private fun SecretValueCard(
    title: String,
    body: String,
    secret: String,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SatraLtrContent {
                Text(
                    text = secret,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.satraLtr(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            SatraButton(
                text = stringResource(R.string.settings_wallet_management_copy_secret),
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                variant = SatraButtonVariant.Secondary,
                height = SatraButtonDefaults.CompactHeight,
            )
        }
    }
}

@Composable
private fun PrivateKeyBackupCard(
    privateKey: WalletPrivateKeyBackupRecord,
    onCopy: () -> Unit,
) {
    val details = listOfNotNull(
        privateKey.backupFormat.takeIf(String::isNotBlank),
        privateKey.address?.shortSettingsValue(),
        privateKey.derivationPath,
    ).joinToString(" · ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SatraCryptoIcon(
                    iconRes = networkIconRes(privateKey.networkId),
                    modifier = Modifier.size(46.dp),
                    contentDescription = privateKey.networkName,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = privateKey.networkName,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    SatraLtrContent {
                        Text(
                            text = details.ifBlank { privateKey.keySource },
                            modifier = Modifier.fillMaxWidth(),
                            style = MaterialTheme.typography.bodyMedium.satraLtr(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            SatraLtrContent {
                Text(
                    text = privateKey.backupValue,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge.satraLtr(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            SatraButton(
                text = stringResource(R.string.settings_wallet_management_copy_secret),
                onClick = onCopy,
                modifier = Modifier.fillMaxWidth(),
                variant = SatraButtonVariant.Secondary,
                height = SatraButtonDefaults.CompactHeight,
            )
        }
    }
}

@Composable
internal fun SatraWalletRemoveWarningScreen(
    walletRepository: SatraWalletRepository,
    walletId: String,
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
) {
    var wallet by remember { mutableStateOf<WalletRecord?>(null) }
    var confirmation by remember { mutableStateOf("") }
    val keyboardActions = satraDoneKeyboardActions()
    LaunchedEffect(walletRepository, walletId) {
        wallet = walletRepository.getWallets().firstOrNull { it.walletId == walletId }
    }

    SettingsScaffold(
        titleRes = R.string.settings_wallet_management_remove_wallet,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                Text(
                    text = stringResource(
                        R.string.settings_wallet_management_remove_warning,
                        wallet?.walletName ?: stringResource(R.string.settings_wallet_management_wallet_missing_name),
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedTextField(
                    value = confirmation,
                    onValueChange = {
                        confirmation = it.satraSingleLineInput(newlineReplacement = "")
                            .uppercase(Locale.US)
                            .take(12)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_wallet_management_remove_confirm_label)) },
                    singleLine = true,
                    keyboardOptions = satraDoneKeyboardOptions(),
                    keyboardActions = keyboardActions,
                )
                SatraButton(
                    text = stringResource(R.string.settings_wallet_management_continue_to_passcode),
                    onClick = { onContinue(walletId) },
                    enabled = wallet != null && confirmation == REMOVE_CONFIRMATION,
                    modifier = Modifier
                        .fillMaxWidth(),
                    variant = SatraButtonVariant.Danger,
                )
            }
        }
    }
}

@Composable
internal fun SatraWalletRemovePasscodeScreen(
    walletRepository: SatraWalletRepository,
    walletId: String,
    onBack: () -> Unit,
    onWalletRemoved: () -> Unit,
    onWalletsEmpty: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var wallet by remember { mutableStateOf<WalletRecord?>(null) }
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var removing by remember { mutableStateOf(false) }
    var resetNonce by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(walletRepository, walletId) {
        wallet = walletRepository.getWallets().firstOrNull { it.walletId == walletId }
        appSettings = walletRepository.getAppSettings()
    }

    SatraPasscodeScreen(
        passcodeLength = appSettings?.passcodeLength ?: 6,
        title = stringResource(R.string.settings_wallet_management_verify_passcode),
        body = stringResource(R.string.settings_wallet_management_verify_passcode_body),
        settings = appSettings.toPasscodeUiSettings(),
        resetNonce = resetNonce,
        errorMessage = errorMessage,
        biometricsEnabled = false,
        onPasscodeComplete = { passcode ->
            if (removing || wallet == null) return@SatraPasscodeScreen
            scope.launch {
                removing = true
                val verified = walletRepository.verifyAppPasscode(passcode)
                if (verified) {
                    val remainingWallets = walletRepository.removeWallet(walletId)
                    Toast.makeText(
                        context,
                        R.string.settings_wallet_management_removed_toast,
                        Toast.LENGTH_SHORT,
                    ).show()
                    if (remainingWallets.isEmpty()) {
                        onWalletsEmpty()
                    } else {
                        onWalletRemoved()
                    }
                } else {
                    removing = false
                    errorMessage = context.getString(R.string.settings_security_wrong_passcode)
                    resetNonce += 1
                }
            }
        },
    )
}

@Composable
internal fun SatraAddressBookScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf(emptyList<AddressBookEntryRecord>()) }
    var editingEntry by remember { mutableStateOf<AddressBookEntryRecord?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    fun reload() {
        scope.launch { entries = walletRepository.getAddressBookEntries() }
    }

    LaunchedEffect(walletRepository) {
        entries = walletRepository.getAddressBookEntries()
    }

    SettingsScaffold(
        titleRes = R.string.settings_address_book_title,
        onBack = onBack,
    ) {
        item {
            SatraButton(
                text = stringResource(R.string.settings_address_book_add),
                onClick = {
                    editingEntry = null
                    showEditor = true
                },
                modifier = Modifier
                    .fillMaxWidth(),
                height = SatraButtonDefaults.CompactHeight,
            )
        }
        if (entries.isEmpty()) {
            item {
                SettingsEmptyCard(
                    titleRes = R.string.settings_address_book_empty_title,
                    bodyRes = R.string.settings_address_book_empty_body,
                )
            }
        } else {
            items(entries, key = { it.entryId }) { entry ->
                SettingsCard {
                    AddressBookEntryRow(
                        entry = entry,
                        onEdit = {
                            editingEntry = entry
                            showEditor = true
                        },
                        onDelete = {
                            scope.launch {
                                walletRepository.deleteAddressBookEntry(entry.entryId)
                                reload()
                            }
                        },
                    )
                }
            }
        }
    }

    if (showEditor) {
        AddressBookEditorSheet(
            entry = editingEntry,
            onDismiss = { showEditor = false },
            onSave = { newEntry ->
                scope.launch {
                    walletRepository.upsertAddressBookEntry(newEntry, editingEntry?.entryId)
                    showEditor = false
                    reload()
                }
            },
        )
    }
}

@Composable
internal fun SatraPreferencesScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    localCurrencyCode: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onHapticsEnabledChange: (Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository, localCurrencyCode) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(
        titleRes = R.string.settings_preferences_title,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                SettingsRow(
                    iconRes = R.drawable.ic_brand_currency,
                    title = stringResource(R.string.settings_currency_title),
                    body = appSettings?.localCurrencyCode ?: DEFAULT_LOCAL_CURRENCY_CODE,
                    onClick = { onNavigate(SatraMainRoute.Currency) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_language,
                    title = stringResource(R.string.settings_language_title),
                    body = supportedSettingLanguages
                        .firstOrNull { it.tag == settings.languageTag }
                        ?.let { language -> stringResource(language.labelRes) }
                        ?: stringResource(R.string.settings_language_english),
                    onClick = { onNavigate(SatraMainRoute.Language) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_appearance,
                    title = stringResource(R.string.settings_appearance_title),
                    body = stringResource(settings.themePreference.titleRes),
                    onClick = { onNavigate(SatraMainRoute.Appearance) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_haptics,
                    title = stringResource(R.string.settings_haptics_title),
                    body = stringResource(R.string.settings_haptics_body),
                    checked = settings.hapticsEnabled,
                    onCheckedChange = { enabled ->
                        onHapticsEnabledChange(enabled)
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(hapticsEnabled = enabled),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SatraCurrencyScreen(
    walletRepository: SatraWalletRepository,
    selectedCurrencyCode: String,
    onCurrencyChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var selectedCode by remember(selectedCurrencyCode) {
        mutableStateOf(selectedCurrencyCode.ifBlank { DEFAULT_LOCAL_CURRENCY_CODE })
    }
    var currencySearchQuery by rememberSaveable { mutableStateOf("") }
    val activeOptions = remember { activeCurrencyOptions() }
    val localizedOptions = activeOptions.map { option ->
        CurrencyOptionLabel(option, stringResource(option.nameRes))
    }
    val visibleOptions = remember(localizedOptions, currencySearchQuery) {
        localizedOptions.filter { option -> option.matches(currencySearchQuery) }
    }

    SettingsScaffold(
        titleRes = R.string.settings_currency_title,
        onBack = onBack,
    ) {
        item {
            WalletManagementSearchField(
                query = currencySearchQuery,
                onQueryChange = { query -> currencySearchQuery = query },
                placeholderRes = R.string.settings_currency_search_placeholder,
                clearContentDescriptionRes = R.string.settings_currency_search_clear,
            )
        }
        if (visibleOptions.isEmpty()) {
            item {
                SettingsEmptyCard(
                    titleRes = R.string.settings_currency_no_search_results_title,
                    bodyRes = R.string.settings_currency_no_search_results_body,
                )
            }
        } else {
            item {
                SettingsListCard {
                    visibleOptions.forEachIndexed { index, currencyLabel ->
                        val currency = currencyLabel.option
                        SelectableSettingsRow(
                            title = stringResource(
                                R.string.settings_currency_option_title,
                                currency.code,
                                currencyLabel.name,
                            ),
                            body = currency.symbol,
                            selected = selectedCode == currency.code,
                            leading = { SettingsFlagGlyph(currency.flag) },
                            onClick = {
                                selectedCode = currency.code
                                scope.launch {
                                    val updatedSettings = walletRepository.changeLocalCurrency(currency.code)
                                    selectedCode = updatedSettings.localCurrencyCode
                                    onCurrencyChanged(updatedSettings.localCurrencyCode)
                                }
                            },
                        )
                        if (index != visibleOptions.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraLanguageScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    onBack: () -> Unit,
    onLanguageTagChange: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var languageSearchQuery by rememberSaveable { mutableStateOf("") }
    val localizedLanguages = supportedSettingLanguages.map { language ->
        SettingsLanguageLabel(
            language = language,
            label = stringResource(language.labelRes),
            country = stringResource(language.countryRes),
        )
    }
    val visibleLanguages = remember(localizedLanguages, languageSearchQuery) {
        localizedLanguages.filter { language -> language.matches(languageSearchQuery) }
    }

    SettingsScaffold(
        titleRes = R.string.settings_language_title,
        onBack = onBack,
    ) {
        item {
            WalletManagementSearchField(
                query = languageSearchQuery,
                onQueryChange = { query -> languageSearchQuery = query },
                placeholderRes = R.string.settings_language_search_placeholder,
                clearContentDescriptionRes = R.string.settings_language_search_clear,
            )
        }
        if (visibleLanguages.isEmpty()) {
            item {
                SettingsEmptyCard(
                    titleRes = R.string.settings_language_no_search_results_title,
                    bodyRes = R.string.settings_language_no_search_results_body,
                )
            }
        } else {
            item {
                SettingsListCard {
                    visibleLanguages.forEachIndexed { index, languageLabel ->
                        val language = languageLabel.language
                        SelectableSettingsRow(
                            title = languageLabel.label,
                            body = languageLabel.country,
                            selected = settings.languageTag == language.tag,
                            leading = { SettingsFlagGlyph(language.flag) },
                            onClick = {
                                onLanguageTagChange(language.tag)
                                scope.launch {
                                    walletRepository.updateAppSettings(
                                        AppSettingsUpdate(languageTag = language.tag),
                                    )
                                }
                            },
                        )
                        if (index != visibleLanguages.lastIndex) {
                            SettingsDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraAppearanceScreen(
    walletRepository: SatraWalletRepository,
    settings: SatraSettings,
    onBack: () -> Unit,
    onThemePreferenceChange: (SatraThemePreference) -> Unit,
) {
    val scope = rememberCoroutineScope()
    SettingsScaffold(
        titleRes = R.string.settings_appearance_title,
        onBack = onBack,
    ) {
        item {
            SettingsListCard {
                SatraThemePreference.entries.forEachIndexed { index, preference ->
                    SelectableSettingsRow(
                        title = stringResource(preference.titleRes),
                        body = stringResource(preference.bodyRes),
                        selected = settings.themePreference == preference,
                        onClick = {
                            onThemePreferenceChange(preference)
                            scope.launch {
                                walletRepository.updateAppSettings(
                                    AppSettingsUpdate(themePreference = preference.name),
                                )
                            }
                        },
                    )
                    if (index != SatraThemePreference.entries.lastIndex) {
                        SettingsDivider()
                    }
                }
            }
        }
    }
}

@Composable
internal fun SatraSecurityPasscodeGateScreen(
    walletRepository: SatraWalletRepository,
    onVerified: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var verifying by remember { mutableStateOf(false) }
    var resetNonce by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    val settings = appSettings
    LaunchedEffect(settings) {
        if (settings != null && !settings.hasUsablePasscode()) {
            onVerified()
        }
    }

    if (settings == null || !settings.hasUsablePasscode()) {
        return
    }

    SatraPasscodeScreen(
        passcodeLength = settings.passcodeLength ?: 6,
        title = stringResource(R.string.settings_security_title),
        body = stringResource(R.string.app_lock_body),
        settings = settings.toPasscodeUiSettings(),
        resetNonce = resetNonce,
        errorMessage = errorMessage,
        biometricsEnabled = false,
        onPasscodeComplete = { passcode ->
            if (verifying) return@SatraPasscodeScreen
            scope.launch {
                verifying = true
                if (walletRepository.verifyAppPasscode(passcode)) {
                    onVerified()
                } else {
                    verifying = false
                    errorMessage = context.getString(R.string.settings_security_wrong_passcode)
                    resetNonce += 1
                }
            }
        },
    )
}

@Composable
internal fun SatraSecurityScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onCreatePasscode: () -> Unit,
    onTurnOffPasscode: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var showAutoLockSheet by remember { mutableStateOf(false) }
    var showEraseWalletSheet by remember { mutableStateOf(false) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    if (showAutoLockSheet) {
        appSettings?.let { settings ->
            AutoLockOptionsSheet(
                currentTimeoutMillis = settings.autoLockTimeoutMillis,
                onSelect = { timeoutMillis ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(autoLockTimeoutMillis = timeoutMillis),
                        )
                    }
                    showAutoLockSheet = false
                },
                onDismiss = { showAutoLockSheet = false },
            )
        }
    }

    if (showEraseWalletSheet) {
        appSettings?.let { settings ->
            EraseWalletProtectionSheet(
                settings = settings,
                onEnabledChange = { enabled ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(eraseWalletEnabled = enabled),
                        )
                    }
                },
                onLimitSelected = { limit ->
                    scope.launch {
                        appSettings = walletRepository.updateAppSettings(
                            AppSettingsUpdate(eraseWalletEnabled = true, eraseWalletAttemptLimit = limit),
                        )
                    }
                },
                onDismiss = { showEraseWalletSheet = false },
            )
        }
    }

    SettingsScaffold(
        titleRes = R.string.settings_security_title,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                val settings = appSettings
                if (settings?.passcodeEnabled == true) {
                    StaticSettingsRow(
                        title = stringResource(R.string.settings_security_current_passcode),
                        body = stringResource(R.string.settings_security_body_enabled),
                    )
                    SettingsDivider()
                    SettingsRow(
                        iconRes = R.drawable.ic_brand_security,
                        title = stringResource(R.string.settings_security_turn_off_passcode),
                        body = stringResource(R.string.settings_security_turn_off_passcode_body),
                        onClick = onTurnOffPasscode,
                    )
                } else {
                    SettingsRow(
                        iconRes = R.drawable.ic_brand_security,
                        title = stringResource(R.string.settings_security_turn_on_passcode),
                        body = stringResource(R.string.settings_security_body_disabled),
                        onClick = onCreatePasscode,
                    )
                }
            }
        }
        item { SettingsSectionTitle(R.string.settings_security_access_title) }
        item {
            SettingsCard {
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_satra_id,
                    title = stringResource(R.string.settings_security_biometrics_title),
                    body = stringResource(R.string.settings_security_biometrics_body),
                    checked = appSettings?.biometricsEnabled == true,
                    enabled = appSettings?.passcodeEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(biometricsEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                val currentAutoLock = autoLockOptions.firstOrNull {
                    it.timeoutMillis == appSettings?.autoLockTimeoutMillis
                } ?: autoLockOptions.first()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_history,
                    title = stringResource(R.string.settings_security_auto_lock_title),
                    body = stringResource(currentAutoLock.titleRes),
                    onClick = { showAutoLockSheet = true },
                )
                SettingsDivider()
                val eraseBody = if (appSettings?.eraseWalletEnabled == true) {
                    stringResource(
                        R.string.settings_security_erase_wallet_enabled_value,
                        appSettings?.eraseWalletAttemptLimit ?: 10,
                    )
                } else {
                    stringResource(R.string.settings_security_erase_wallet_disabled_value)
                }
                SettingsRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_security_erase_wallet_title),
                    body = eraseBody,
                    onClick = { showEraseWalletSheet = true },
                    isDanger = appSettings?.eraseWalletEnabled == true,
                )
            }
        }
    }
}

@Composable
internal fun SatraSecurityCreatePasscodeScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onPasscodeCreated: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SatraPasscodeScreen(
        passcodeLength = 6,
        title = stringResource(R.string.settings_security_turn_on_passcode),
        body = stringResource(R.string.settings_security_new_passcode),
        settings = appSettings.toPasscodeUiSettings(),
        biometricsEnabled = false,
        onPasscodeComplete = { passcode ->
            if (saving) return@SatraPasscodeScreen
            scope.launch {
                saving = true
                walletRepository.setAppPasscode(passcode)
                onPasscodeCreated()
            }
        },
    )
}

@Composable
internal fun SatraSecurityTurnOffPasscodeScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onPasscodeDisabled: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var resetNonce by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var disabling by remember { mutableStateOf(false) }

    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SatraPasscodeScreen(
        passcodeLength = appSettings?.passcodeLength ?: 6,
        title = stringResource(R.string.settings_security_turn_off_passcode),
        body = stringResource(R.string.settings_security_turn_off_passcode_screen_body),
        settings = appSettings.toPasscodeUiSettings(),
        resetNonce = resetNonce,
        errorMessage = errorMessage,
        biometricsEnabled = false,
        onPasscodeComplete = { passcode ->
            if (disabling) return@SatraPasscodeScreen
            scope.launch {
                disabling = true
                if (walletRepository.verifyAppPasscode(passcode)) {
                    walletRepository.clearAppPasscode()
                    onPasscodeDisabled()
                } else {
                    disabling = false
                    errorMessage = context.getString(R.string.settings_security_wrong_passcode)
                    resetNonce += 1
                }
            }
        },
    )
}

@Composable
internal fun SatraNotificationsScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SettingsScaffold(
        titleRes = R.string.settings_notifications_title,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                NotificationSwitch(
                    iconRes = R.drawable.ic_brand_document,
                    titleRes = R.string.settings_notifications_news,
                    bodyRes = R.string.settings_notifications_news_body,
                    checked = appSettings?.notificationsNewsEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsNewsEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                NotificationSwitch(
                    iconRes = R.drawable.ic_brand_price_alert,
                    titleRes = R.string.settings_notifications_prices,
                    bodyRes = R.string.settings_notifications_prices_body,
                    checked = appSettings?.notificationsPricesEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsPricesEnabled = enabled),
                            )
                        }
                    },
                )
                SettingsDivider()
                NotificationSwitch(
                    iconRes = R.drawable.ic_brand_transaction_alert,
                    titleRes = R.string.settings_notifications_transactions,
                    bodyRes = R.string.settings_notifications_transactions_body,
                    checked = appSettings?.notificationsTransactionsEnabled == true,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            appSettings = walletRepository.updateAppSettings(
                                AppSettingsUpdate(notificationsTransactionsEnabled = enabled),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SatraAboutScreen(
    appVersion: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val privacyUrl = stringResource(R.string.settings_url_privacy)
    val termsUrl = stringResource(R.string.settings_url_terms)
    val sourceUrl = stringResource(R.string.settings_url_source)
    fun openUrl(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    SettingsScaffold(
        titleRes = R.string.settings_about_title,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                StaticSettingsRow(
                    title = stringResource(R.string.settings_about_version),
                    body = appVersion,
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_document,
                    title = stringResource(R.string.settings_privacy_policy),
                    body = stringResource(R.string.settings_about_open_website),
                    onClick = { openUrl(privacyUrl) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_brand_document,
                    title = stringResource(R.string.settings_terms_of_use),
                    body = stringResource(R.string.settings_about_open_website),
                    onClick = { openUrl(termsUrl) },
                )
                SettingsDivider()
                SettingsRow(
                    iconRes = R.drawable.ic_github_invertocat,
                    title = stringResource(R.string.settings_about_source),
                    body = stringResource(R.string.settings_about_open_github),
                    onClick = { openUrl(sourceUrl) },
                )
            }
        }
    }
}

@Composable
internal fun SatraDangerZoneScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onContinueReset: () -> Unit,
) {
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var agreementAccepted by remember { mutableStateOf(false) }
    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }
    val passcodeEnabled = appSettings?.passcodeEnabled == true
    val canContinue = agreementAccepted && passcodeEnabled

    SettingsScaffold(
        titleRes = R.string.settings_danger_title,
        onBack = onBack,
    ) {
        item {
            SettingsCard {
                Text(
                    text = stringResource(R.string.settings_danger_reset_warning),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                )
                SettingsDivider()
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_security,
                    title = stringResource(R.string.settings_danger_reset_agreement_title),
                    body = stringResource(R.string.settings_danger_reset_agreement_body),
                    checked = agreementAccepted,
                    onCheckedChange = { accepted -> agreementAccepted = accepted },
                )
                if (!passcodeEnabled && appSettings != null) {
                    SettingsDivider()
                    Text(
                        text = stringResource(R.string.settings_danger_reset_passcode_required_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                SatraButton(
                    text = stringResource(R.string.settings_danger_reset_action),
                    onClick = onContinueReset,
                    enabled = canContinue,
                    modifier = Modifier
                        .fillMaxWidth(),
                    variant = SatraButtonVariant.Danger,
                )
            }
        }
    }
}

@Composable
internal fun SatraDangerZonePasscodeScreen(
    walletRepository: SatraWalletRepository,
    onBack: () -> Unit,
    onResetComplete: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var appSettings by remember { mutableStateOf<AppSettingsRecord?>(null) }
    var resetting by remember { mutableStateOf(false) }
    var resetNonce by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(walletRepository) {
        appSettings = walletRepository.getAppSettings()
    }

    SatraPasscodeScreen(
        passcodeLength = appSettings?.passcodeLength ?: 6,
        title = stringResource(R.string.settings_danger_verify_passcode),
        body = stringResource(R.string.settings_danger_verify_passcode_body),
        settings = appSettings.toPasscodeUiSettings(),
        resetNonce = resetNonce,
        errorMessage = errorMessage,
        biometricsEnabled = false,
        onPasscodeComplete = { passcode ->
            if (resetting) return@SatraPasscodeScreen
            scope.launch {
                resetting = true
                if (walletRepository.verifyAppPasscode(passcode)) {
                    walletRepository.resetUserData()
                    onResetComplete()
                } else {
                    resetting = false
                    errorMessage = context.getString(R.string.settings_security_wrong_passcode)
                    resetNonce += 1
                }
            }
        },
    )
}

@Composable
private fun SettingsScaffold(
    @StringRes titleRes: Int,
    onBack: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = SettingsContentMaxWidth)
                    .padding(top = 18.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.wallet_setup_back_content_description),
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(titleRes),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        content()
        item { Spacer(modifier = Modifier.height(18.dp)) }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsListCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            content = content,
        )
    }
}

@Composable
private fun SettingsRow(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    onClick: () -> Unit,
    isDanger: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(iconRes = iconRes, isDanger = isDanger)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDanger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier.width(24.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_brand_chevron_end),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f),
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun StaticSettingsRow(
    title: String,
    body: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun SelectableSettingsRow(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(
                modifier = Modifier.size(34.dp),
                contentAlignment = Alignment.Center,
            ) {
                leading()
            }
            Spacer(modifier = Modifier.width(14.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RadioButton(selected = selected, onClick = onClick)
    }
}

@Composable
private fun SettingsFlagGlyph(flag: String) {
    Text(
        text = flag,
        style = MaterialTheme.typography.titleLarge,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockOptionsSheet(
    currentTimeoutMillis: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_security_auto_lock_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_security_auto_lock_sheet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            autoLockOptions.forEach { option ->
                SettingsSheetChoice(
                    title = stringResource(option.titleRes),
                    body = stringResource(option.bodyRes),
                    selected = option.timeoutMillis == currentTimeoutMillis,
                    onClick = { onSelect(option.timeoutMillis) },
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EraseWalletProtectionSheet(
    settings: AppSettingsRecord,
    onEnabledChange: (Boolean) -> Unit,
    onLimitSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_security_erase_wallet_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.settings_security_erase_wallet_sheet_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingsCard {
                SettingsSwitchRow(
                    iconRes = R.drawable.ic_brand_empty,
                    title = stringResource(R.string.settings_security_erase_wallet_title),
                    body = stringResource(R.string.settings_security_erase_wallet_body),
                    checked = settings.eraseWalletEnabled,
                    onCheckedChange = onEnabledChange,
                )
            }
            if (settings.eraseWalletEnabled) {
                Text(
                    text = stringResource(R.string.settings_security_erase_attempts_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                listOf(5, 10, 15, 20).forEach { limit ->
                    SettingsSheetChoice(
                        title = stringResource(R.string.settings_security_erase_attempt_limit, limit),
                        body = stringResource(R.string.settings_security_erase_attempt_limit_body),
                        selected = settings.eraseWalletAttemptLimit == limit,
                        onClick = { onLimitSelected(limit) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsSheetChoice(
    title: String,
    body: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor.copy(alpha = 0.72f),
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    @DrawableRes iconRes: Int,
    title: String,
    body: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsIcon(iconRes = iconRes)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

@Composable
private fun NotificationSwitch(
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        iconRes = iconRes,
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        checked = checked,
        onCheckedChange = onCheckedChange,
    )
}

@Composable
private fun AddressBookEntryRow(
    entry: AddressBookEntryRecord,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SatraCryptoIcon(
                iconRes = networkIconRes(entry.networkId),
                modifier = Modifier.size(44.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${entry.networkId} · ${entry.address.shortSettingsValue()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SatraButton(
                text = stringResource(R.string.settings_action_edit),
                onClick = onEdit,
                modifier = Modifier.weight(1f),
                variant = SatraButtonVariant.Secondary,
                height = SatraButtonDefaults.CompactHeight,
            )
            SatraButton(
                text = stringResource(R.string.settings_action_delete),
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                variant = SatraButtonVariant.Danger,
                height = SatraButtonDefaults.CompactHeight,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddressBookEditorSheet(
    entry: AddressBookEntryRecord?,
    onDismiss: () -> Unit,
    onSave: (NewAddressBookEntryRecord) -> Unit,
) {
    var label by remember(entry) { mutableStateOf(entry?.label.orEmpty()) }
    var networkId by remember(entry) { mutableStateOf(entry?.networkId ?: SupportedAssetCatalog.networks.first().networkId) }
    var address by remember(entry) { mutableStateOf(entry?.address.orEmpty()) }
    var notes by remember(entry) { mutableStateOf(entry?.notes.orEmpty()) }
    var favorite by remember(entry) { mutableStateOf(entry?.isFavorite ?: false) }
    val keyboardActions = satraDoneKeyboardActions()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    if (entry == null) R.string.settings_address_book_add else R.string.settings_address_book_edit,
                ),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.satraSingleLineInput().take(64) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_label)) },
                singleLine = true,
                keyboardOptions = satraDoneKeyboardOptions(),
                keyboardActions = keyboardActions,
            )
            OutlinedTextField(
                value = address,
                onValueChange = {
                    address = it.satraSingleLineInput(newlineReplacement = "")
                        .trim()
                        .take(160)
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_address)) },
                keyboardOptions = satraDoneKeyboardOptions(),
                keyboardActions = keyboardActions,
                minLines = 2,
            )
            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it.satraSingleLineInput().take(160) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_address_book_notes)) },
                keyboardOptions = satraDoneKeyboardOptions(),
                keyboardActions = keyboardActions,
                minLines = 2,
            )
            Text(
                text = stringResource(R.string.settings_address_book_network),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            SupportedAssetCatalog.networks.forEach { network ->
                SelectableSettingsRow(
                    title = network.displayName,
                    body = network.nativeSymbol,
                    selected = networkId == network.networkId,
                    onClick = { networkId = network.networkId },
                )
            }
            SettingsSwitchRow(
                iconRes = R.drawable.ic_brand_favorite,
                title = stringResource(R.string.settings_address_book_favorite),
                body = stringResource(R.string.settings_address_book_favorite_body),
                checked = favorite,
                onCheckedChange = { favorite = it },
            )
            SatraButton(
                text = stringResource(R.string.settings_action_save),
                onClick = {
                    onSave(
                        NewAddressBookEntryRecord(
                            label = label.trim(),
                            networkId = networkId,
                            address = address.trim(),
                            notes = notes.trim().takeIf(String::isNotBlank),
                            isFavorite = favorite,
                        ),
                    )
                },
                enabled = label.isNotBlank() && address.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(18.dp))
        }
    }
}

@Composable
private fun SettingsIcon(
    @DrawableRes iconRes: Int,
    isDanger: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        if (isDanger) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun SettingsSectionTitle(@StringRes titleRes: Int) {
    Text(
        text = stringResource(titleRes),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = SettingsContentMaxWidth)
            .padding(top = 6.dp),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SettingsEmptyCard(
    @StringRes titleRes: Int,
    @StringRes bodyRes: Int,
) {
    SatraEmptyState(
        title = stringResource(titleRes),
        body = stringResource(bodyRes),
        modifier = Modifier.fillMaxWidth(),
    )
}

private val SatraThemePreference.titleRes: Int
    @StringRes get() = when (this) {
        SatraThemePreference.System -> R.string.settings_theme_system
        SatraThemePreference.Light -> R.string.settings_theme_light
        SatraThemePreference.Dark -> R.string.settings_theme_dark
    }

private val SatraThemePreference.bodyRes: Int
    @StringRes get() = when (this) {
        SatraThemePreference.System -> R.string.settings_theme_system_body
        SatraThemePreference.Light -> R.string.settings_theme_light_body
        SatraThemePreference.Dark -> R.string.settings_theme_dark_body
    }

private data class SettingsLanguage(
    val tag: String,
    @StringRes val labelRes: Int,
    @StringRes val countryRes: Int,
    val flag: String,
)

private data class SettingsLanguageLabel(
    val language: SettingsLanguage,
    val label: String,
    val country: String,
) {
    fun matches(query: String): Boolean {
        val normalizedQuery = query.trim()
        return normalizedQuery.isBlank() ||
            label.contains(normalizedQuery, ignoreCase = true) ||
            country.contains(normalizedQuery, ignoreCase = true) ||
            language.tag.contains(normalizedQuery, ignoreCase = true) ||
            language.flag.contains(normalizedQuery, ignoreCase = true)
    }
}

private data class CurrencyOption(
    val code: String,
    @StringRes val nameRes: Int,
    val symbol: String,
    val flag: String,
)

private data class CurrencyOptionLabel(
    val option: CurrencyOption,
    val name: String,
) {
    fun matches(query: String): Boolean {
        val normalizedQuery = query.trim()
        return normalizedQuery.isBlank() ||
            option.code.contains(normalizedQuery, ignoreCase = true) ||
            name.contains(normalizedQuery, ignoreCase = true) ||
            option.symbol.contains(normalizedQuery, ignoreCase = true)
    }
}

private data class WalletBackupRequest(
    val walletId: String,
    val mode: WalletBackupMode,
)

private fun AppSettingsRecord?.toPasscodeUiSettings(): SatraSettings =
    SatraSettings(hapticsEnabled = this?.hapticsEnabled ?: true)

private fun AppSettingsRecord.hasUsablePasscode(): Boolean =
    passcodeEnabled &&
        !passcodeHash.isNullOrBlank() &&
        !passcodeSalt.isNullOrBlank()

private data class AutoLockOption(
    val timeoutMillis: Long,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
)

private val commonCurrencyCodes = listOf(
    "USD",
    "EUR",
    "AED",
    "GBP",
    "JPY",
    "CNY",
    "INR",
    "CAD",
    "AUD",
    "CHF",
    "SGD",
    "HKD",
    "SAR",
    "BRL",
)

private val excludedCurrencyCodes = setOf(
    "ADP",
    "AFA",
    "ATS",
    "AYM",
    "AZM",
    "BEF",
    "BGL",
    "BOV",
    "BYB",
    "BYR",
    "CHE",
    "CHW",
    "CLF",
    "COU",
    "CSD",
    "CYP",
    "DEM",
    "EEK",
    "ESP",
    "FIM",
    "FRF",
    "GHC",
    "GRD",
    "GWP",
    "IEP",
    "ITL",
    "LTL",
    "LUF",
    "LVL",
    "MGF",
    "MRO",
    "MTL",
    "MXV",
    "MZM",
    "NLG",
    "PTE",
    "ROL",
    "RUR",
    "SDD",
    "SIT",
    "SKK",
    "SRG",
    "STD",
    "TMM",
    "TPE",
    "TRL",
    "USN",
    "USS",
    "UYI",
    "VEB",
    "VEF",
    "XAD",
    "XAG",
    "XAU",
    "XBA",
    "XBB",
    "XBC",
    "XBD",
    "XDR",
    "XFO",
    "XFU",
    "XPD",
    "XPT",
    "XSU",
    "XTS",
    "XUA",
    "XXX",
    "YUM",
    "ZMK",
    "ZWD",
    "ZWL",
    "ZWN",
    "ZWR",
)

private fun activeCurrencyOptions(): List<CurrencyOption> {
    val activeCurrencyCodes = Currency.getAvailableCurrencies()
        .mapTo(mutableSetOf()) { currency -> currency.currencyCode.uppercase(Locale.US) }

    return allCurrencyOptions
        .asSequence()
        .filter { option -> option.code in activeCurrencyCodes }
        .filterNot { option -> option.code in excludedCurrencyCodes }
        .sortedWith(
            compareBy<CurrencyOption> { option ->
                commonCurrencyCodes.indexOf(option.code).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
            }.thenBy { option -> option.code },
        )
        .toList()
}

private val allCurrencyOptions: List<CurrencyOption> = listOf(
    CurrencyOption("USD", R.string.settings_currency_name_usd, "$", "🇺🇸"),
    CurrencyOption("ADP", R.string.settings_currency_name_adp, "ADP", "🇦🇩"),
    CurrencyOption("AED", R.string.settings_currency_name_aed, "AED", "🇦🇪"),
    CurrencyOption("AFA", R.string.settings_currency_name_afa, "AFA", "🇦🇫"),
    CurrencyOption("AFN", R.string.settings_currency_name_afn, "AFN", "🇦🇫"),
    CurrencyOption("ALL", R.string.settings_currency_name_all, "ALL", "🇦🇱"),
    CurrencyOption("AMD", R.string.settings_currency_name_amd, "AMD", "🇦🇲"),
    CurrencyOption("ANG", R.string.settings_currency_name_ang, "ANG", "🇦🇳"),
    CurrencyOption("AOA", R.string.settings_currency_name_aoa, "AOA", "🇦🇴"),
    CurrencyOption("ARS", R.string.settings_currency_name_ars, "ARS", "🇦🇷"),
    CurrencyOption("ATS", R.string.settings_currency_name_ats, "ATS", "🇦🇹"),
    CurrencyOption("AUD", R.string.settings_currency_name_aud, "A$", "🇦🇺"),
    CurrencyOption("AWG", R.string.settings_currency_name_awg, "AWG", "🇦🇼"),
    CurrencyOption("AYM", R.string.settings_currency_name_aym, "AYM", "🌐"),
    CurrencyOption("AZM", R.string.settings_currency_name_azm, "AZM", "🇦🇿"),
    CurrencyOption("AZN", R.string.settings_currency_name_azn, "AZN", "🇦🇿"),
    CurrencyOption("BAM", R.string.settings_currency_name_bam, "BAM", "🇧🇦"),
    CurrencyOption("BBD", R.string.settings_currency_name_bbd, "BBD", "🇧🇧"),
    CurrencyOption("BDT", R.string.settings_currency_name_bdt, "BDT", "🇧🇩"),
    CurrencyOption("BEF", R.string.settings_currency_name_bef, "BEF", "🇧🇪"),
    CurrencyOption("BGL", R.string.settings_currency_name_bgl, "BGL", "🇧🇬"),
    CurrencyOption("BGN", R.string.settings_currency_name_bgn, "BGN", "🇧🇬"),
    CurrencyOption("BHD", R.string.settings_currency_name_bhd, "BHD", "🇧🇭"),
    CurrencyOption("BIF", R.string.settings_currency_name_bif, "BIF", "🇧🇮"),
    CurrencyOption("BMD", R.string.settings_currency_name_bmd, "BMD", "🇧🇲"),
    CurrencyOption("BND", R.string.settings_currency_name_bnd, "BND", "🇧🇳"),
    CurrencyOption("BOB", R.string.settings_currency_name_bob, "BOB", "🇧🇴"),
    CurrencyOption("BOV", R.string.settings_currency_name_bov, "BOV", "🇧🇴"),
    CurrencyOption("BRL", R.string.settings_currency_name_brl, "R$", "🇧🇷"),
    CurrencyOption("BSD", R.string.settings_currency_name_bsd, "BSD", "🇧🇸"),
    CurrencyOption("BTN", R.string.settings_currency_name_btn, "BTN", "🇧🇹"),
    CurrencyOption("BWP", R.string.settings_currency_name_bwp, "BWP", "🇧🇼"),
    CurrencyOption("BYB", R.string.settings_currency_name_byb, "BYB", "🇧🇾"),
    CurrencyOption("BYN", R.string.settings_currency_name_byn, "BYN", "🇧🇾"),
    CurrencyOption("BYR", R.string.settings_currency_name_byr, "BYR", "🇧🇾"),
    CurrencyOption("BZD", R.string.settings_currency_name_bzd, "BZD", "🇧🇿"),
    CurrencyOption("CAD", R.string.settings_currency_name_cad, "CA$", "🇨🇦"),
    CurrencyOption("CDF", R.string.settings_currency_name_cdf, "CDF", "🇨🇩"),
    CurrencyOption("CHE", R.string.settings_currency_name_che, "CHE", "🇨🇭"),
    CurrencyOption("CHF", R.string.settings_currency_name_chf, "CHF", "🇨🇭"),
    CurrencyOption("CHW", R.string.settings_currency_name_chw, "CHW", "🇨🇭"),
    CurrencyOption("CLF", R.string.settings_currency_name_clf, "CLF", "🇨🇱"),
    CurrencyOption("CLP", R.string.settings_currency_name_clp, "CLP", "🇨🇱"),
    CurrencyOption("CNY", R.string.settings_currency_name_cny, "CN¥", "🇨🇳"),
    CurrencyOption("COP", R.string.settings_currency_name_cop, "COP", "🇨🇴"),
    CurrencyOption("COU", R.string.settings_currency_name_cou, "COU", "🇨🇴"),
    CurrencyOption("CRC", R.string.settings_currency_name_crc, "CRC", "🇨🇷"),
    CurrencyOption("CSD", R.string.settings_currency_name_csd, "CSD", "🇷🇸"),
    CurrencyOption("CUC", R.string.settings_currency_name_cuc, "CUC", "🇨🇺"),
    CurrencyOption("CUP", R.string.settings_currency_name_cup, "CUP", "🇨🇺"),
    CurrencyOption("CVE", R.string.settings_currency_name_cve, "CVE", "🇨🇻"),
    CurrencyOption("CYP", R.string.settings_currency_name_cyp, "CYP", "🇨🇾"),
    CurrencyOption("CZK", R.string.settings_currency_name_czk, "CZK", "🇨🇿"),
    CurrencyOption("DEM", R.string.settings_currency_name_dem, "DEM", "🇩🇪"),
    CurrencyOption("DJF", R.string.settings_currency_name_djf, "DJF", "🇩🇯"),
    CurrencyOption("DKK", R.string.settings_currency_name_dkk, "DKK", "🇩🇰"),
    CurrencyOption("DOP", R.string.settings_currency_name_dop, "DOP", "🇩🇴"),
    CurrencyOption("DZD", R.string.settings_currency_name_dzd, "DZD", "🇩🇿"),
    CurrencyOption("EEK", R.string.settings_currency_name_eek, "EEK", "🇪🇪"),
    CurrencyOption("EGP", R.string.settings_currency_name_egp, "EGP", "🇪🇬"),
    CurrencyOption("ERN", R.string.settings_currency_name_ern, "ERN", "🇪🇷"),
    CurrencyOption("ESP", R.string.settings_currency_name_esp, "ESP", "🇪🇸"),
    CurrencyOption("ETB", R.string.settings_currency_name_etb, "ETB", "🇪🇹"),
    CurrencyOption("EUR", R.string.settings_currency_name_eur, "€", "🇪🇺"),
    CurrencyOption("FIM", R.string.settings_currency_name_fim, "FIM", "🇫🇮"),
    CurrencyOption("FJD", R.string.settings_currency_name_fjd, "FJD", "🇫🇯"),
    CurrencyOption("FKP", R.string.settings_currency_name_fkp, "FKP", "🇫🇰"),
    CurrencyOption("FRF", R.string.settings_currency_name_frf, "FRF", "🇫🇷"),
    CurrencyOption("GBP", R.string.settings_currency_name_gbp, "£", "🇬🇧"),
    CurrencyOption("GEL", R.string.settings_currency_name_gel, "GEL", "🇬🇪"),
    CurrencyOption("GHC", R.string.settings_currency_name_ghc, "GHC", "🇬🇭"),
    CurrencyOption("GHS", R.string.settings_currency_name_ghs, "GHS", "🇬🇭"),
    CurrencyOption("GIP", R.string.settings_currency_name_gip, "GIP", "🇬🇮"),
    CurrencyOption("GMD", R.string.settings_currency_name_gmd, "GMD", "🇬🇲"),
    CurrencyOption("GNF", R.string.settings_currency_name_gnf, "GNF", "🇬🇳"),
    CurrencyOption("GRD", R.string.settings_currency_name_grd, "GRD", "🇬🇷"),
    CurrencyOption("GTQ", R.string.settings_currency_name_gtq, "GTQ", "🇬🇹"),
    CurrencyOption("GWP", R.string.settings_currency_name_gwp, "GWP", "🇬🇼"),
    CurrencyOption("GYD", R.string.settings_currency_name_gyd, "GYD", "🇬🇾"),
    CurrencyOption("HKD", R.string.settings_currency_name_hkd, "HK$", "🇭🇰"),
    CurrencyOption("HNL", R.string.settings_currency_name_hnl, "HNL", "🇭🇳"),
    CurrencyOption("HRK", R.string.settings_currency_name_hrk, "HRK", "🇭🇷"),
    CurrencyOption("HTG", R.string.settings_currency_name_htg, "HTG", "🇭🇹"),
    CurrencyOption("HUF", R.string.settings_currency_name_huf, "HUF", "🇭🇺"),
    CurrencyOption("IDR", R.string.settings_currency_name_idr, "IDR", "🇮🇩"),
    CurrencyOption("IEP", R.string.settings_currency_name_iep, "IEP", "🇮🇪"),
    CurrencyOption("ILS", R.string.settings_currency_name_ils, "₪", "🇮🇱"),
    CurrencyOption("INR", R.string.settings_currency_name_inr, "₹", "🇮🇳"),
    CurrencyOption("IQD", R.string.settings_currency_name_iqd, "IQD", "🇮🇶"),
    CurrencyOption("IRR", R.string.settings_currency_name_irr, "IRR", "🇮🇷"),
    CurrencyOption("ISK", R.string.settings_currency_name_isk, "ISK", "🇮🇸"),
    CurrencyOption("ITL", R.string.settings_currency_name_itl, "ITL", "🇮🇹"),
    CurrencyOption("JMD", R.string.settings_currency_name_jmd, "JMD", "🇯🇲"),
    CurrencyOption("JOD", R.string.settings_currency_name_jod, "JOD", "🇯🇴"),
    CurrencyOption("JPY", R.string.settings_currency_name_jpy, "¥", "🇯🇵"),
    CurrencyOption("KES", R.string.settings_currency_name_kes, "KES", "🇰🇪"),
    CurrencyOption("KGS", R.string.settings_currency_name_kgs, "KGS", "🇰🇬"),
    CurrencyOption("KHR", R.string.settings_currency_name_khr, "KHR", "🇰🇭"),
    CurrencyOption("KMF", R.string.settings_currency_name_kmf, "KMF", "🇰🇲"),
    CurrencyOption("KPW", R.string.settings_currency_name_kpw, "KPW", "🇰🇵"),
    CurrencyOption("KRW", R.string.settings_currency_name_krw, "₩", "🇰🇷"),
    CurrencyOption("KWD", R.string.settings_currency_name_kwd, "KWD", "🇰🇼"),
    CurrencyOption("KYD", R.string.settings_currency_name_kyd, "KYD", "🇰🇾"),
    CurrencyOption("KZT", R.string.settings_currency_name_kzt, "KZT", "🇰🇿"),
    CurrencyOption("LAK", R.string.settings_currency_name_lak, "LAK", "🇱🇦"),
    CurrencyOption("LBP", R.string.settings_currency_name_lbp, "LBP", "🇱🇧"),
    CurrencyOption("LKR", R.string.settings_currency_name_lkr, "LKR", "🇱🇰"),
    CurrencyOption("LRD", R.string.settings_currency_name_lrd, "LRD", "🇱🇷"),
    CurrencyOption("LSL", R.string.settings_currency_name_lsl, "LSL", "🇱🇸"),
    CurrencyOption("LTL", R.string.settings_currency_name_ltl, "LTL", "🇱🇹"),
    CurrencyOption("LUF", R.string.settings_currency_name_luf, "LUF", "🇱🇺"),
    CurrencyOption("LVL", R.string.settings_currency_name_lvl, "LVL", "🇱🇻"),
    CurrencyOption("LYD", R.string.settings_currency_name_lyd, "LYD", "🇱🇾"),
    CurrencyOption("MAD", R.string.settings_currency_name_mad, "MAD", "🇲🇦"),
    CurrencyOption("MDL", R.string.settings_currency_name_mdl, "MDL", "🇲🇩"),
    CurrencyOption("MGA", R.string.settings_currency_name_mga, "MGA", "🇲🇬"),
    CurrencyOption("MGF", R.string.settings_currency_name_mgf, "MGF", "🇲🇬"),
    CurrencyOption("MKD", R.string.settings_currency_name_mkd, "MKD", "🇲🇰"),
    CurrencyOption("MMK", R.string.settings_currency_name_mmk, "MMK", "🇲🇲"),
    CurrencyOption("MNT", R.string.settings_currency_name_mnt, "MNT", "🇲🇳"),
    CurrencyOption("MOP", R.string.settings_currency_name_mop, "MOP", "🇲🇴"),
    CurrencyOption("MRO", R.string.settings_currency_name_mro, "MRO", "🇲🇷"),
    CurrencyOption("MRU", R.string.settings_currency_name_mru, "MRU", "🇲🇷"),
    CurrencyOption("MTL", R.string.settings_currency_name_mtl, "MTL", "🇲🇹"),
    CurrencyOption("MUR", R.string.settings_currency_name_mur, "MUR", "🇲🇺"),
    CurrencyOption("MVR", R.string.settings_currency_name_mvr, "MVR", "🇲🇻"),
    CurrencyOption("MWK", R.string.settings_currency_name_mwk, "MWK", "🇲🇼"),
    CurrencyOption("MXN", R.string.settings_currency_name_mxn, "MX$", "🇲🇽"),
    CurrencyOption("MXV", R.string.settings_currency_name_mxv, "MXV", "🇲🇽"),
    CurrencyOption("MYR", R.string.settings_currency_name_myr, "MYR", "🇲🇾"),
    CurrencyOption("MZM", R.string.settings_currency_name_mzm, "MZM", "🇲🇿"),
    CurrencyOption("MZN", R.string.settings_currency_name_mzn, "MZN", "🇲🇿"),
    CurrencyOption("NAD", R.string.settings_currency_name_nad, "NAD", "🇳🇦"),
    CurrencyOption("NGN", R.string.settings_currency_name_ngn, "NGN", "🇳🇬"),
    CurrencyOption("NIO", R.string.settings_currency_name_nio, "NIO", "🇳🇮"),
    CurrencyOption("NLG", R.string.settings_currency_name_nlg, "NLG", "🇳🇱"),
    CurrencyOption("NOK", R.string.settings_currency_name_nok, "NOK", "🇳🇴"),
    CurrencyOption("NPR", R.string.settings_currency_name_npr, "NPR", "🇳🇵"),
    CurrencyOption("NZD", R.string.settings_currency_name_nzd, "NZ$", "🇳🇿"),
    CurrencyOption("OMR", R.string.settings_currency_name_omr, "OMR", "🇴🇲"),
    CurrencyOption("PAB", R.string.settings_currency_name_pab, "PAB", "🇵🇦"),
    CurrencyOption("PEN", R.string.settings_currency_name_pen, "PEN", "🇵🇪"),
    CurrencyOption("PGK", R.string.settings_currency_name_pgk, "PGK", "🇵🇬"),
    CurrencyOption("PHP", R.string.settings_currency_name_php, "₱", "🇵🇭"),
    CurrencyOption("PKR", R.string.settings_currency_name_pkr, "PKR", "🇵🇰"),
    CurrencyOption("PLN", R.string.settings_currency_name_pln, "PLN", "🇵🇱"),
    CurrencyOption("PTE", R.string.settings_currency_name_pte, "PTE", "🇵🇹"),
    CurrencyOption("PYG", R.string.settings_currency_name_pyg, "PYG", "🇵🇾"),
    CurrencyOption("QAR", R.string.settings_currency_name_qar, "QAR", "🇶🇦"),
    CurrencyOption("ROL", R.string.settings_currency_name_rol, "ROL", "🇷🇴"),
    CurrencyOption("RON", R.string.settings_currency_name_ron, "RON", "🇷🇴"),
    CurrencyOption("RSD", R.string.settings_currency_name_rsd, "RSD", "🇷🇸"),
    CurrencyOption("RUB", R.string.settings_currency_name_rub, "RUB", "🇷🇺"),
    CurrencyOption("RUR", R.string.settings_currency_name_rur, "RUR", "🇷🇺"),
    CurrencyOption("RWF", R.string.settings_currency_name_rwf, "RWF", "🇷🇼"),
    CurrencyOption("SAR", R.string.settings_currency_name_sar, "SAR", "🇸🇦"),
    CurrencyOption("SBD", R.string.settings_currency_name_sbd, "SBD", "🇸🇧"),
    CurrencyOption("SCR", R.string.settings_currency_name_scr, "SCR", "🇸🇨"),
    CurrencyOption("SDD", R.string.settings_currency_name_sdd, "SDD", "🇸🇩"),
    CurrencyOption("SDG", R.string.settings_currency_name_sdg, "SDG", "🇸🇩"),
    CurrencyOption("SEK", R.string.settings_currency_name_sek, "SEK", "🇸🇪"),
    CurrencyOption("SGD", R.string.settings_currency_name_sgd, "SGD", "🇸🇬"),
    CurrencyOption("SHP", R.string.settings_currency_name_shp, "SHP", "🇸🇭"),
    CurrencyOption("SIT", R.string.settings_currency_name_sit, "SIT", "🇸🇮"),
    CurrencyOption("SKK", R.string.settings_currency_name_skk, "SKK", "🇸🇰"),
    CurrencyOption("SLE", R.string.settings_currency_name_sle, "SLE", "🇸🇱"),
    CurrencyOption("SLL", R.string.settings_currency_name_sll, "SLL", "🇸🇱"),
    CurrencyOption("SOS", R.string.settings_currency_name_sos, "SOS", "🇸🇴"),
    CurrencyOption("SRD", R.string.settings_currency_name_srd, "SRD", "🇸🇷"),
    CurrencyOption("SRG", R.string.settings_currency_name_srg, "SRG", "🇸🇷"),
    CurrencyOption("SSP", R.string.settings_currency_name_ssp, "SSP", "🇸🇸"),
    CurrencyOption("STD", R.string.settings_currency_name_std, "STD", "🇸🇹"),
    CurrencyOption("STN", R.string.settings_currency_name_stn, "STN", "🇸🇹"),
    CurrencyOption("SVC", R.string.settings_currency_name_svc, "SVC", "🇸🇻"),
    CurrencyOption("SYP", R.string.settings_currency_name_syp, "SYP", "🇸🇾"),
    CurrencyOption("SZL", R.string.settings_currency_name_szl, "SZL", "🇸🇿"),
    CurrencyOption("THB", R.string.settings_currency_name_thb, "THB", "🇹🇭"),
    CurrencyOption("TJS", R.string.settings_currency_name_tjs, "TJS", "🇹🇯"),
    CurrencyOption("TMM", R.string.settings_currency_name_tmm, "TMM", "🇹🇲"),
    CurrencyOption("TMT", R.string.settings_currency_name_tmt, "TMT", "🇹🇲"),
    CurrencyOption("TND", R.string.settings_currency_name_tnd, "TND", "🇹🇳"),
    CurrencyOption("TOP", R.string.settings_currency_name_top, "TOP", "🇹🇴"),
    CurrencyOption("TPE", R.string.settings_currency_name_tpe, "TPE", "🇹🇵"),
    CurrencyOption("TRL", R.string.settings_currency_name_trl, "TRL", "🇹🇷"),
    CurrencyOption("TRY", R.string.settings_currency_name_try, "TRY", "🇹🇷"),
    CurrencyOption("TTD", R.string.settings_currency_name_ttd, "TTD", "🇹🇹"),
    CurrencyOption("TWD", R.string.settings_currency_name_twd, "NT$", "🇹🇼"),
    CurrencyOption("TZS", R.string.settings_currency_name_tzs, "TZS", "🇹🇿"),
    CurrencyOption("UAH", R.string.settings_currency_name_uah, "UAH", "🇺🇦"),
    CurrencyOption("UGX", R.string.settings_currency_name_ugx, "UGX", "🇺🇬"),
    CurrencyOption("USN", R.string.settings_currency_name_usn, "USN", "🇺🇸"),
    CurrencyOption("USS", R.string.settings_currency_name_uss, "USS", "🇺🇸"),
    CurrencyOption("UYI", R.string.settings_currency_name_uyi, "UYI", "🇺🇾"),
    CurrencyOption("UYU", R.string.settings_currency_name_uyu, "UYU", "🇺🇾"),
    CurrencyOption("UZS", R.string.settings_currency_name_uzs, "UZS", "🇺🇿"),
    CurrencyOption("VEB", R.string.settings_currency_name_veb, "VEB", "🇻🇪"),
    CurrencyOption("VED", R.string.settings_currency_name_ved, "VED", "🇻🇪"),
    CurrencyOption("VEF", R.string.settings_currency_name_vef, "VEF", "🇻🇪"),
    CurrencyOption("VES", R.string.settings_currency_name_ves, "VES", "🇻🇪"),
    CurrencyOption("VND", R.string.settings_currency_name_vnd, "₫", "🇻🇳"),
    CurrencyOption("VUV", R.string.settings_currency_name_vuv, "VUV", "🇻🇺"),
    CurrencyOption("WST", R.string.settings_currency_name_wst, "WST", "🇼🇸"),
    CurrencyOption("XAD", R.string.settings_currency_name_xad, "XAD", "🌐"),
    CurrencyOption("XAF", R.string.settings_currency_name_xaf, "FCFA", "🇨🇲"),
    CurrencyOption("XAG", R.string.settings_currency_name_xag, "XAG", "🌐"),
    CurrencyOption("XAU", R.string.settings_currency_name_xau, "XAU", "🌐"),
    CurrencyOption("XBA", R.string.settings_currency_name_xba, "XBA", "🌐"),
    CurrencyOption("XBB", R.string.settings_currency_name_xbb, "XBB", "🌐"),
    CurrencyOption("XBC", R.string.settings_currency_name_xbc, "XBC", "🌐"),
    CurrencyOption("XBD", R.string.settings_currency_name_xbd, "XBD", "🌐"),
    CurrencyOption("XCD", R.string.settings_currency_name_xcd, "EC$", "🇦🇬"),
    CurrencyOption("XCG", R.string.settings_currency_name_xcg, "XCG", "🇨🇼"),
    CurrencyOption("XDR", R.string.settings_currency_name_xdr, "XDR", "🌐"),
    CurrencyOption("XFO", R.string.settings_currency_name_xfo, "XFO", "🌐"),
    CurrencyOption("XFU", R.string.settings_currency_name_xfu, "XFU", "🌐"),
    CurrencyOption("XOF", R.string.settings_currency_name_xof, "F CFA", "🇸🇳"),
    CurrencyOption("XPD", R.string.settings_currency_name_xpd, "XPD", "🌐"),
    CurrencyOption("XPF", R.string.settings_currency_name_xpf, "CFPF", "🇵🇫"),
    CurrencyOption("XPT", R.string.settings_currency_name_xpt, "XPT", "🌐"),
    CurrencyOption("XSU", R.string.settings_currency_name_xsu, "XSU", "🌐"),
    CurrencyOption("XTS", R.string.settings_currency_name_xts, "XTS", "🌐"),
    CurrencyOption("XUA", R.string.settings_currency_name_xua, "XUA", "🌐"),
    CurrencyOption("XXX", R.string.settings_currency_name_xxx, "¤", "🌐"),
    CurrencyOption("YER", R.string.settings_currency_name_yer, "YER", "🇾🇪"),
    CurrencyOption("YUM", R.string.settings_currency_name_yum, "YUM", "🇾🇺"),
    CurrencyOption("ZAR", R.string.settings_currency_name_zar, "ZAR", "🇿🇦"),
    CurrencyOption("ZMK", R.string.settings_currency_name_zmk, "ZMK", "🇿🇲"),
    CurrencyOption("ZMW", R.string.settings_currency_name_zmw, "ZMW", "🇿🇲"),
    CurrencyOption("ZWD", R.string.settings_currency_name_zwd, "ZWD", "🇿🇼"),
    CurrencyOption("ZWG", R.string.settings_currency_name_zwg, "ZWG", "🇿🇼"),
    CurrencyOption("ZWL", R.string.settings_currency_name_zwl, "ZWL", "🇿🇼"),
    CurrencyOption("ZWN", R.string.settings_currency_name_zwn, "ZWN", "🇿🇼"),
    CurrencyOption("ZWR", R.string.settings_currency_name_zwr, "ZWR", "🇿🇼"),
)

private val supportedSettingLanguages = listOf(
    SettingsLanguage("en", R.string.settings_language_english, R.string.settings_country_united_states, "🇺🇸"),
    SettingsLanguage("zh-Hans", R.string.settings_language_chinese_simplified, R.string.settings_country_china, "🇨🇳"),
    SettingsLanguage("hi", R.string.settings_language_hindi, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("es", R.string.settings_language_spanish, R.string.settings_country_spain, "🇪🇸"),
    SettingsLanguage("ar", R.string.settings_language_arabic, R.string.settings_country_saudi_arabia, "🇸🇦"),
    SettingsLanguage("fr", R.string.settings_language_french, R.string.settings_country_france, "🇫🇷"),
    SettingsLanguage("bn", R.string.settings_language_bengali, R.string.settings_country_bangladesh, "🇧🇩"),
    SettingsLanguage("pt", R.string.settings_language_portuguese, R.string.settings_country_brazil, "🇧🇷"),
    SettingsLanguage("id", R.string.settings_language_indonesian, R.string.settings_country_indonesia, "🇮🇩"),
    SettingsLanguage("ur", R.string.settings_language_urdu, R.string.settings_country_pakistan, "🇵🇰"),
    SettingsLanguage("ru", R.string.settings_language_russian, R.string.settings_country_russia, "🇷🇺"),
    SettingsLanguage("de", R.string.settings_language_german, R.string.settings_country_germany, "🇩🇪"),
    SettingsLanguage("ja", R.string.settings_language_japanese, R.string.settings_country_japan, "🇯🇵"),
    SettingsLanguage("pcm", R.string.settings_language_nigerian_pidgin, R.string.settings_country_nigeria, "🇳🇬"),
    SettingsLanguage("arz", R.string.settings_language_egyptian_arabic, R.string.settings_country_egypt, "🇪🇬"),
    SettingsLanguage("mr", R.string.settings_language_marathi, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("vi", R.string.settings_language_vietnamese, R.string.settings_country_vietnam, "🇻🇳"),
    SettingsLanguage("te", R.string.settings_language_telugu, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("sw", R.string.settings_language_swahili, R.string.settings_country_tanzania, "🇹🇿"),
    SettingsLanguage("ha", R.string.settings_language_hausa, R.string.settings_country_nigeria, "🇳🇬"),
    SettingsLanguage("tr", R.string.settings_language_turkish, R.string.settings_country_turkey, "🇹🇷"),
    SettingsLanguage("pnb", R.string.settings_language_western_punjabi, R.string.settings_country_pakistan, "🇵🇰"),
    SettingsLanguage("fil", R.string.settings_language_filipino, R.string.settings_country_philippines, "🇵🇭"),
    SettingsLanguage("ta", R.string.settings_language_tamil, R.string.settings_country_india, "🇮🇳"),
    SettingsLanguage("yue-Hant", R.string.settings_language_cantonese, R.string.settings_country_hong_kong, "🇭🇰"),
)


private val autoLockOptions = listOf(
    AutoLockOption(0L, R.string.settings_auto_lock_immediate, R.string.settings_auto_lock_immediate_body),
    AutoLockOption(60_000L, R.string.settings_auto_lock_1m, R.string.settings_auto_lock_1m_body),
    AutoLockOption(300_000L, R.string.settings_auto_lock_5m, R.string.settings_auto_lock_5m_body),
    AutoLockOption(1_800_000L, R.string.settings_auto_lock_30m, R.string.settings_auto_lock_30m_body),
    AutoLockOption(3_600_000L, R.string.settings_auto_lock_1h, R.string.settings_auto_lock_1h_body),
)

private fun String.shortSettingsValue(): String =
    if (length <= 18) this else "${take(8)}...${takeLast(6)}"

private fun WalletRecord.matchesWalletSearch(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return true

    val walletKeyLabel = when {
        isWatchOnly -> "watch only watch-only"
        walletKeyType == WalletKeyType.Mnemonic.value -> "recovery phrase mnemonic seed"
        walletKeyType == WalletKeyType.PrivateKey.value -> "private key"
        else -> "wallet"
    }
    val statusLabel = if (isActive) "active" else "inactive"
    return listOf(
        walletName,
        walletId,
        walletType,
        walletKeyType,
        walletKeyFingerprint.orEmpty(),
        walletKeyDerivationPath.orEmpty(),
        walletKeyLabel,
        statusLabel,
    ).any { value ->
        value.lowercase(Locale.US).contains(normalizedQuery)
    }
}

private fun WalletPrivateKeyBackupRecord.matchesPrivateKeyBackupSearch(query: String): Boolean {
    val normalizedQuery = query.trim().lowercase(Locale.US)
    if (normalizedQuery.isBlank()) return true

    return listOf(
        networkName,
        networkId,
        address.orEmpty(),
        derivationPath.orEmpty(),
        keySource,
        keyFormat,
        backupFormat,
        backupValue,
    ).any { value ->
        value.lowercase(Locale.US).contains(normalizedQuery)
    }
}

@Composable
private fun walletManagementSubtitle(wallet: WalletRecord): String {
    val walletType = when {
        wallet.isWatchOnly -> stringResource(R.string.settings_wallet_management_type_watch_only)
        wallet.walletKeyType == WalletKeyType.Mnemonic.value -> stringResource(R.string.settings_wallet_management_type_recovery_phrase)
        wallet.walletKeyType == WalletKeyType.PrivateKey.value -> stringResource(R.string.settings_wallet_management_type_private_key)
        else -> stringResource(R.string.settings_wallet_management_type_wallet)
    }
    return if (wallet.isActive) {
        stringResource(R.string.settings_wallet_management_wallet_active_subtitle, walletType)
    } else {
        stringResource(R.string.settings_wallet_management_wallet_inactive_subtitle, walletType)
    }
}

private fun copySecretToClipboard(
    context: Context,
    label: String,
    value: String,
) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, R.string.settings_wallet_management_copied_toast, Toast.LENGTH_SHORT).show()
}

private enum class WalletBackupMode {
    RecoveryPhrase,
    PrivateKeys,
}

private const val REMOVE_CONFIRMATION = "REMOVE"
private val SettingsContentMaxWidth = 720.dp
