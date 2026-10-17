package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// a reusable component to display items consistently
@Composable
fun ListItemCard(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable () -> Unit,
    onClick: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            onClick()
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        ListItem(
            headlineContent = headlineContent,
            supportingContent = supportingContent,
            leadingContent = {
                Text("⟩")
            },
            colors = ListItemDefaults.colors(
                containerColor = Color.Transparent,
            ),
        )
    }
}
