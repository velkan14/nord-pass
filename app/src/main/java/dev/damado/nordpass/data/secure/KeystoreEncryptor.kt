package dev.damado.nordpass.data.secure

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Direct Android Keystore AES-256-GCM encryption. No deprecated Jetpack
 * security APIs are used. The key is not user-authentication-bound: the
 * design assumes a secure device whose unlock holder is the only user.
 */
class KeystoreEncryptor(context: Context) {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    private val alias: String = "${context.packageName}.secrets_key"
    private val tagLengthBits = 128
    private val ivLengthBytes = 12

    fun encryptString(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        val combined = ByteBuffer.allocate(iv.size + encrypted.size).put(iv).put(encrypted).array()
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    /** Returns null when the blob is malformed or the Keystore key is gone. */
    fun decryptString(encoded: String): String? {
        if (!keyStore.containsAlias(alias)) return null
        val combined = runCatching { Base64.decode(encoded, Base64.NO_WRAP) }.getOrNull() ?: return null
        if (combined.size <= ivLengthBytes) return null

        val iv = combined.copyOfRange(0, ivLengthBytes)
        val cipherText = combined.copyOfRange(ivLengthBytes, combined.size)
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getKey() ?: return null, GCMParameterSpec(tagLengthBits, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun getKey(): SecretKey? =
        runCatching { keyStore.getKey(alias, null) as? SecretKey }.getOrNull()

    private fun getOrCreateKey(): SecretKey {
        getKey()?.let { return it }
        val spec = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(spec)
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
