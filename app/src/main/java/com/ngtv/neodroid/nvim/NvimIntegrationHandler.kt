package com.ngtv.neodroid.nvim

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.ngtv.neodroid.core.NeovimClient
import com.ngtv.neodroid.core.RpcReply
import com.ngtv.neodroid.core.RpcRequest
import com.ngtv.neodroid.core.RpcRequestHandler
import com.ngtv.neodroid.settings.Settings
import com.ngtv.neodroid.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Handles client requests from Neovim
 *
 * Clipboard is denied unless allowRemoteClipboard is set
 */
object NvimIntegrationHandler : RpcRequestHandler {

    private lateinit var context: Context
    private lateinit var settings: SettingsRepository

    fun initialize(context: Context, settings: SettingsRepository) {
        this.context = context.applicationContext
        this.settings = settings
    }

    fun attach(client: NeovimClient) {
        client.setRequestHandler(this)
    }

    private val clipboard: ClipboardManager?
        get() = context.getSystemService(ClipboardManager::class.java)

    override suspend fun handle(request: RpcRequest): RpcReply = when (request.method) {
        METHOD_GET -> withClipboardAccess { getClipboard() }

        METHOD_SET -> withClipboardAccess { setClipboard(request.params) }

        else -> RpcReply.Error(
            ERROR_TYPE,
            "unsupported client request: ${request.method}",
        )
    }

    private suspend fun withClipboardAccess(request: suspend () -> RpcReply): RpcReply {
        val allowed = settings.getAsFlow(Settings.allowRemoteClipboard).first()
        if (!allowed) {
            return RpcReply.Error(
                ERROR_TYPE,
                "Remote clipboard access is disabled in Neodroid settings",
            )
        }

        return request()
    }

    private suspend fun getClipboard(): RpcReply = try {
        withContext(Dispatchers.Main.immediate) {
            val manager = clipboard
                ?: return@withContext RpcReply.Error(
                    ERROR_TYPE,
                    "Clipboard service is unavailable",
                )
            val clip = manager.primaryClip
            val text = if (clip == null || clip.itemCount == 0) {
                ""
            } else {
                clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
            }
            val (lines, regtype) = decodeClipboardText(text)
            RpcReply.Result(
                mapOf(
                    "lines" to lines,
                    "regtype" to regtype,
                ),
            )
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        RpcReply.Error(
            ERROR_TYPE,
            "Clipboard read failed: ${e.message ?: e.toString()}",
        )
    }

    private suspend fun setClipboard(params: List<Any?>): RpcReply {
        val payload = params.firstOrNull() as? Map<*, *>
            ?: return RpcReply.Error(
                ERROR_TYPE,
                "neodroid_clipboard_set expects a table with lines and regtype",
            )
        val rawLines = payload["lines"] as? List<*>
            ?: return RpcReply.Error(
                ERROR_TYPE,
                "neodroid_clipboard_set is missing the lines field",
            )
        if (rawLines.any { it !is String }) {
            return RpcReply.Error(
                ERROR_TYPE,
                "neodroid_clipboard_set lines must be a list of strings",
            )
        }

        val lines = rawLines.filterIsInstance<String>()
        val regtype = (payload["regtype"] as? String)?.takeIf { it.isNotEmpty() } ?: "v"
        val text = encodeClipboardText(lines, regtype)

        return try {
            withContext(Dispatchers.Main.immediate) {
                val manager = clipboard
                    ?: return@withContext RpcReply.Error(
                        ERROR_TYPE,
                        "Clipboard service is unavailable",
                    )
                manager.setPrimaryClip(
                    ClipData.newPlainText(CLIP_LABEL, text),
                )
                RpcReply.Result(null)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            RpcReply.Error(
                ERROR_TYPE,
                "Clipboard write failed: ${e.message ?: e.toString()}",
            )
        }
    }

    private fun decodeClipboardText(text: String): Pair<List<String>, String> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isEmpty()) return listOf("") to "v"

        return if (normalized.endsWith("\n")) {
            normalized.dropLast(1).split('\n') to "V"
        } else {
            normalized.split('\n') to "v"
        }
    }

    private fun encodeClipboardText(lines: List<String>, regtype: String): String {
        val text = lines.joinToString("\n")
        return if (regtype == "V" && !text.endsWith("\n")) "$text\n" else text
    }

    private const val METHOD_GET = "neodroid_clipboard_get"
    private const val METHOD_SET = "neodroid_clipboard_set"
    private const val ERROR_TYPE = 0L
    private const val CLIP_LABEL = "Neodroid"
}
