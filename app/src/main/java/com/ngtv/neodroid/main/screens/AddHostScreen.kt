package com.ngtv.neodroid.main.screens

import androidx.compose.runtime.Composable
import com.ngtv.neodroid.database.SSHHostViewModel

@Composable
fun AddHostScreen(onSubmit: (SSHHostViewModel.InputModel) -> Unit, back: () -> Unit) {
    HostEditorScreen(
        host = null,
        onBack = back,
        onSubmit = { input ->
            onSubmit(input)
            back()
        },
    )
}
