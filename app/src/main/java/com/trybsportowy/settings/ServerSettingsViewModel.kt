package com.trybsportowy.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trybsportowy.sync.api.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Result of the "Testuj połączenie" action; rendered as a localized row. */
sealed interface ConnectionTestState {
    data object Idle : ConnectionTestState
    data object Testing : ConnectionTestState
    data class Ok(val version: String) : ConnectionTestState
    data class Failed(val reason: String) : ConnectionTestState
}

class ServerSettingsViewModel(private val secrets: SecretsStore) : ViewModel() {

    private val _serverUrl = MutableStateFlow(secrets.serverUrlOrDefault())
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _hasSavedSecret = MutableStateFlow(secrets.hasSecret())
    val hasSavedSecret: StateFlow<Boolean> = _hasSavedSecret.asStateFlow()

    private val _secretInvalid = MutableStateFlow(secrets.isSecretInvalid())
    val secretInvalid: StateFlow<Boolean> = _secretInvalid.asStateFlow()

    private val _testState = MutableStateFlow<ConnectionTestState>(ConnectionTestState.Idle)
    val testState: StateFlow<ConnectionTestState> = _testState.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    fun onServerUrlChange(value: String) {
        _serverUrl.value = value
        _saved.value = false
    }

    /**
     * Persists URL and (only if the user typed a new one) the secret, then
     * clears the invalid flag (§8.3). Empty secret input means "keep current".
     */
    fun save(secretInput: String) {
        val url = _serverUrl.value.trim()
        if (url.isNotEmpty()) secrets.saveServerUrl(url)
        if (secretInput.isNotBlank()) secrets.saveSecret(secretInput.trim())
        secrets.clearSecretInvalid()
        _hasSavedSecret.value = secrets.hasSecret()
        _secretInvalid.value = false
        _saved.value = true
    }

    /**
     * Tests connectivity against GET /api/android/health using the CURRENT
     * (possibly un-saved) URL input, so the user can validate before saving.
     */
    fun testConnection() {
        _testState.value = ConnectionTestState.Testing
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NetworkModule.create(_serverUrl.value).health()
                }
            }
            _testState.value = result.fold(
                onSuccess = { ConnectionTestState.Ok(it.version.ifBlank { it.status }) },
                onFailure = { ConnectionTestState.Failed(it.message ?: it.javaClass.simpleName) }
            )
        }
    }
}
