package com.example.nutrease.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.AuthUser
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.PasswordPolicy
import com.example.nutrease.domain.model.Patient
import com.example.nutrease.domain.model.Specialist
import com.example.nutrease.domain.model.UserProfile

/**
 * Schermata profilo (RF5–RF7): vista/modifica anagrafica per ruolo, più le dialog
 * per cambio password ed eliminazione account (entrambe chiedono la password:
 * la conferma vera è la re-auth fatta negli UseCase) e il logout.
 */
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) onNavigateToLogin()
    }

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccess()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    ProfileContent(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onNavigateBack = onNavigateBack,
        onSavePatient = viewModel::savePatient,
        onSaveSpecialist = viewModel::saveSpecialist,
        onChangePassword = viewModel::changePassword,
        onShowPasswordDialog = viewModel::showPasswordDialog,
        onHidePasswordDialog = viewModel::hidePasswordDialog,
        onShowDeleteDialog = viewModel::showDeleteDialog,
        onHideDeleteDialog = viewModel::hideDeleteDialog,
        onLogout = viewModel::logout,
        onDeleteAccount = viewModel::deleteAccount
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileContent(
    uiState: ProfileUiState,
    snackbarHostState: SnackbarHostState,
    onNavigateBack: () -> Unit,
    onSavePatient: (Patient) -> Unit,
    onSaveSpecialist: (Specialist) -> Unit,
    onChangePassword: (currentPassword: String, newPassword: String) -> Unit,
    onShowPasswordDialog: () -> Unit,
    onHidePasswordDialog: () -> Unit,
    onShowDeleteDialog: () -> Unit,
    onHideDeleteDialog: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: (password: String) -> Unit
) {
    var isEditing by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    if (!uiState.isLoading && uiState.profile != null) {
                        if (isEditing) {
                            // TextButton (non IconButton): "Annulla" è testo, e l'IconButton lo
                            // ritagliava nei suoi 48dp circolari (sembrava tagliato a cerchio).
                            TextButton(onClick = { isEditing = false }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                        } else {
                            IconButton(onClick = { isEditing = true }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.common_edit))
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        val profile = uiState.profile
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                profile == null -> Text(
                    text = stringResource(R.string.profile_load_error),
                    modifier = Modifier.align(Alignment.Center)
                )

                profile is UserProfile.PatientProfile -> PatientProfileForm(
                    profile = profile,
                    isEditing = isEditing,
                    isSaving = uiState.isSaving,
                    onSave = { patient ->
                        onSavePatient(patient)
                        isEditing = false
                    },
                    onChangePassword = onShowPasswordDialog,
                    onLogout = onLogout,
                    onDeleteAccount = onShowDeleteDialog
                )

                profile is UserProfile.SpecialistProfile -> SpecialistProfileForm(
                    profile = profile,
                    isEditing = isEditing,
                    isSaving = uiState.isSaving,
                    onSave = { specialist ->
                        onSaveSpecialist(specialist)
                        isEditing = false
                    },
                    onChangePassword = onShowPasswordDialog,
                    onLogout = onLogout,
                    onDeleteAccount = onShowDeleteDialog
                )
            }
        }
    }

    if (uiState.showPasswordDialog) {
        ChangePasswordDialog(
            onConfirm = onChangePassword,
            onDismiss = onHidePasswordDialog
        )
    }

    if (uiState.showDeleteDialog) {
        DeleteAccountDialog(
            onConfirm = onDeleteAccount,
            onDismiss = onHideDeleteDialog
        )
    }
}

@Composable
private fun DeleteAccountDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_account)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.profile_delete_message))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.profile_delete_confirm_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(password) },
                enabled = password.isNotBlank(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text(stringResource(R.string.common_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}

@Composable
private fun PatientProfileForm(
    profile: UserProfile.PatientProfile,
    isEditing: Boolean,
    isSaving: Boolean,
    onSave: (Patient) -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val p = profile.patient
    var firstName by rememberSaveable(p.firstName) { mutableStateOf(p.firstName) }
    var lastName by rememberSaveable(p.lastName) { mutableStateOf(p.lastName) }
    var phone by rememberSaveable(p.phone) { mutableStateOf(p.phone ?: "") }
    var street by rememberSaveable(p.street) { mutableStateOf(p.street ?: "") }
    var city by rememberSaveable(p.city) { mutableStateOf(p.city ?: "") }
    var postalCode by rememberSaveable(p.postalCode) { mutableStateOf(p.postalCode ?: "") }

    ProfileFormLayout(
        authUser = profile.authUser,
        roleLabel = stringResource(R.string.profile_role_patient),
        isEditing = isEditing,
        isSaving = isSaving,
        onSave = {
            onSave(
                p.copy(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    phone = phone.trim().ifBlank { null },
                    street = street.trim().ifBlank { null },
                    city = city.trim().ifBlank { null },
                    postalCode = postalCode.trim().ifBlank { null }
                )
            )
        },
        onChangePassword = onChangePassword,
        onLogout = onLogout,
        onDeleteAccount = onDeleteAccount
    ) {
        val full = Modifier.fillMaxWidth()
        ProfileTextField(stringResource(R.string.field_first_name), firstName, full, isEditing) { firstName = it }
        ProfileTextField(stringResource(R.string.field_last_name), lastName, full, isEditing) { lastName = it }
        ProfileTextField(stringResource(R.string.field_tax_code), p.taxCode, full, enabled = false) {}
        ProfileTextField(stringResource(R.string.common_email), profile.authUser.email, full, enabled = false) {}
        ProfileTextField(stringResource(R.string.field_phone), phone, full, isEditing) { phone = it }

        ProfileSectionLabel(stringResource(R.string.profile_residence))
        ProfileTextField(stringResource(R.string.profile_street), street, full, isEditing) { street = it }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ProfileTextField(stringResource(R.string.field_city), city, Modifier.weight(1f), isEditing) { city = it }
            ProfileTextField(stringResource(R.string.profile_postal_code), postalCode, Modifier.weight(0.5f), isEditing) { postalCode = it }
        }

        ProfileTextField(stringResource(R.string.field_sex), genderLabel(p.gender), full, enabled = false) {}
        ProfileTextField(stringResource(R.string.field_birth_date), p.birthDate.toString(), full, enabled = false) {}
    }
}

@Composable
private fun genderLabel(gender: Gender): String = when (gender) {
    Gender.M -> stringResource(R.string.gender_male)
    Gender.F -> stringResource(R.string.gender_female)
    Gender.OTHER -> stringResource(R.string.gender_other)
}

@Composable
private fun SpecialistProfileForm(
    profile: UserProfile.SpecialistProfile,
    isEditing: Boolean,
    isSaving: Boolean,
    onSave: (Specialist) -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val s = profile.specialist
    var firstName by rememberSaveable(s.firstName) { mutableStateOf(s.firstName) }
    var lastName by rememberSaveable(s.lastName) { mutableStateOf(s.lastName) }
    var phone by rememberSaveable(s.phone) { mutableStateOf(s.phone ?: "") }
    var info by rememberSaveable(s.info) { mutableStateOf(s.info ?: "") }

    ProfileFormLayout(
        authUser = profile.authUser,
        roleLabel = stringResource(R.string.profile_role_specialist),
        isEditing = isEditing,
        isSaving = isSaving,
        onSave = {
            onSave(
                s.copy(
                    firstName = firstName.trim(),
                    lastName = lastName.trim(),
                    phone = phone.trim().ifBlank { null },
                    info = info.trim().ifBlank { null }
                )
            )
        },
        onChangePassword = onChangePassword,
        onLogout = onLogout,
        onDeleteAccount = onDeleteAccount
    ) {
        val full = Modifier.fillMaxWidth()
        ProfileTextField(stringResource(R.string.field_first_name), firstName, full, isEditing) { firstName = it }
        ProfileTextField(stringResource(R.string.field_last_name), lastName, full, isEditing) { lastName = it }
        ProfileTextField(stringResource(R.string.field_tax_code), s.taxCode, full, enabled = false) {}
        ProfileTextField(stringResource(R.string.field_vat), s.vatNumber, full, enabled = false) {}
        ProfileTextField(stringResource(R.string.common_email), profile.authUser.email, full, enabled = false) {}
        ProfileTextField(stringResource(R.string.field_phone), phone, full, isEditing) { phone = it }
        ProfileTextField(stringResource(R.string.profile_info_specialization), info, full, isEditing, maxLines = 3) { info = it }
    }
}

@Composable
private fun ProfileFormLayout(
    authUser: AuthUser,
    roleLabel: String,
    isEditing: Boolean,
    isSaving: Boolean,
    onSave: () -> Unit,
    onChangePassword: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit,
    fields: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        // Avatar + nome
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Column {
                Text(
                    text = authUser.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider()

        ProfileSectionLabel(stringResource(R.string.profile_personal_data))

        fields()

        if (isEditing) {
            Spacer(Modifier.height(4.dp))
            if (isSaving) {
                CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
            } else {
                Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Save, null, Modifier.padding(end = 8.dp))
                    Text(stringResource(R.string.profile_save_changes))
                }
            }
        }

        HorizontalDivider()
        ProfileSectionLabel(stringResource(R.string.profile_security))

        OutlinedButton(onClick = onChangePassword, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_change_password))
        }

        HorizontalDivider()
        ProfileSectionLabel(stringResource(R.string.profile_account))

        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.profile_logout))
        }

        OutlinedButton(
            onClick = onDeleteAccount,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
        ) {
            Text(stringResource(R.string.profile_delete_account))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileTextField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    maxLines: Int = 1,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        enabled = enabled,
        maxLines = maxLines,
        singleLine = maxLines == 1
    )
}

@Composable
private fun ProfileSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

@Composable
private fun ChangePasswordDialog(
    onConfirm: (currentPassword: String, newPassword: String) -> Unit,
    onDismiss: () -> Unit
) {
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    val mismatch = confirmPassword.isNotEmpty() && newPassword != confirmPassword
    val newPasswordValid = PasswordPolicy.validate(newPassword) == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_change_password)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = currentPassword,
                    onValueChange = { currentPassword = it },
                    label = { Text(stringResource(R.string.profile_current_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text(stringResource(R.string.profile_new_password)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    supportingText = { Text(stringResource(R.string.password_helper)) }
                )
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text(stringResource(R.string.password_confirm)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    isError = mismatch,
                    supportingText = if (mismatch) {
                        { Text(stringResource(R.string.password_mismatch)) }
                    } else null
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(currentPassword, newPassword) },
                enabled = currentPassword.isNotBlank() && newPasswordValid && newPassword == confirmPassword
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        }
    )
}