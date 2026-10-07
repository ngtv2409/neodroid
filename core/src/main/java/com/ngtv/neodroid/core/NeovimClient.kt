package com.ngtv.neodroid.core

import java.net.Socket

class NeovimClient(
    private val host : String,
    private val port : Int
) : AutoCloseable {
    private val socket = Socket(host, port)
    private val input = socket.getInputStream()
    private val output = socket.getOutputStream() 

    private var nextMessageId = 0

    fun call(method: String, params: List<Any?>): Any? {
        val id = nextMessageId++
        println("About to do stuff with $socket")
        return null
    }

    override fun close() {
        socket.close()
    }
}
