package com.ngtv.neodroid.nvim

import android.util.Log
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.NvimNotification
import com.ngtv.neodroid.core.events.NvimUiEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.Closeable

// creates a session from a (brand new) nvim client (to own ui state)
class NvimSession private constructor(val nvim: NeovimClient) : Closeable {
    var state = NvimState()
        private set

    private val readerJob: Job = nvim.scope.launch {
        nvim.notifications.collect { notification ->
            if (notification is NvimNotification.Redraw) {
                notification.events.forEach { event ->
                    Log.v("Nvim", "Received: $event")
                    when (event) {
                        is NvimUiEvent.OptionSet -> {
                            state.options[event.name] = event.value
                        }

                        else -> {}
                    }
                }
            }
        }
    }

    companion object {
        suspend fun create(nvim: NeovimClient): NvimSession {
            val session = NvimSession(nvim)

            val attachOpts = mapOf(
                "rgb" to true,
                "ext_linegrid" to true,
            )

            nvim.ui_attach(120, 40, attachOpts)

            return session
        }
    }

    override fun close() {
        nvim.close()
    }
}
