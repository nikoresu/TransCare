package org.nikoresu.transcare.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.Appointment
import org.nikoresu.transcare.domain.model.DiaryEntry
import org.nikoresu.transcare.domain.model.ExamResult
import org.nikoresu.transcare.domain.model.MedicalProfile
import org.nikoresu.transcare.domain.model.Medication
import org.nikoresu.transcare.domain.model.MedicationDose
import org.nikoresu.transcare.domain.model.Profile
import org.nikoresu.transcare.settings.AppLanguage
import org.nikoresu.transcare.settings.LocalePreferences
import org.nikoresu.transcare.ui.backup.BackupSection
import org.nikoresu.transcare.ui.security.SecuritySection

private data class StatusMessage(
    val text: String,
    val isError: Boolean,
)

/** The Profile tab is a small hub; each section opens on its own page. */
private enum class ProfileDestination {
    HOME,
    PERSONAL,
    SETTINGS,
    BACKUP,
}

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    appointments: List<Appointment>,
    results: List<ExamResult>,
    entries: List<DiaryEntry>,
    medications: List<Medication>,
    medicationDoses: List<MedicationDose>,
    onImport: suspend (
        List<Appointment>,
        List<ExamResult>,
        List<DiaryEntry>,
        List<Medication>,
        List<MedicationDose>,
    ) -> Unit,
    modifier: Modifier = Modifier,
    onLanguageChanged: () -> Unit = {},
    onLockEnabledChanged: (Boolean) -> Unit = {},
) {
    var destination by rememberSaveable { mutableStateOf(ProfileDestination.HOME) }

    when (destination) {
        ProfileDestination.HOME -> ProfileHome(
            viewModel = viewModel,
            onOpenPersonal = { destination = ProfileDestination.PERSONAL },
            onOpenSettings = { destination = ProfileDestination.SETTINGS },
            onOpenBackup = { destination = ProfileDestination.BACKUP },
            modifier = modifier,
        )

        ProfileDestination.PERSONAL -> PersonalInformationScreen(
            viewModel = viewModel,
            onBack = { destination = ProfileDestination.HOME },
            modifier = modifier,
        )

        ProfileDestination.SETTINGS -> SettingsScreen(
            onBack = { destination = ProfileDestination.HOME },
            onLanguageChanged = onLanguageChanged,
            onLockEnabledChanged = onLockEnabledChanged,
            modifier = modifier,
        )

        ProfileDestination.BACKUP -> BackupScreen(
            appointments = appointments,
            results = results,
            entries = entries,
            medications = medications,
            medicationDoses = medicationDoses,
            onImport = onImport,
            onBack = { destination = ProfileDestination.HOME },
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileHome(
    viewModel: ProfileViewModel,
    onOpenPersonal: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenBackup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(title = { Text(text = stringResource(R.string.profile_title)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    ProfileAvatar(profile = profile, size = 72.dp)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = profile.name.ifBlank {
                                stringResource(R.string.profile_default_name)
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = profile.email.ifBlank {
                                stringResource(R.string.profile_default_email)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            ProfileMenuRow(
                icon = "👤",
                title = stringResource(R.string.profile_personal_information),
                summary = stringResource(R.string.profile_personal_summary),
                onClick = onOpenPersonal,
            )
            ProfileMenuRow(
                icon = "⚙️",
                title = stringResource(R.string.profile_settings_title),
                summary = stringResource(R.string.profile_settings_summary),
                onClick = onOpenSettings,
            )
            ProfileMenuRow(
                icon = "💾",
                title = stringResource(R.string.backup_title),
                summary = stringResource(R.string.profile_backup_summary),
                onClick = onOpenBackup,
            )

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ProfileMenuRow(
    icon: String,
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = icon,
                style = MaterialTheme.typography.titleLarge,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "›",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonalInformationScreen(
    viewModel: ProfileViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val resources = LocalResources.current

    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("") }
    var medicalProfile by rememberSaveable { mutableStateOf<MedicalProfile?>(null) }
    var photoFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isPhotoBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<StatusMessage?>(null) }

    val parsedAge = ageText.trim().toIntOrNull()
    val ageError = ageText.isNotBlank() &&
        (parsedAge == null || !Profile.isValidAge(parsedAge))

    fun draft(): Profile = Profile(
        name = name,
        email = email,
        photoFileName = photoFileName,
        age = ageText.trim().toIntOrNull()?.takeIf { Profile.isValidAge(it) },
        medicalProfile = medicalProfile,
    )

    // Fill the fields once, so later profile updates (for example a new photo)
    // never overwrite text the user is still editing.
    LaunchedEffect(isLoaded, profile) {
        if (isLoaded && !initialized) {
            name = profile.name
            email = profile.email
            ageText = profile.age?.toString().orEmpty()
            medicalProfile = profile.medicalProfile
            photoFileName = profile.photoFileName
            initialized = true
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val currentDraft = draft()
        scope.launch {
            isPhotoBusy = true
            message = null
            val saved = viewModel.setPhoto(currentDraft, uri)
            message = if (saved != null) {
                photoFileName = saved.photoFileName
                StatusMessage(text = resources.getString(R.string.profile_photo_updated), isError = false)
            } else {
                StatusMessage(
                    text = resources.getString(R.string.profile_photo_update_failed),
                    isError = true,
                )
            }
            isPhotoBusy = false
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.none { it }) {
            message = StatusMessage(
                text = resources.getString(R.string.profile_media_permission_denied),
                isError = true,
            )
        }
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.profile_personal_information)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileAvatar(
                        profile = profile.copy(
                            name = name,
                            email = email,
                            photoFileName = photoFileName,
                        ),
                    )

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = name.ifBlank { stringResource(R.string.profile_default_name) },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = email.ifBlank { stringResource(R.string.profile_default_email) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                if (needsMediaPermission(context)) {
                                    mediaPermissionLauncher.launch(mediaPermissions())
                                } else {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(
                                            ActivityResultContracts.PickVisualMedia.ImageOnly,
                                        ),
                                    )
                                }
                            },
                            enabled = isLoaded && !isSaving && !isPhotoBusy && !ageError,
                        ) {
                            if (isPhotoBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = stringResource(R.string.status_updating))
                            } else {
                                Text(
                                    text = stringResource(
                                        if (photoFileName == null) {
                                            R.string.profile_add_photo
                                        } else {
                                            R.string.profile_change_photo
                                        },
                                    ),
                                )
                            }
                        }

                        if (photoFileName != null) {
                            TextButton(
                                onClick = {
                                    val currentDraft = draft()
                                    scope.launch {
                                        isPhotoBusy = true
                                        message = null
                                        val saved = viewModel.removePhoto(currentDraft)
                                        message = if (saved != null) {
                                            photoFileName = null
                                            StatusMessage(
                                                text = resources.getString(R.string.profile_photo_removed),
                                                isError = false,
                                            )
                                        } else {
                                            StatusMessage(
                                                text = resources.getString(
                                                    R.string.profile_photo_remove_failed,
                                                ),
                                                isError = true,
                                            )
                                        }
                                        isPhotoBusy = false
                                    }
                                },
                                enabled = isLoaded && !isSaving && !isPhotoBusy && !ageError,
                            ) {
                                Text(text = stringResource(R.string.profile_remove_photo))
                            }
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.profile_personal_information),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    message = null
                },
                label = { Text(text = stringResource(R.string.profile_name)) },
                singleLine = true,
                enabled = isLoaded,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = email,
                onValueChange = {
                    email = it
                    message = null
                },
                label = { Text(text = stringResource(R.string.profile_email)) },
                singleLine = true,
                enabled = isLoaded,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = ageText,
                onValueChange = { value ->
                    ageText = value.filter { it.isDigit() }.take(3)
                    message = null
                },
                label = { Text(text = stringResource(R.string.profile_age)) },
                singleLine = true,
                enabled = isLoaded,
                isError = ageError,
                supportingText = if (ageError) {
                    {
                        Text(
                            text = stringResource(
                                R.string.profile_age_invalid,
                                Profile.MIN_AGE,
                                Profile.MAX_AGE,
                            ),
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.profile_medical_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )

            MedicalProfileOptions(
                selected = medicalProfile,
                onSelect = {
                    medicalProfile = it
                    message = null
                },
                enabled = isLoaded,
            )

            Text(
                text = stringResource(R.string.profile_medical_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                onClick = {
                    val currentDraft = draft()
                    scope.launch {
                        isSaving = true
                        message = null
                        val saved = viewModel.save(currentDraft)
                        message = if (saved) {
                            StatusMessage(
                                text = resources.getString(R.string.profile_saved),
                                isError = false,
                            )
                        } else {
                            StatusMessage(
                                text = resources.getString(R.string.profile_save_failed),
                                isError = true,
                            )
                        }
                        isSaving = false
                    }
                },
                enabled = isLoaded && !isSaving && !isPhotoBusy && !ageError,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.status_saving))
                } else {
                    Text(text = stringResource(R.string.profile_save))
                }
            }

            message?.let { current ->
                ProfileMessage(text = current.text, isError = current.isError)
            }

            Text(
                text = stringResource(R.string.profile_storage_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onLanguageChanged: () -> Unit,
    onLockEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var language by remember { mutableStateOf(LocalePreferences.current(context)) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.profile_settings_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LanguageSection(
                language = language,
                onLanguageSelected = { selected ->
                    if (selected != language) {
                        LocalePreferences.set(context, selected)
                        language = selected
                        onLanguageChanged()
                    }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            SecuritySection(onLockEnabledChanged = onLockEnabledChanged)

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BackupScreen(
    appointments: List<Appointment>,
    results: List<ExamResult>,
    entries: List<DiaryEntry>,
    medications: List<Medication>,
    medicationDoses: List<MedicationDose>,
    onImport: suspend (
        List<Appointment>,
        List<ExamResult>,
        List<DiaryEntry>,
        List<Medication>,
        List<MedicationDose>,
    ) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.backup_title)) },
                navigationIcon = { BackButton(onClick = onBack) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            BackupSection(
                appointments = appointments,
                results = results,
                entries = entries,
                medications = medications,
                medicationDoses = medicationDoses,
                onImport = onImport,
                showTitle = false,
            )

            Spacer(modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun BackButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = stringResource(R.string.action_back))
    }
}

@Composable
private fun LanguageSection(
    language: AppLanguage,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    Text(
        text = stringResource(R.string.language_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    Card(modifier = Modifier.fillMaxWidth()) {
        Column {
            LanguageOption(
                label = stringResource(R.string.language_system),
                selected = language == AppLanguage.SYSTEM,
                onSelect = { onLanguageSelected(AppLanguage.SYSTEM) },
            )
            HorizontalDivider()
            LanguageOption(
                label = stringResource(R.string.language_english),
                selected = language == AppLanguage.ENGLISH,
                onSelect = { onLanguageSelected(AppLanguage.ENGLISH) },
            )
            HorizontalDivider()
            LanguageOption(
                label = stringResource(R.string.language_spanish_latam),
                selected = language == AppLanguage.SPANISH_LATIN_AMERICA,
                onSelect = { onLanguageSelected(AppLanguage.SPANISH_LATIN_AMERICA) },
            )
        }
    }
    Text(
        text = stringResource(R.string.language_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun LanguageOption(
    label: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ProfileMessage(
    text: String,
    isError: Boolean,
) {
    Surface(
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (isError) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
