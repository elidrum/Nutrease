package com.example.nutrease.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.ui.theme.NutreaseTheme

/**
 * Schermata di login (RF3). Wrapper "connesso": osserva il ViewModel e gestisce la
 * navigazione post-login in [LaunchedEffect]; il layout vero è nel content stateless
 * sottostante, così la @Preview funziona senza Hilt né rete.
 */
@Composable
fun LoginScreen(
    onNavigateToPatientHome: () -> Unit,
    onNavigateToSpecialistHome: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToResetPassword: (email: String) -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigateTo) {
        when (uiState.navigateTo) {
            UserRole.PATIENT -> onNavigateToPatientHome()
            UserRole.SPECIALIST, UserRole.SECRETARY -> onNavigateToSpecialistHome()
            null -> Unit
        }
    }

    LoginContent(
        uiState = uiState,
        onLogin = viewModel::login,
        onErrorShown = viewModel::clearError,
        onNavigateToRegister = onNavigateToRegister,
        onSendReset = viewModel::sendPasswordReset,
        onConsumeResetSent = viewModel::consumePasswordResetSent,
        onNavigateToResetPassword = onNavigateToResetPassword
    )
}

// Falso positivo dell'IDE: showForgotDialog è riassegnato nelle callback del dialog e riletto
// alla ricomposizione (snapshot state), non visibile all'analisi statica (come altri screen).
@Suppress("AssignedValueIsNeverRead")
@Composable
private fun LoginContent(
    uiState: LoginUiState,
    onLogin: (email: String, password: String) -> Unit,
    onErrorShown: () -> Unit,
    onNavigateToRegister: () -> Unit = {},
    onSendReset: (String) -> Unit = {},
    onConsumeResetSent: () -> Unit = {},
    onNavigateToResetPassword: (email: String) -> Unit = {}
) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showForgotDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState.passwordResetSent) {
        if (uiState.passwordResetSent) {
            showForgotDialog = false
            onNavigateToResetPassword(uiState.resetEmail.orEmpty())
            onConsumeResetSent()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = stringResource(R.string.login_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    onErrorShown()
                },
                label = { Text(stringResource(R.string.common_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                isError = uiState.error != null,
                enabled = !uiState.isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    onErrorShown()
                },
                label = { Text(stringResource(R.string.common_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        onLogin(email, password)
                    }
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.Visibility
                                          else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) stringResource(R.string.login_hide_password)
                                                 else stringResource(R.string.login_show_password)
                        )
                    }
                },
                isError = uiState.error != null,
                enabled = !uiState.isLoading
            )

            if (uiState.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Button(
                        onClick = {
                            focusManager.clearFocus()
                            onLogin(email, password)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = email.isNotBlank() && password.isNotBlank()
                    ) {
                        Text(
                            text = stringResource(R.string.login_submit),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            TextButton(
                onClick = onNavigateToRegister,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !uiState.isLoading
            ) {
                Text(stringResource(R.string.login_go_to_register))
            }

            TextButton(
                onClick = { showForgotDialog = true },
                modifier = Modifier.align(Alignment.CenterHorizontally),
                enabled = !uiState.isLoading
            ) {
                Text(stringResource(R.string.login_forgot_password))
            }
        }
    }

    if (showForgotDialog) {
        ForgotPasswordDialog(
            initialEmail = email,
            isSending = uiState.isSendingReset,
            onSend = onSendReset,
            onDismiss = { showForgotDialog = false }
        )
    }

}

@Composable
private fun ForgotPasswordDialog(
    initialEmail: String,
    isSending: Boolean,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var email by remember { mutableStateOf(initialEmail) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.login_reset_title)) },
        text = {
            Column {
                Text(stringResource(R.string.login_reset_prompt))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.common_email)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isSending
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSend(email) },
                enabled = email.contains("@") && !isSending
            ) { Text(stringResource(R.string.common_send)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSending) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenPreview() {
    NutreaseTheme {
        LoginContent(
            uiState = LoginUiState(),
            onLogin = { _, _ -> },
            onErrorShown = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LoginScreenErrorPreview() {
    NutreaseTheme {
        LoginContent(
            uiState = LoginUiState(error = "Email o password errati"),
            onLogin = { _, _ -> },
            onErrorShown = {}
        )
    }
}