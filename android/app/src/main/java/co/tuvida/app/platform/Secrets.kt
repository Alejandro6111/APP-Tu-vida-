package co.tuvida.app.platform

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object Secrets {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey("tuvida-calendar", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(KeyGenParameterSpec.Builder("tuvida-calendar", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build()) }.generateKey()
    }
    fun save(context: Context, url: String) {
        val prefs = context.getSharedPreferences("secret", Context.MODE_PRIVATE)
        if (url.isBlank()) { prefs.edit().clear().apply(); return }
        require(Calendars.validGoogleUrl(url))
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).putString("value", Base64.encodeToString(cipher.doFinal(url.toByteArray()), Base64.NO_WRAP)).apply()
    }
    fun read(context: Context): String {
        val prefs = context.getSharedPreferences("secret", Context.MODE_PRIVATE)
        val value = prefs.getString("value", null) ?: return ""
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP))) }.doFinal(Base64.decode(value, Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }
}
