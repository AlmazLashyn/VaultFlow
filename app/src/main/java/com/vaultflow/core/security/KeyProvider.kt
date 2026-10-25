package com.vaultflow.core.security

import javax.crypto.SecretKey

fun interface KeyProvider {
    fun getOrCreateKey(): SecretKey
}
