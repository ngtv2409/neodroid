package com.ngtv.neodroid.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.ngtv.neodroid.R

@Composable
fun neodroidTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = "Back" },
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_arrow_back),
            contentDescription = null,
        )
    }
}

/**
 * Standard app bar: primary-container colors with an optional back button.
 *
 * Use [centered] for secondary panes that show a compact centered title.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeodroidTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    centered: Boolean = false,
    colors: TopAppBarColors = neodroidTopBarColors(),
    actions: @Composable RowScope.() -> Unit = {},
) {
    val navigationIcon: (@Composable () -> Unit)? = if (onBack != null) {
        { BackButton(onBack) }
    } else {
        null
    }

    if (centered) {
        CenterAlignedTopAppBar(
            modifier = modifier,
            colors = colors,
            navigationIcon = navigationIcon ?: {},
            title = {
                Text(
                    text = title,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleMedium,
                )
            },
            actions = actions,
        )
    } else {
        TopAppBar(
            modifier = modifier,
            colors = colors,
            navigationIcon = navigationIcon ?: {},
            title = { Text(title) },
            actions = actions,
        )
    }
}
