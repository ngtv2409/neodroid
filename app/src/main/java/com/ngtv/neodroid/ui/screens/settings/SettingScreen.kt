@file:OptIn(ExperimentalMaterial3Api::class)

package com.ngtv.neodroid.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ngtv.neodroid.R
import com.ngtv.neodroid.settings.Setting
import com.ngtv.neodroid.settings.SettingsCatalog
import com.ngtv.neodroid.settings.SettingsCategory
import com.ngtv.neodroid.settings.SettingsRepository
import com.ngtv.neodroid.settings.rememberSettingsRepository
import com.ngtv.neodroid.ui.components.CompactListItem
import com.ngtv.neodroid.ui.components.NeodroidTopBar
import com.ngtv.neodroid.ui.components.SectionLabel
import com.ngtv.neodroid.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SettingsSplitPaneThreshold = 720.dp
private val SettingsListPaneWidth = 360.dp

@Composable
fun SettingScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val repository = rememberSettingsRepository()
    val categories = SettingsCatalog.categories

    var selectedCategoryId by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isSplit = maxWidth >= SettingsSplitPaneThreshold
        val effectiveCategoryId =
            selectedCategoryId ?: categories.firstOrNull()?.id.takeIf { isSplit }
        val selectedCategory = categories.firstOrNull { it.id == effectiveCategoryId }

        if (isSplit) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                SettingsListPane(
                    categories = categories,
                    selectedCategoryId = effectiveCategoryId,
                    onSelectCategory = { selectedCategoryId = it.id },
                    onBack = onBack,
                    modifier = Modifier
                        .width(SettingsListPaneWidth)
                        .fillMaxHeight(),
                )
                VerticalDivider()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    selectedCategory?.let { category ->
                        SettingsCategoryPane(
                            category = category,
                            repository = repository,
                            onBack = null,
                        )
                    }
                }
            }
        } else {
            BackHandler(enabled = selectedCategoryId != null) {
                selectedCategoryId = null
            }

            if (selectedCategory == null) {
                SettingsListPane(
                    categories = categories,
                    selectedCategoryId = effectiveCategoryId,
                    onSelectCategory = { selectedCategoryId = it.id },
                    onBack = onBack,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                SettingsCategoryPane(
                    category = selectedCategory,
                    repository = repository,
                    onBack = { selectedCategoryId = null },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SettingsListPane(
    categories: List<SettingsCategory>,
    selectedCategoryId: String?,
    onSelectCategory: (SettingsCategory) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        NeodroidTopBar(
            title = "Settings",
            onBack = onBack,
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(
                items = categories,
                key = { category -> category.id },
            ) { category ->
                CompactListItem(
                    headlineContent = {
                        Text(
                            text = category.title,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = category.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    onClick = { onSelectCategory(category) },
                    selected = category.id == selectedCategoryId,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.md))
            }
        }
    }
}

@Composable
private fun SettingsCategoryPane(
    category: SettingsCategory,
    repository: SettingsRepository,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.background),
    ) {
        NeodroidTopBar(
            title = category.title,
            onBack = onBack,
            centered = true,
            colors = categoryTopBarColors(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

        SettingsCategoryContent(
            category = category,
            repository = repository,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsCategoryContent(
    category: SettingsCategory,
    repository: SettingsRepository,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = Spacing.xxl),
    ) {
        item(key = "intro-${category.id}") {
            Text(
                text = category.summary,
                modifier = Modifier.padding(
                    start = Spacing.lg,
                    end = Spacing.lg,
                    top = Spacing.lg,
                    bottom = Spacing.sm,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        category.sections.forEach { section ->
            item(key = "section-${category.id}-${section.title}") {
                SectionLabel(
                    text = section.title,
                    modifier = Modifier.padding(
                        start = Spacing.lg,
                        end = Spacing.lg,
                        top = Spacing.lg,
                        bottom = Spacing.xs,
                    ),
                )
            }

            items(
                items = section.settings,
                key = { setting -> "${category.id}:${setting.key.name}" },
            ) { setting ->
                SettingItem(
                    setting = setting,
                    repository = repository,
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = Spacing.lg))
            }
        }
    }
}

@Composable
private fun SettingItem(setting: Setting<*>, repository: SettingsRepository) {
    when (setting) {
        is Setting.Toggle -> ToggleSettingRow(setting, repository)
        is Setting.Choice -> ChoiceSettingRow(setting, repository)
        is Setting.Text -> TextSettingRow(setting, repository)
        is Setting.Number -> NumberSettingRow(setting, repository)
    }
}

@Composable
private fun ToggleSettingRow(setting: Setting.Toggle, repository: SettingsRepository) {
    val value by repository
        .getAsFlow(setting)
        .collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    val scope = rememberCoroutineScope()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = value,
                role = Role.Switch,
                onValueChange = { checked ->
                    scope.launch {
                        repository.setValue(setting, checked)
                    }
                },
            )
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SettingLabel(
            setting = setting,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = value,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun ChoiceSettingRow(setting: Setting.Choice, repository: SettingsRepository) {
    val value by repository
        .getAsFlow(setting)
        .collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }

    val selectedLabel = setting.options
        .firstOrNull { option -> option.value == value }
        ?.label
        ?: value

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SettingLabel(
            setting = setting,
            modifier = Modifier.weight(1f),
        )

        Box {
            OutlinedButton(
                onClick = { expanded = true },
                contentPadding = PaddingValues(
                    horizontal = Spacing.md,
                    vertical = Spacing.xs,
                ),
            ) {
                Text(
                    text = selectedLabel,
                    style = MaterialTheme.typography.labelLarge,
                )
                Icon(
                    painter = painterResource(R.drawable.ic_expand_more),
                    contentDescription = null,
                    modifier = Modifier.padding(start = Spacing.xs),
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                setting.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            expanded = false
                            scope.launch {
                                repository.setValue(setting, option.value)
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TextSettingRow(setting: Setting.Text, repository: SettingsRepository) {
    val storedValue by repository
        .getAsFlow(setting)
        .collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    var localValue by remember(setting, storedValue) { mutableStateOf(storedValue) }
    val placeholder = setting.placeholder

    LaunchedEffect(localValue, storedValue, setting) {
        if (localValue != storedValue) {
            delay(TEXT_WRITE_DEBOUNCE_MS)
            repository.setValue(setting, localValue)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
    ) {
        SettingLabel(setting)
        OutlinedTextField(
            value = localValue,
            onValueChange = { newValue ->
                if (setting.maxLength == null || newValue.length <= setting.maxLength) {
                    localValue = newValue
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Spacing.sm),
            singleLine = true,
            placeholder = if (placeholder != null) {
                { Text(placeholder) }
            } else {
                null
            },
        )
    }
}

@Composable
private fun NumberSettingRow(setting: Setting.Number, repository: SettingsRepository) {
    val storedValue by repository
        .getAsFlow(setting)
        .collectAsStateWithLifecycle(initialValue = setting.defaultValue)
    val scope = rememberCoroutineScope()
    var showDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { showDialog = true }
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        SettingLabel(
            setting = setting,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${storedValue}${setting.suffix}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showDialog) {
        NumberInputDialog(
            setting = setting,
            initialValue = storedValue,
            onDismiss = { showDialog = false },
            onConfirm = { value ->
                showDialog = false
                scope.launch {
                    repository.setValue(setting, value)
                }
            },
        )
    }
}

@Composable
private fun NumberInputDialog(
    setting: Setting.Number,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var valueText by remember(setting, initialValue) {
        mutableStateOf(initialValue.toString())
    }
    val parsedValue = valueText.toIntOrNull()
    val isValid = parsedValue != null && parsedValue in setting.range
    val rangeText = "Enter a value from ${setting.range.first} to ${setting.range.last}"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(setting.title)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                setting.description?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all(Char::isDigit)) {
                            valueText = newValue
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isValid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        Text(
                            text = rangeText,
                            color = if (isValid) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                        )
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    parsedValue?.let(onConfirm)
                },
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun SettingLabel(setting: Setting<*>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = setting.title,
            style = MaterialTheme.typography.bodyLarge,
        )
        setting.description?.let { description ->
            Text(
                text = description,
                modifier = Modifier.padding(top = Spacing.xxs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun categoryTopBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

private const val TEXT_WRITE_DEBOUNCE_MS = 350L
