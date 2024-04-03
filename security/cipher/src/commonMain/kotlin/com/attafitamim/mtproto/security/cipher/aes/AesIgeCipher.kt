package com.attafitamim.mtproto.security.cipher.aes

import com.attafitamim.mtproto.security.cipher.core.CipherMode
import com.attafitamim.mtproto.security.cipher.core.ICipher
import com.attafitamim.mtproto.security.digest.core.Digest
import com.attafitamim.mtproto.security.digest.core.DigestMode
import com.attafitamim.mtproto.security.utils.CryptoUtils
import kotlin.experimental.xor

class AesIgeCipher(
    mode: CipherMode,
    private val aesKey: AesKey
) : ICipher {

    private var processedBlocks = 0
    private val engine = AESFastEngine()

    private val forEncryption = when (mode) {
        CipherMode.ENCRYPT -> true
        CipherMode.DECRYPT -> false
    }

    init {
        reset()
    }

    override fun updateData(data: ByteArray): ByteArray =
        update(data)

    override fun finalize(data: ByteArray): ByteArray {
        val encryptedData = update(data)
        reset()
        return encryptedData
    }

    private fun reset() {
        processedBlocks = 0
        engine.reset()
        engine.init(forEncryption, aesKey.key.encodedBytes)
    }

    private fun update(
        data: ByteArray
    ): ByteArray {
        val destination = ByteArray(data.size)
        if (forEncryption) {
            encrypt(data, destination, data.size)
        } else {
            decrypt(data, destination, data.size)
        }

        return destination
    }

    private fun decrypt(
        src: ByteArray,
        dest: ByteArray,
        len: Int
    ) {
        val blocksCount = len / 16
        var curIvX = aesKey.iv
        var curIvY = aesKey.iv
        var curIvXOffset = 16
        var curIvYOffset = 0

        for (i in 0 until blocksCount) {
            val offset = (processedBlocks + i) * 16

            for (j in 0..15) {
                val processedByte = src[offset + j] xor curIvX[curIvXOffset + j]
                dest[offset + j] = processedByte
            }

            engine.processBlock(dest, offset, dest, offset)
            for (j in 0..15) {
                val processedByte = dest[offset + j] xor curIvY[curIvYOffset + j]
                dest[offset + j] = processedByte
            }

            curIvY = src
            curIvYOffset = offset
            curIvX = dest
            curIvXOffset = offset
        }

        processedBlocks += blocksCount
    }

    private fun encrypt(
        src: ByteArray,
        dest: ByteArray,
        len: Int
    ) {
        val blocksCount = len / 16
        var curIvX = aesKey.iv
        var curIvY = aesKey.iv
        var curIvXOffset = 16
        var curIvYOffset = 0
        for (i in 0 until blocksCount) {
            val offset = (processedBlocks + i) * 16

            for (j in 0..15) {
                val processedByte = src[offset + j] xor curIvY[curIvYOffset + j]
                dest[offset + j] = processedByte
            }

            engine.processBlock(dest, offset, dest, offset)
            for (j in 0..15) {
                val processedByte = dest[offset + j] xor curIvX[curIvXOffset + j]
                dest[offset + j] = processedByte
            }

            curIvX = src
            curIvXOffset = offset
            curIvY = dest
            curIvYOffset = offset
        }

        processedBlocks += blocksCount
    }

    companion object {

        fun computeAesKey(
            authKey: ByteArray,
            msgKey: ByteArray,
            isOutgoing: Boolean = true
        ): AesKey {
            val x = if (isOutgoing) 0 else 8
            val a = Digest(DigestMode.SHA256).digest(msgKey, CryptoUtils.subArray(authKey, x, 36))
            val b = Digest(DigestMode.SHA256).digest(CryptoUtils.subArray(authKey, x + 40, 36), msgKey)

            val key = CryptoUtils.subArray(a, 0, 8) + CryptoUtils.subArray(
                b,
                8,
                16
            ) + CryptoUtils.subArray(a, 24, 8)
            val iv = CryptoUtils.subArray(b, 0, 8) + CryptoUtils.subArray(
                a,
                8,
                16
            ) + CryptoUtils.subArray(b, 24, 8)

            val aesSecretKey = EncodedAesSecretKey(key)
            return AesKey(
                aesSecretKey,
                iv
            )
        }
    }
}
