package com.nomoresnow

import android.util.Base64

object ShadowlemonDecryptor {

    private val MAGIC = byteArrayOf(109, 118, 109, 49)

    private val jl = uintArrayOf(
        1116352408u, 1899447441u, 3049323471u, 3921009573u,
        961987163u, 1508970993u, 2453635748u, 2870763221u,
        3624381080u, 310598401u, 607225278u, 1426881987u,
        1925078388u, 2162078206u, 2614888103u, 3248222580u
    )

    private val Tf = uintArrayOf(1732584193u, 4023233417u, 2562383102u, 271733878u)

    private const val Js = 61
    private const val _f = 8
    private const val ms = 2654435769u

    fun decrypt(encrypted: String, seed: Int, tmdbId: Int): String {
        val data = xf(encrypted)
        val keystream = generateKeystream(seed.toString(), tmdbId, data.size)

        for (i in data.indices) {
            data[i] = (data[i].toInt() xor keystream[i].toInt()).toByte()
        }

        if (data.size < MAGIC.size || !data.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw Exception("Shadowlemon: Bad magic bytes")
        }

        return data.drop(MAGIC.size).toByteArray().decodeToString()
    }

    private fun xf(l: String): ByteArray {
        val normalized = l.replace('-', '+').replace('_', '/')
            .padEnd(((l.length + 3) / 4) * 4, '=')
        return Base64.decode(normalized, Base64.DEFAULT)
    }

    private fun generateKeystream(l: String, o: Int, length: Int): ByteArray {
        val state = Nf(l, o)
        val result = ByteArray(length)
        var n = 0
        var idx = 0

        while (idx < length) {
            val d = Rf(state, n++)
            result[idx++] = (d and 0xFFu).toByte()
            if (idx < length) result[idx++] = ((d shr 8) and 0xFFu).toByte()
            if (idx < length) result[idx++] = ((d shr 16) and 0xFFu).toByte()
            if (idx < length) result[idx++] = ((d shr 24) and 0xFFu).toByte()
        }
        return result
    }

    private fun ui(l: UInt): UInt {
        var x = l
        x = x xor (x shr 16)
        x = x * 2246822507u
        x = x xor (x shr 13)
        x = x * 3266489909u
        x = x xor (x shr 16)
        return x
    }

    private fun ps(l: UInt, o: Int): UInt {
        val shift = o and 31
        return if (shift == 0) l else (l shl shift) or (l shr (32 - shift))
    }

    private fun wf(l: String): UInt {
        var o = 2166136261u
        for (c in l) o = (o xor c.code.toUInt()) * 16777619u
        return ui(o)
    }

    private fun If(l: String): UInt {
        var o = Tf[0]
        for (e in l.indices) {
            o = ps(o xor (l[e].code.toUInt() * jl[e and 15]), 5)
        }
        return ui(o)
    }

    private fun Nf(l: String, o: Int): State {
        val e = IntArray(Js)
        var i = ui(wf(l) xor ui(o.toUInt() xor ms))

        for (r in 0 until _f) {
            if ((r * (r + 1) and 1) == 0) {
                val n = (i % Js.toUInt()).toInt()
                i = ps(i + ms, 7 + (r and 7))
                e[n] = ui(i xor ui(i)).toInt()
                i = ui(i + n.toUInt())
            } else {
                e[r] = jl[r and 15].toInt()
            }
        }
        return State(e, ui(i xor 2779096485u).toInt())
    }

    private fun Rf(state: State, o: Int): UInt {
        val e = state.S
        var i = state.acc.toUInt()
        val r = (i % Js.toUInt()).toInt()
        val n = if (r in e.indices && e[r] != 0) 0u else (-1).toUInt()
        val u = e[r].toUInt()
        val d = ms * (o + 1).toUInt()

        var g = (i xor u xor d) or (i and u and n)
        g = ps(g, r and 31) xor ps(i, (r * 7) and 31)

        i = ui(g + ms)
        e[r] = i.toInt()
        state.acc = i.toInt()
        return i
    }

    private data class State(val S: IntArray, var acc: Int)
}
