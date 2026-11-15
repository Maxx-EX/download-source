package com.downloadsource.utils

import java.security.MessageDigest

object PasswordCrypto {
    // SHA-256 单向加密，不保存明文
    fun hashPassword(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(password.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPassword(input: String, storedHash: String?): Boolean {
        if (storedHash == null) return false
        return hashPassword(input) == storedHash
    }
}
