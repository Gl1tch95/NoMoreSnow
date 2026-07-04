package com.nomoresnow

import android.util.Base64

object ShadowlemonDecryptor {

    private val MAGIC = byteArrayOf(109, 118, 109, 49) // mvm1
    private val jl = intArrayOf(
        1116352408, 1899447441, 3049323471, 3921009573,
        961987163, 1508970993, 2453635748, 2870763221,
        3624381080, 310598401, 607225278, 1426881987,
        1925078388, 2162078206, 2614888103, 3248222580
    )

    fun decrypt(encrypted: String, seed: Int, tmdbId: Int): String {
        val data = Base64.decode(encrypted, Base64.DEFAULT)
        val keystream = generateKeystream(seed.toString(), tmdbId, data.size)

        for (i in data.indices) {
            data[i] = (data[i].toInt() xor keystream[i].toInt()).toByte()
        }

        if (!data.startsWith(MAGIC)) {
            throw Exception("Shadowlemon: Bad magic bytes")
        }

        return data.drop(MAGIC.size).toByteArray().decodeToString()
    }

    private fun generateKeystream(l: String, o: Int, length: Int): ByteArray {
        val state = Nf(l, o)
        val result = ByteArray(length)
        var n = 0
        var idx = 0

        while (idx < length) {
            val d = Rf(state, n++)
            result[idx++] = (d and 0xFF).toByte()
            if (idx < length) result[idx++] = ((d ushr 8) and 0xFF).toByte()
            if (idx < length) result[idx++] = ((d ushr 16) and 0xFF).toByte()
            if (idx < length) result[idx++] = ((d ushr 24) and 0xFF).toByte()
        }
        return result
    }

    // ==================== CORE HELPERS ====================

    private fun ui(l: Int): Int {
        var x = l.toUInt()
        x = x xor (x shr 16)
        x = x * 2246822507u
        x = x xor (x shr 13)
        x = x * 3266489909u
        x = x xor (x shr 16)
        return x.toInt()
    }

    private fun ps(l: Int, o: Int): Int {
        val x = l.toUInt()
        val shift = o and 31
        return if (shift == 0) x.toInt() else ((x shl shift) or (x shr (32 - shift))).toInt()
    }

    private fun wf(l: String): Int {
        var o = 2166136261
        for (c in l) {
            o = ((o.toLong() xor c.code.toLong()) * 16777619L).toInt()
        }
        return ui(o)
    }

    private fun If(l: String): Int {
        var o = 1732584193u
        for (i in l.indices) {
            o = ps((o xor (l[i].code * jl[i and 15]).toUInt()).toInt(), 5).toUInt()
        }
        return ui(o.toInt())
    }

    private fun Nf(l: String, o: Int): State {
        val e = IntArray(61)
        var i = ui(wf(l) xor ui(o xor 2654435769)).toUInt()

        for (r in 0 until 8) {
            if ((r * (r + 1) and 1) == 0) {
                val n = (i % 61u).toInt()
                i = ps((i + 2654435769u).toInt(), 7 + (r and 7)).toUInt()
                e[n] = ui((i xor ui(i.toInt())).toInt())
                i = ui((i + n.toUInt()).toInt()).toUInt()
            } else {
                e[r] = jl[r and 15]
            }
        }
        return State(e, ui((i xor 2779096485u).toInt()))
    }

    private fun Rf(state: State, o: Int): Int {
        val e = state.S
        val i = state.acc
        val r = i % 61
        val n = if (r in e.indices && e[r] != 0) 0 else -1
        val u = e[r].toUInt()
        val d = 2654435769u * (o + 1u)

        var g = ((i.toUInt() xor u xor d) or (i.toUInt() and u and n.toUInt())) and 0xFFFFFFFFu
        g = (ps(g.toInt(), r and 31).toUInt() xor ps(i, (r * 7) and 31).toUInt()) and 0xFFFFFFFFu

        state.acc = ui((g + 2654435769u).toInt())
        e[r] = state.acc
        return state.acc
    }

    private data class State(val S: IntArray, var acc: Int)

    private fun xf(l: String): ByteArray {
        val base64 = l.replace('-', '+').replace('_', '/')
            .padEnd(((l.length + 3) / 4) * 4, '=')
        return Base64.decode(base64, Base64.DEFAULT)
    }
}
