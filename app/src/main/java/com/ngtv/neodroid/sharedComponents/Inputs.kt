package com.ngtv.neodroid.sharedComponents

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun ValidatedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    validator: (String) -> String? = { null },
    modifier: Modifier = Modifier,
    maxLength: Int? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
) {
    var hasBeenFocused by rememberSaveable { mutableStateOf(false) }
    var touched by rememberSaveable { mutableStateOf(false) }

    val error = validator(value)
    val showError = error != null && touched

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (maxLength == null || newValue.length <= maxLength) {
                onValueChange(newValue)
            }
        },
        label = { Text(label) },
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (focusState.isFocused) {
                    hasBeenFocused = true
                } else if (hasBeenFocused) {
                    touched = true
                }
            },
        singleLine = singleLine,
        enabled = enabled,
        isError = showError,
        supportingText = {
            when {
                showError -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                maxLength != null -> {
                    Text("${value.length}/$maxLength characters")
                }
            }
        },
    )
}
