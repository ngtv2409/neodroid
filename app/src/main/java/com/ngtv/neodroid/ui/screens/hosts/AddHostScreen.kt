package com.ngtv.neodroid.ui.screens.hosts

import androidx.compose.runtime.Composable
import com.ngtv.neodroid.database.AuthKeyEntity
import com.ngtv.neodroid.database.SSHHostViewModel

@Composable
fun AddHostScreen(
    onSubmit: (SSHHostViewModel.InputModel) -> Unit,
    back: () -> Unit,
    authKeys: List<AuthKeyEntity> = emptyList(),
) {
    HostEditorScreen(
        host = null,
        onBack = back,
        onSubmit = { input ->
            onSubmit(input)
            back()
        },
        authKeys = authKeys,
    )
}
