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

    fun decrypt(encrypted: String, seed: String, tmdbId: Int): String {
        val data = xf(encrypted)
        val keystream = generateKeystream(seed, tmdbId, data.size)

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

    private fun Sf(l: Int): Boolean {
        return ((l.toLong() * (l.toLong() + 1)) and 1L) == 0L
    }

    private fun bf(l: Int): Boolean {
        return ((l.toLong() * (l.toLong() + 1)) and 1L) == 1L
    }

    private fun Af(l: String): Array<UInt?> {
        val o = Array<UInt?>(256) { it.toUInt() }
        var e = 0
        for (i in 0 until 256) {
            val charCode = l[i % l.length].code
            val oVal = (o[i] ?: 0u).toInt()
            e = (e + oVal + charCode) and 255
            val tmp = o[i]
            o[i] = o[e]
            o[e] = tmp
        }
        return o
    }

    private fun vf(l: UInt, o: UInt, e: UInt): UInt {
        return (l xor o) or (l and o and e)
    }

    private fun Nf(l: String, o: Int): State {
        if (bf(l.length)) {
            return State(Af(l), If(l))
        }

        val e = Array<UInt?>(Js) { null }
        var i = ui(wf(l) xor ui(o.toUInt() xor ms))

        for (r in 0 until _f) {
            if (Sf(r)) {
                val n = (i % Js.toUInt()).toInt()
                i = ps(i + ms, 7 + (r and 7))
                e[n] = i xor ui(i)
                i = ui(i + n.toUInt())
            } else {
                e[r] = jl[r and 15]
            }
        }
        return State(e, ui(i xor 2779096485u))
    }

    private fun Rf(state: State, o: Int): UInt {
        val e = state.S
        var i = state.acc
        val r = (i % Js.toUInt()).toInt()
        val n = if (e[r] != null) 0xFFFFFFFFu else 0u
        val u = e[r] ?: 0u
        val d = ms * (o + 1).toUInt()

        var g = vf(i, u xor d, n)
        g = ps(g + i, r and 31) xor ps(i, (r * 7) and 31)

        i = ui(g + ms)
        e[r] = i
        state.acc = i
        return i
    }

    private data class State(val S: Array<UInt?>, var acc: UInt)
}
