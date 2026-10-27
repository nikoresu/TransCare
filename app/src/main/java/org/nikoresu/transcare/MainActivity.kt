package org.nikoresu.transcare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import org.nikoresu.transcare.settings.LocalePreferences
import org.nikoresu.transcare.settings.OnboardingPreferences
import org.nikoresu.transcare.ui.MainScreen
import org.nikoresu.transcare.ui.appointments.AppointmentsViewModel
import org.nikoresu.transcare.ui.diary.DiaryViewModel
import org.nikoresu.transcare.ui.exams.ExamsViewModel
import org.nikoresu.transcare.ui.medications.MedicationsViewModel
import org.nikoresu.transcare.ui.onboarding.OnboardingScreen
import org.nikoresu.transcare.ui.profile.ProfileViewModel
import org.nikoresu.transcare.ui.theme.TranscareTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check before creating the view models, because their repositories
        // load the data used to detect an install that predates onboarding.
        val startOnboarding = !OnboardingPreferences.isCompleted(this)

        val appointmentsViewModel = ViewModelProvider(this)[AppointmentsViewModel::class.java]
        val examsViewModel = ViewModelProvider(this)[ExamsViewModel::class.java]
        val diaryViewModel = ViewModelProvider(this)[DiaryViewModel::class.java]
        val medicationsViewModel = ViewModelProvider(this)[MedicationsViewModel::class.java]
        val profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        setContent {
            TranscareTheme {
                var showOnboarding by remember { mutableStateOf(startOnboarding) }

                if (showOnboarding) {
                    OnboardingScreen(
                        viewModel = profileViewModel,
                        onCompleted = {
                            OnboardingPreferences.markCompleted(this)
                            showOnboarding = false
                        },
                    )
                } else {
                    MainScreen(
                        appointmentsViewModel = appointmentsViewModel,
                        examsViewModel = examsViewModel,
                        diaryViewModel = diaryViewModel,
                        medicationsViewModel = medicationsViewModel,
                        profileViewModel = profileViewModel,
                        onLanguageChanged = { recreate() },
                    )
                }
            }
        }
    }
}
