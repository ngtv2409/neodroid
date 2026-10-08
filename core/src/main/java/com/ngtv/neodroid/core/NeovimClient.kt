package com.ngtv.neodroid.core

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicInteger

import org.msgpack.core.MessagePack
import org.msgpack.core.MessagePacker


class NeovimClient(
    private val input: InputStream,
    private val output: OutputStream
) {
    private var MessageId = AtomicInteger(0)

    // generic call
    // TODO: return promise and complete with a reading coroutine
    fun call(method_name: String, params: List<Any?>) {
        val id = MessageId.getAndIncrement().toUInt()
        MessagePack.newDefaultPacker(output).use { packer ->
            packer.packRequest(id, method_name, params)
        }
    }
}
