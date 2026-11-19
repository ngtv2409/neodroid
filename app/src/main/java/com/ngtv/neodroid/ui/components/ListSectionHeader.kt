package com.ngtv.neodroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.ngtv.neodroid.ui.theme.Spacing

@Composable
fun ListSectionHeader(
    title: String,
    count: Int = 0,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = Spacing.lg,
                end = Spacing.sm,
                top = Spacing.lg,
                bottom = Spacing.xxs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleSmall,
        )

        if (count > 0) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        Spacer(Modifier.weight(1f))

        if (actionLabel != null) {
            Text(
                text = actionLabel,
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = Spacing.sm),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
