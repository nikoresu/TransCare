package org.nikoresu.transcare

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import org.nikoresu.transcare.settings.LocalePreferences
import org.nikoresu.transcare.ui.MainScreen
import org.nikoresu.transcare.ui.appointments.AppointmentsViewModel
import org.nikoresu.transcare.ui.diary.DiaryViewModel
import org.nikoresu.transcare.ui.exams.ExamsViewModel
import org.nikoresu.transcare.ui.profile.ProfileViewModel
import org.nikoresu.transcare.ui.theme.TranscareTheme

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocalePreferences.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appointmentsViewModel = ViewModelProvider(this)[AppointmentsViewModel::class.java]
        val examsViewModel = ViewModelProvider(this)[ExamsViewModel::class.java]
        val diaryViewModel = ViewModelProvider(this)[DiaryViewModel::class.java]
        val profileViewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        setContent {
            TranscareTheme {
                MainScreen(
                    appointmentsViewModel = appointmentsViewModel,
                    examsViewModel = examsViewModel,
                    diaryViewModel = diaryViewModel,
                    profileViewModel = profileViewModel,
                    onLanguageChanged = { recreate() },
                )
            }
        }
    }
}
