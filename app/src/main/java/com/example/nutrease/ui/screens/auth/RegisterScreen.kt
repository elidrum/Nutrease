package com.example.nutrease.ui.screens.auth

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nutrease.R
import com.example.nutrease.domain.model.AgePolicy
import com.example.nutrease.domain.model.Gender
import com.example.nutrease.domain.model.PasswordPolicy
import com.example.nutrease.domain.model.SpecializationType
import com.example.nutrease.domain.model.UserRole
import com.example.nutrease.ui.components.specializationLabel
import com.example.nutrease.ui.theme.NutreaseTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

private enum class RoleTab { PATIENT, SPECIALIST }

/**
 * Schermata di registrazione (RF1/RF2) con due tab (paziente/specialista) e campi
 * dedicati per ruolo. La data di nascita è un campo a sole cifre mascherato in
 * `gg/MM/aaaa` da [DateMaskTransformation]; l'abilitazione del bottone è calcolata
 * in locale da `isFormValid`, le regole di dominio rivivono nel ViewModel.
 */
@Composable
fun RegisterScreen(
    onNavigateToPatientHome: () -> Unit,
    onNavigateToSpecialistHome: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: RegisterViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.navigateTo) {
        when (uiState.navigateTo) {
            UserRole.PATIENT -> onNavigateToPatientHome()
            UserRole.SPECIALIST, UserRole.SECRETARY -> onNavigateToSpecialistHome()
            null -> Unit
        }
    }

    RegisterContent(
        uiState = uiState,
        onRegisterPatient = viewModel::registerPatient,
        onRegisterSpecialist = viewModel::registerSpecialist,
        onErrorShown = viewModel::clearError,
        onNavigateBack = onNavigateBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegisterContent(
    uiState: RegisterUiState,
    onRegisterPatient: (String, String, String, String, String, Gender, LocalDate) -> Unit,
    onRegisterSpecialist: (String, String, String, String, String, String, SpecializationType, String) -> Unit,
    onErrorShown: () -> Unit,
    onNavigateBack: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableStateOf(RoleTab.PATIENT) }
    var firstName by rememberSaveable { mutableStateOf("") }
    var lastName by rememberSaveable { mutableStateOf("") }
    var taxCode by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    var selectedGender by rememberSaveable { mutableStateOf(Gender.M) }
    var birthDateText by rememberSaveable { mutableStateOf("") }

    var vatNumber by rememberSaveable { mutableStateOf("") }
    var specialization by rememberSaveable { mutableStateOf<SpecializationType?>(null) }
    var city by rememberSaveable { mutableStateOf("") }

    var passwordMismatch by remember { mutableStateOf(false) }

    // Validazione età (solo paziente): data parsata + esito della regola di dominio.
    // Stesso pattern di PasswordPolicy, riusato sia qui sia nel ViewModel.
    val today = remember { Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date }
    val parsedBirthDate = parseBirthDate(birthDateText)
    val birthDateAgeError = parsedBirthDate?.let { AgePolicy.validate(it, today) }

    val clearError = {
        onErrorShown()
        passwordMismatch = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = com.example.nutrease.ui.theme.nutreaseTopBarColors(),
                title = { Text(stringResource(R.string.register_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.register_role_label),
                style = MaterialTheme.typography.titleMedium
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedTab == RoleTab.PATIENT,
                    onClick = { selectedTab = RoleTab.PATIENT },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text(stringResource(R.string.register_role_patient)) }
                SegmentedButton(
                    selected = selectedTab == RoleTab.SPECIALIST,
                    onClick = { selectedTab = RoleTab.SPECIALIST },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text(stringResource(R.string.register_role_specialist)) }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it; clearError() },
                    label = { Text(stringResource(R.string.field_first_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    enabled = !uiState.isLoading
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it; clearError() },
                    label = { Text(stringResource(R.string.field_last_name)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    enabled = !uiState.isLoading
                )
            }

            OutlinedTextField(
                value = taxCode,
                onValueChange = { if (it.length <= 16) { taxCode = it.uppercase(); clearError() } },
                label = { Text(stringResource(R.string.field_tax_code)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Next
                ),
                supportingText = { Text("${taxCode.length}/16") },
                isError = taxCode.isNotEmpty() && taxCode.length != 16,
                enabled = !uiState.isLoading
            )

            if (selectedTab == RoleTab.PATIENT) {
                Text(
                    text = stringResource(R.string.field_sex),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val genders = listOf(
                    Gender.M to stringResource(R.string.register_gender_m),
                    Gender.F to stringResource(R.string.register_gender_f),
                    Gender.OTHER to stringResource(R.string.register_gender_other)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    genders.forEachIndexed { index, (gender, label) ->
                        SegmentedButton(
                            selected = selectedGender == gender,
                            onClick = { selectedGender = gender },
                            shape = SegmentedButtonDefaults.itemShape(index, genders.size)
                        ) { Text(label) }
                    }
                }

                val birthDateFormatInvalid = birthDateText.length == 8 && parsedBirthDate == null
                OutlinedTextField(
                    value = birthDateText,
                    // L'utente digita solo cifre (max 8 = ddMMyyyy); le "/" sono inserite
                    // dalla VisualTransformation, lo stato resta a sole cifre.
                    onValueChange = { input ->
                        birthDateText = input.filter { it.isDigit() }.take(8)
                        clearError()
                    },
                    label = { Text(stringResource(R.string.field_birth_date)) },
                    placeholder = { Text(stringResource(R.string.register_birth_date_placeholder)) },
                    visualTransformation = DateMaskTransformation,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    isError = birthDateFormatInvalid || birthDateAgeError != null,
                    supportingText = {
                        when {
                            birthDateFormatInvalid ->
                                Text(stringResource(R.string.register_birth_date_invalid))
                            birthDateAgeError != null ->
                                Text(birthDateAgeError.message)
                        }
                    },
                    enabled = !uiState.isLoading
                )
            }

            if (selectedTab == RoleTab.SPECIALIST) {
                OutlinedTextField(
                    value = vatNumber,
                    onValueChange = { if (it.length <= 11) { vatNumber = it; clearError() } },
                    label = { Text(stringResource(R.string.field_vat)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    supportingText = { Text("${vatNumber.length}/11") },
                    isError = vatNumber.isNotEmpty() && vatNumber.length != 11,
                    enabled = !uiState.isLoading
                )

                SpecializationDropdown(
                    selected = specialization,
                    onSelect = { specialization = it; clearError() },
                    enabled = !uiState.isLoading
                )

                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it; clearError() },
                    label = { Text(stringResource(R.string.register_specialist_city_label)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    enabled = !uiState.isLoading
                )
            }

            OutlinedTextField(
                value = email,
                onValueChange = { email = it; clearError() },
                label = { Text(stringResource(R.string.common_email)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                ),
                isError = uiState.error != null,
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = password,
                onValueChange = { password = it; clearError() },
                label = { Text(stringResource(R.string.common_password)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (passwordVisible) stringResource(R.string.login_hide_password)
                                                 else stringResource(R.string.login_show_password)
                        )
                    }
                },
                supportingText = { Text(stringResource(R.string.password_helper)) },
                isError = uiState.error != null || passwordMismatch,
                enabled = !uiState.isLoading
            )

            OutlinedTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it; clearError() },
                label = { Text(stringResource(R.string.password_confirm)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = if (confirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = { confirmPasswordVisible = !confirmPasswordVisible }) {
                        Icon(
                            if (confirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            contentDescription = if (confirmPasswordVisible) stringResource(R.string.login_hide_password)
                                                 else stringResource(R.string.login_show_password)
                        )
                    }
                },
                isError = passwordMismatch,
                enabled = !uiState.isLoading
            )

            if (passwordMismatch) {
                Text(
                    text = stringResource(R.string.password_mismatch),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(48.dp)
                        .align(Alignment.CenterHorizontally)
                )
            } else {
                Button(
                    onClick = {
                        if (password != confirmPassword) {
                            passwordMismatch = true
                            return@Button
                        }
                        if (selectedTab == RoleTab.PATIENT) {
                            val birthDate = parseBirthDate(birthDateText)
                            if (birthDate == null) {
                                onErrorShown()
                                return@Button
                            }
                            onRegisterPatient(
                                email, password, firstName, lastName,
                                taxCode, selectedGender, birthDate
                            )
                        } else {
                            val sel = specialization ?: return@Button
                            onRegisterSpecialist(
                                email, password, firstName, lastName,
                                taxCode, vatNumber, sel, city
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = isFormValid(
                        selectedTab, firstName, lastName, taxCode,
                        email, password, confirmPassword,
                        birthDateText, vatNumber, specialization, city
                    ) && (selectedTab != RoleTab.PATIENT || birthDateAgeError == null)
                ) {
                    Text(stringResource(R.string.register_submit))
                }
            }

            TextButton(
                onClick = onNavigateBack,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text(stringResource(R.string.register_go_to_login))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
// Falso positivo dell'IDE: 'expanded' è riassegnato nelle callback del dropdown e riletto alla ricomposizione.
@Suppress("AssignedValueIsNeverRead")
@Composable
private fun SpecializationDropdown(
    selected: SpecializationType?,
    onSelect: (SpecializationType) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = selected?.let { specializationLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.register_specialist_specialization_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            enabled = enabled,
            isError = selected == null && enabled,
            supportingText = if (selected == null) {
                { Text(stringResource(R.string.register_specialist_specialization_required)) }
            } else null
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            SpecializationType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(specializationLabel(type)) },
                    onClick = {
                        onSelect(type)
                        expanded = false
                    }
                )
            }
        }
    }
}

/** Converte le 8 cifre `ddMMyyyy` in [LocalDate]; null se incomplete o data inesistente. */
private fun parseBirthDate(digits: String): LocalDate? {
    if (digits.length != 8) return null
    return runCatching {
        val day = digits.substring(0, 2).toInt()
        val month = digits.substring(2, 4).toInt()
        val year = digits.substring(4, 8).toInt()
        LocalDate(year, month, day)
    }.getOrNull()
}

/**
 * Maschera di input per la data di nascita: lo stato contiene solo cifre (`ddMMyyyy`) e
 * le "/" vengono inserite automaticamente in visualizzazione (`gg/MM/aaaa`). L'[OffsetMapping]
 * tiene allineato il cursore tenendo conto delle 2 barre aggiunte dopo la 2ª e la 4ª cifra.
 */
private object DateMaskTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.take(8)
        val masked = buildString {
            digits.forEachIndexed { index, c ->
                append(c)
                if (index == 1 || index == 3) append('/')
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = when {
                offset <= 1 -> offset
                offset <= 3 -> offset + 1
                else -> offset + 2
            }

            override fun transformedToOriginal(offset: Int): Int = when {
                offset <= 2 -> offset
                offset <= 5 -> offset - 1
                else -> offset - 2
            }
        }
        return TransformedText(AnnotatedString(masked), offsetMapping)
    }
}

private fun isFormValid(
    tab: RoleTab,
    firstName: String, lastName: String, taxCode: String,
    email: String, password: String, confirmPassword: String,
    birthDateText: String, vatNumber: String,
    specialization: SpecializationType?, city: String
): Boolean {
    val base = firstName.isNotBlank() && lastName.isNotBlank() &&
        taxCode.length == 16 && email.contains("@") &&
        PasswordPolicy.validate(password) == null && password == confirmPassword
    return when (tab) {
        RoleTab.PATIENT -> base && parseBirthDate(birthDateText) != null
        RoleTab.SPECIALIST -> base && vatNumber.length == 11 &&
            specialization != null && city.isNotBlank()
    }
}

@Preview(showBackground = true)
@Composable
private fun RegisterScreenPreview() {
    NutreaseTheme {
        RegisterContent(
            uiState = RegisterUiState(),
            onRegisterPatient = { _, _, _, _, _, _, _ -> },
            onRegisterSpecialist = { _, _, _, _, _, _, _, _ -> },
            onErrorShown = {},
            onNavigateBack = {}
        )
    }
}