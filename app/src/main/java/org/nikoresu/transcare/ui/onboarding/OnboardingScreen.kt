package org.nikoresu.transcare.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.nikoresu.transcare.R
import org.nikoresu.transcare.domain.model.MedicalProfile
import org.nikoresu.transcare.domain.model.Profile
import org.nikoresu.transcare.ui.profile.MedicalProfileOptions
import org.nikoresu.transcare.ui.profile.ProfileAvatar
import org.nikoresu.transcare.ui.profile.ProfileViewModel
import org.nikoresu.transcare.ui.profile.mediaPermissions
import org.nikoresu.transcare.ui.profile.needsMediaPermission

private enum class OnboardingStep {
    WELCOME,
    PROFILE,
    MEDICAL,
    FEATURES,
}

private data class Feature(
    val icon: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
)

private val FEATURES = listOf(
    Feature(
        icon = "🗓",
        titleRes = R.string.onboarding_feature_schedule_title,
        descriptionRes = R.string.onboarding_feature_schedule_description,
    ),
    Feature(
        icon = "💊",
        titleRes = R.string.onboarding_feature_medications_title,
        descriptionRes = R.string.onboarding_feature_medications_description,
    ),
    Feature(
        icon = "🧪",
        titleRes = R.string.onboarding_feature_exams_title,
        descriptionRes = R.string.onboarding_feature_exams_description,
    ),
    Feature(
        icon = "📔",
        titleRes = R.string.onboarding_feature_diary_title,
        descriptionRes = R.string.onboarding_feature_diary_description,
    ),
    Feature(
        icon = "👤",
        titleRes = R.string.onboarding_feature_profile_title,
        descriptionRes = R.string.onboarding_feature_profile_description,
    ),
)

/**
 * First-run flow shown before the main tabs. It collects the basic profile,
 * the optional medical profile and gives a short overview of the app.
 */
@Composable
fun OnboardingScreen(
    viewModel: ProfileViewModel,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val isLoaded by viewModel.isLoaded.collectAsStateWithLifecycle()

    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var ageText by rememberSaveable { mutableStateOf("") }
    var medicalProfile by rememberSaveable { mutableStateOf<MedicalProfile?>(null) }
    var photoFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var initialized by rememberSaveable { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var isPhotoBusy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val step = OnboardingStep.entries[stepIndex.coerceIn(0, OnboardingStep.entries.lastIndex)]
    val parsedAge = ageText.trim().toIntOrNull()
    val ageError = ageText.isNotBlank() &&
        (parsedAge == null || !Profile.isValidAge(parsedAge))

    BackHandler(enabled = stepIndex > 0 && !isSaving) {
        stepIndex--
    }

    fun draft(): Profile = Profile(
        name = name,
        email = email,
        photoFileName = photoFileName,
        age = ageText.trim().toIntOrNull()?.takeIf { Profile.isValidAge(it) },
        medicalProfile = medicalProfile,
    )

    // Seed the form once the stored profile has been read.
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
        val previous = photoFileName
        scope.launch {
            isPhotoBusy = true
            message = null
            val fileName = viewModel.preparePhoto(uri, previousFileName = previous)
            if (fileName != null) {
                photoFileName = fileName
            } else {
                message = resources.getString(R.string.profile_photo_update_failed)
            }
            isPhotoBusy = false
        }
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.none { it }) {
            message = resources.getString(R.string.profile_media_permission_denied)
        }
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    fun pickPhoto() {
        if (needsMediaPermission(context)) {
            mediaPermissionLauncher.launch(mediaPermissions())
        } else {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
        }
    }

    fun removePhoto() {
        val previous = photoFileName
        photoFileName = null
        scope.launch { viewModel.deletePhoto(previous) }
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            StepIndicator(currentStep = stepIndex)

            Box(modifier = Modifier.weight(1f)) {
                when (step) {
                    OnboardingStep.WELCOME -> WelcomeStep(modifier = Modifier.fillMaxSize())

                    OnboardingStep.PROFILE -> ProfileStep(
                        name = name,
                        onNameChange = {
                            name = it
                            message = null
                        },
                        email = email,
                        onEmailChange = {
                            email = it
                            message = null
                        },
                        ageText = ageText,
                        onAgeChange = { value ->
                            ageText = value.filter { it.isDigit() }.take(3)
                            message = null
                        },
                        ageError = ageError,
                        photoFileName = photoFileName,
                        isLoaded = isLoaded,
                        isPhotoBusy = isPhotoBusy,
                        onPickPhoto = ::pickPhoto,
                        onRemovePhoto = ::removePhoto,
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.MEDICAL -> MedicalStep(
                        selected = medicalProfile,
                        onSelect = {
                            medicalProfile = it
                            message = null
                        },
                        enabled = isLoaded,
                        modifier = Modifier.fillMaxSize(),
                    )

                    OnboardingStep.FEATURES -> FeaturesStep(modifier = Modifier.fillMaxSize())
                }
            }

            message?.let { text ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                ) {
                    Text(
                        text = text,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            OnboardingControls(
                step = step,
                canContinue = when (step) {
                    OnboardingStep.WELCOME -> true
                    OnboardingStep.PROFILE -> isLoaded && !isPhotoBusy && !ageError
                    OnboardingStep.MEDICAL -> isLoaded
                    OnboardingStep.FEATURES -> isLoaded && !isPhotoBusy && !isSaving
                },
                isSaving = isSaving,
                onBack = { if (stepIndex > 0) stepIndex-- },
                onContinue = {
                    when (step) {
                        OnboardingStep.WELCOME,
                        OnboardingStep.PROFILE,
                        OnboardingStep.MEDICAL,
                        -> stepIndex++

                        OnboardingStep.FEATURES -> {
                            val currentDraft = draft()
                            scope.launch {
                                isSaving = true
                                message = null
                                val saved = viewModel.save(currentDraft)
                                isSaving = false
                                if (saved) {
                                    onCompleted()
                                } else {
                                    message = resources.getString(R.string.onboarding_save_failed)
                                }
                            }
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun StepIndicator(currentStep: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OnboardingStep.entries.forEachIndexed { index, _ ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(
                        if (index <= currentStep) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(96.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.onboarding_logo),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_tagline),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.onboarding_welcome_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ProfileStep(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    ageText: String,
    onAgeChange: (String) -> Unit,
    ageError: Boolean,
    photoFileName: String?,
    isLoaded: Boolean,
    isPhotoBusy: Boolean,
    onPickPhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_profile_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.onboarding_profile_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        ProfileAvatar(
            profile = Profile(name = name, photoFileName = photoFileName),
            size = 104.dp,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onPickPhoto,
                enabled = isLoaded && !isPhotoBusy,
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
                    onClick = onRemovePhoto,
                    enabled = isLoaded && !isPhotoBusy,
                ) {
                    Text(text = stringResource(R.string.profile_remove_photo))
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(text = stringResource(R.string.profile_name)) },
            singleLine = true,
            enabled = isLoaded,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = email,
            onValueChange = onEmailChange,
            label = { Text(text = stringResource(R.string.profile_email)) },
            singleLine = true,
            enabled = isLoaded,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = ageText,
            onValueChange = onAgeChange,
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
            text = stringResource(R.string.onboarding_profile_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MedicalStep(
    selected: MedicalProfile?,
    onSelect: (MedicalProfile?) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_medical_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_medical_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MedicalProfileOptions(
            selected = selected,
            onSelect = onSelect,
            enabled = enabled,
        )
    }
}

@Composable
private fun FeaturesStep(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.onboarding_features_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.onboarding_features_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FEATURES.forEach { feature ->
            FeatureRow(feature = feature)
        }

        Text(
            text = stringResource(R.string.onboarding_features_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FeatureRow(feature: Feature) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = feature.icon,
                style = MaterialTheme.typography.headlineSmall,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(feature.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(feature.descriptionRes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun OnboardingControls(
    step: OnboardingStep,
    canContinue: Boolean,
    isSaving: Boolean,
    onBack: () -> Unit,
    onContinue: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (step != OnboardingStep.WELCOME) {
            OutlinedButton(
                onClick = onBack,
                enabled = !isSaving,
            ) {
                Text(text = stringResource(R.string.onboarding_back))
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Button(
            onClick = onContinue,
            enabled = canContinue,
            modifier = if (step == OnboardingStep.WELCOME) {
                Modifier.weight(1f)
            } else {
                Modifier
            },
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
                Text(
                    text = stringResource(
                        when (step) {
                            OnboardingStep.WELCOME -> R.string.onboarding_get_started
                            OnboardingStep.FEATURES -> R.string.onboarding_finish
                            else -> R.string.onboarding_continue
                        },
                    ),
                )
            }
        }
    }
}
