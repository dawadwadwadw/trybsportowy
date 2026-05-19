package com.trybsportowy.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.trybsportowy.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsScreen(viewModel: ServerSettingsViewModel, onBack: () -> Unit) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val hasSavedSecret by viewModel.hasSavedSecret.collectAsState()
    val secretInvalid by viewModel.secretInvalid.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val saved by viewModel.saved.collectAsState()

    var secretInput by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = serverUrl,
                onValueChange = viewModel::onServerUrlChange,
                label = { Text(stringResource(R.string.settings_server_url_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = secretInput,
                onValueChange = { secretInput = it },
                label = { Text(stringResource(R.string.settings_secret_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (secretVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { secretVisible = !secretVisible }) {
                        Icon(
                            imageVector = if (secretVisible) {
                                Icons.Default.VisibilityOff
                            } else {
                                Icons.Default.Visibility
                            },
                            contentDescription = stringResource(
                                if (secretVisible) {
                                    R.string.settings_hide_secret
                                } else {
                                    R.string.settings_show_secret
                                }
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            if (hasSavedSecret) {
                Text(
                    stringResource(R.string.settings_secret_saved_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (secretInvalid) {
                Text(
                    stringResource(R.string.settings_secret_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Button(
                onClick = {
                    viewModel.save(secretInput)
                    secretInput = ""
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_save)) }

            if (saved) {
                Text(
                    stringResource(R.string.settings_saved),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            OutlinedButton(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.settings_test)) }

            when (val s = testState) {
                ConnectionTestState.Idle -> Unit
                ConnectionTestState.Testing -> Text(
                    stringResource(R.string.settings_testing),
                    style = MaterialTheme.typography.bodyMedium
                )
                is ConnectionTestState.Ok -> Text(
                    stringResource(R.string.settings_test_ok, s.version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                is ConnectionTestState.Failed -> Text(
                    stringResource(R.string.settings_test_failed, s.reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.settings_back))
            }
        }
    }
}
