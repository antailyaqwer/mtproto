package com.attafitamim.mtproto.security.utils

import kotlin.random.Random.Default.nextBytes

object CryptoUtils {

    fun concat(vararg v: ByteArray): ByteArray {
        val totalLength = v.sumOf {  byteArray ->
            byteArray.size
        }

        val res = ByteArray(totalLength)
        var offset = 0

        for (i in v.indices) {
            v[i].copyInto(res, offset)
            offset += v[i].size
        }

        return res
    }

    fun subArray(source: ByteArray, start: Int, size: Int): ByteArray {
        val end = start + size
        return source.sliceArray(start ..< end)
    }

    /**
     * Adds padding
     */
    fun align(src: ByteArray, factor: Int): ByteArray {
        if (src.size % factor == 0) {
            return src
        }
        val padding = factor - src.size % factor
        return concat(src, nextBytes(padding))
    }

    fun xor(a: ByteArray, b: ByteArray): ByteArray {
        val res = ByteArray(a.size)
        for (i in a.indices) {
            res[i] = (a[i].toInt() xor b[i].toInt()).toByte()
        }
        return res
    }
}