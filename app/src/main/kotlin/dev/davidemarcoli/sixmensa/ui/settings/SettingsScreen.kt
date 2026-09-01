package dev.davidemarcoli.sixmensa.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import dev.davidemarcoli.sixmensa.BuildConfig
import dev.davidemarcoli.sixmensa.R
import dev.davidemarcoli.sixmensa.core.ContentLanguage
import dev.davidemarcoli.sixmensa.core.Restaurant
import dev.davidemarcoli.sixmensa.data.local.Settings
import dev.davidemarcoli.sixmensa.data.local.ThemeMode
import dev.davidemarcoli.sixmensa.data.remote.dto.StatusDto
import dev.davidemarcoli.sixmensa.share.ShareActions
import dev.davidemarcoli.sixmensa.ui.theme.supportsDynamicColor

/** Seed presets, brand colour first. A swatch row beats a hex field on a phone. */
private val seedPresets = listOf(
    Settings.DEFAULT_SEED_COLOR,
    0xFF1B6EF3.toInt(),
    0xFF00897B.toInt(),
    0xFF43A047.toInt(),
    0xFF8E24AA.toInt(),
    0xFFF9A825.toInt(),
    0xFFD81B60.toInt(),
    0xFF5D4037.toInt(),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    status: StatusDto?,
    notificationsBlocked: Boolean,
    onBack: () -> Unit,
    onContentLanguage: (ContentLanguage) -> Unit,
    onRestaurant: (Restaurant) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onSeedColor: (Int) -> Unit,
    onNotificationsEnabled: (Boolean) -> Unit,
    onNotificationTime: (Int, Int) -> Unit,
    onNotifyOnlyAtWork: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.nav_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {

            item { SectionHeader(stringResource(R.string.settings_section_content)) }

            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_menu_language),
                    summary = stringResource(R.string.settings_menu_language_summary),
                ) {
                    ContentLanguage.entries.forEach { language ->
                        FilterChip(
                            selected = settings.contentLanguage == language,
                            onClick = { onContentLanguage(language) },
                            label = {
                                Text(
                                    when (language) {
                                        ContentLanguage.DE -> stringResource(R.string.settings_language_de)
                                        ContentLanguage.EN -> stringResource(R.string.settings_language_en)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_restaurant),
                    summary = stringResource(R.string.settings_restaurant_summary),
                ) {
                    Restaurant.entries.forEach { restaurant ->
                        FilterChip(
                            selected = settings.restaurant == restaurant,
                            onClick = { onRestaurant(restaurant) },
                            label = { Text(restaurant.displayName) },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }

            item {
                ChoiceRow(title = stringResource(R.string.settings_theme)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeMode(mode) },
                            label = {
                                Text(
                                    when (mode) {
                                        ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_system)
                                        ThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                        ThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                                    },
                                )
                            },
                        )
                    }
                }
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_dynamic_color)) },
                    supportingContent = {
                        Text(
                            if (supportsDynamicColor) {
                                stringResource(R.string.settings_dynamic_color_summary)
                            } else {
                                stringResource(R.string.settings_dynamic_color_unavailable)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = settings.useDynamicColor && supportsDynamicColor,
                            onCheckedChange = onDynamicColor,
                            // Below API 31 the seeded scheme is the only option.
                            enabled = supportsDynamicColor,
                        )
                    },
                )
            }

            // Only meaningful when the seeded path is actually in use.
            if (!settings.useDynamicColor || !supportsDynamicColor) {
                item {
                    ChoiceRow(
                        title = stringResource(R.string.settings_accent),
                        summary = stringResource(R.string.settings_accent_summary),
                    ) {
                        seedPresets.forEach { preset ->
                            SeedSwatch(
                                color = Color(preset),
                                selected = settings.seedColor == preset,
                                onClick = { onSeedColor(preset) },
                            )
                        }
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_notifications)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications_enabled)) },
                    supportingContent = {
                        Text(
                            if (notificationsBlocked) {
                                stringResource(R.string.settings_notifications_blocked)
                            } else {
                                stringResource(R.string.settings_notifications_summary)
                            },
                        )
                    },
                    trailingContent = {
                        Switch(
                            // Effective state is the setting AND the OS permission.
                            checked = settings.notificationsEnabled && !notificationsBlocked,
                            onCheckedChange = onNotificationsEnabled,
                        )
                    },
                )
            }

            if (notificationsBlocked) {
                item {
                    TextButton(
                        onClick = { context.openNotificationSettings() },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) { Text(stringResource(R.string.settings_notifications_open)) }
                }
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notification_time)) },
                    trailingContent = {
                        TextButton(onClick = { showTimePicker = true }) {
                            Text("%02d:%02d".format(settings.notificationHour, settings.notificationMinute))
                        }
                    },
                )
            }

            item {
                AtWorkSetting(
                    enabled = settings.notifyOnlyAtWork,
                    onEnabledChange = onNotifyOnlyAtWork,
                )
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_app_version)) },
                    trailingContent = { Text(BuildConfig.VERSION_NAME) },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_api_version)) },
                    supportingContent = {
                        status?.features?.updateInterval
                            ?.takeIf { it.isNotEmpty() }
                            ?.let { Text(stringResource(R.string.settings_api_updated, it)) }
                    },
                    trailingContent = {
                        Text(status?.version ?: stringResource(R.string.settings_unavailable))
                    },
                )
            }

            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_website)) },
                    modifier = Modifier.clickable { context.openWebApp() },
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialHour = settings.notificationHour,
            initialMinute = settings.notificationMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onNotificationTime(hour, minute)
                showTimePicker = false
            },
        )
    }
}

/**
 * Location is only ever requested when this is switched on, and in two stages because
 * Android will not grant "all the time" in the same dialog as the foreground permission.
 * From API 30 the background grant has no dialog at all — the user has to pick it in system
 * settings — so the row offers a shortcut there when it is still missing.
 */
@Composable
private fun AtWorkSetting(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var backgroundMissing by remember { mutableStateOf(false) }
    var showDisclosure by remember { mutableStateOf(false) }

    fun hasBackground(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        backgroundMissing = !granted
        onEnabledChange(true)
    }

    val foregroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.any { it }
        if (!granted) {
            onEnabledChange(false)
            return@rememberLauncherForActivityResult
        }
        if (hasBackground()) {
            backgroundMissing = false
            onEnabledChange(true)
        } else {
            backgroundLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_only_at_work)) },
        supportingContent = {
            Text(
                if (enabled && backgroundMissing) {
                    stringResource(R.string.settings_only_at_work_needs_background)
                } else {
                    stringResource(R.string.settings_only_at_work_summary)
                },
            )
        },
        trailingContent = {
            Switch(
                checked = enabled,
                onCheckedChange = { wantsEnabled ->
                    if (!wantsEnabled) {
                        backgroundMissing = false
                        onEnabledChange(false)
                    } else {
                        showDisclosure = true
                    }
                },
            )
        },
    )

    if (enabled && backgroundMissing) {
        TextButton(
            onClick = { context.openAppSettings() },
            modifier = Modifier.padding(horizontal = 16.dp),
        ) { Text(stringResource(R.string.settings_notifications_open)) }
    }

    // Play requires this disclosure to precede the system permission dialog, and to be
    // dismissible without granting anything.
    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = { showDisclosure = false },
            title = { Text(stringResource(R.string.location_disclosure_title)) },
            text = { Text(stringResource(R.string.location_disclosure_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisclosure = false
                        foregroundLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                ) { Text(stringResource(R.string.location_disclosure_accept)) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDisclosure = false },
                ) { Text(stringResource(R.string.location_disclosure_decline)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceRow(
    title: String,
    summary: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        summary?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun SeedSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color, CircleShape)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.nav_back)) } },
        text = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TimePicker(state = state)
            }
        },
    )
}

private fun android.content.Context.openNotificationSettings() {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
    runCatching { with(ShareActions) { launch(intent) } }
}

private fun android.content.Context.openWebApp() {
    val intent = Intent(Intent.ACTION_VIEW, "https://mensa.davidemarcoli.dev".toUri())
    runCatching { with(ShareActions) { launch(intent) } }
}

private fun android.content.Context.openAppSettings() {
    val intent = Intent(
        AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null),
    )
    runCatching { with(ShareActions) { launch(intent) } }
}
