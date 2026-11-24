package com.ngtv.neodroid.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.ngtv.neodroid.R

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
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
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
        keyboardOptions = keyboardOptions,
        supportingText = {
            when {
                showError -> {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                maxLength != null -> {
                    Text(
                        text = "${value.length}/$maxLength characters",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
fun PasswordInput(
    password: String,
    onPasswordChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: String = "Password",
) {
    var passwordVisible by remember {
        mutableStateOf(false)
    }

    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        visualTransformation = if (passwordVisible) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            autoCorrectEnabled = false,
        ),
        trailingIcon = {
            IconButton(
                enabled = enabled,
                onClick = {
                    passwordVisible = !passwordVisible
                },
                modifier = Modifier.semantics {
                    contentDescription = if (passwordVisible) {
                        "Hide password"
                    } else {
                        "Show password"
                    }
                },
            ) {
                Icon(
                    painter = painterResource(
                        if (passwordVisible) {
                            R.drawable.ic_visibility_off
                        } else {
                            R.drawable.ic_visibility
                        },
                    ),
                    contentDescription = null,
                )
            }
        },
    )
}
