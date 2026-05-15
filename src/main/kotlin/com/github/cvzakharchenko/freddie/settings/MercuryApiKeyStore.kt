package com.github.cvzakharchenko.freddie.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

object MercuryApiKeyStore {
    private const val SUBSYSTEM = "Freddie Mercury"
    private const val KEY = "Inception API Key"
    private const val ENV_VAR = "INCEPTION_API_KEY"

    private fun attributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName(SUBSYSTEM, KEY))

    fun getStoredApiKey(): String? =
        PasswordSafe.instance.getPassword(attributes())?.takeIf { it.isNotBlank() }

    fun getApiKeyOrEnv(): String? =
        getStoredApiKey() ?: System.getenv(ENV_VAR)?.takeIf { it.isNotBlank() }

    fun describeApiKeySource(): String =
        when {
            getStoredApiKey() != null -> "PasswordSafe"
            System.getenv(ENV_VAR)?.isNotBlank() == true -> ENV_VAR
            else -> "missing"
        }

    fun setApiKey(apiKey: String) {
        val normalized = apiKey.trim()
        val credentials = normalized.takeIf { it.isNotEmpty() }?.let { Credentials(KEY, it) }
        PasswordSafe.instance.set(attributes(), credentials)
    }
}
