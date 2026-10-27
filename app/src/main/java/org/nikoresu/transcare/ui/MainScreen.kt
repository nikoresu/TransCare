package org.nikoresu.transcare.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nikoresu.transcare.R
import org.nikoresu.transcare.notifications.AppointmentReminderScheduler
import org.nikoresu.transcare.notifications.MedicationReminderScheduler
import org.nikoresu.transcare.ui.appointments.AppointmentsScreen
import org.nikoresu.transcare.ui.appointments.AppointmentsViewModel
import org.nikoresu.transcare.ui.diary.DiaryScreen
import org.nikoresu.transcare.ui.diary.DiaryViewModel
import org.nikoresu.transcare.ui.exams.ExamsScreen
import org.nikoresu.transcare.ui.exams.ExamsViewModel
import org.nikoresu.transcare.ui.medications.MedicationsScreen
import org.nikoresu.transcare.ui.medications.MedicationsViewModel
import org.nikoresu.transcare.ui.profile.ProfileScreen
import org.nikoresu.transcare.ui.profile.ProfileViewModel

private enum class MainTab(@param:StringRes val labelRes: Int, val icon: String) {
    SCHEDULE(labelRes = R.string.tab_schedule, icon = "🗓"),
    MEDICATIONS(labelRes = R.string.tab_medications, icon = "💊"),
    EXAMS(labelRes = R.string.tab_exams, icon = "🧪"),
    DIARY(labelRes = R.string.tab_diary, icon = "📔"),
    PROFILE(labelRes = R.string.tab_profile, icon = "👤"),
}

@Composable
fun MainScreen(
    appointmentsViewModel: AppointmentsViewModel,
    examsViewModel: ExamsViewModel,
    diaryViewModel: DiaryViewModel,
    medicationsViewModel: MedicationsViewModel,
    profileViewModel: ProfileViewModel,
    onLanguageChanged: () -> Unit,
) {
    var selectedTab by rememberSaveable { mutableStateOf(MainTab.SCHEDULE) }
    var editorOpen by remember { mutableStateOf(false) }

    val appointments by appointmentsViewModel.appointments.collectAsStateWithLifecycle()
    val appointmentsLoaded by appointmentsViewModel.isLoaded.collectAsStateWithLifecycle()
    val results by examsViewModel.results.collectAsStateWithLifecycle()
    val entries by diaryViewModel.entries.collectAsStateWithLifecycle()
    val medications by medicationsViewModel.medications.collectAsStateWithLifecycle()
    val medicationDoses by medicationsViewModel.doses.collectAsStateWithLifecycle()
    val medicationsLoaded by medicationsViewModel.isLoaded.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Resync whenever the app returns to the foreground: permission changes
    // and time changes may have happened while the app was in the background.
    var resyncToken by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resyncToken++
    }

    LaunchedEffect(appointments, appointmentsLoaded, resyncToken) {
        if (!appointmentsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            AppointmentReminderScheduler.sync(context.applicationContext, appointments)
        }
    }

    LaunchedEffect(medications, medicationDoses, medicationsLoaded, resyncToken) {
        if (!medicationsLoaded) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            MedicationReminderScheduler.sync(
                context = context.applicationContext,
                medications = medications,
                doses = medicationDoses,
            )
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (!editorOpen) {
                NavigationBar {
                    MainTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = {
                                Text(
                                    text = tab.icon,
                                    fontSize = 20.sp,
                                    textAlign = TextAlign.Center,
                                )
                            },
                            label = { Text(text = stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (selectedTab) {
                MainTab.SCHEDULE -> AppointmentsScreen(
                    viewModel = appointmentsViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onEditorVisibilityChanged = { editorOpen = it },
                )

                MainTab.MEDICATIONS -> MedicationsScreen(
                    viewModel = medicationsViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onEditorVisibilityChanged = { editorOpen = it },
                )

                MainTab.EXAMS -> ExamsScreen(
                    viewModel = examsViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onEditorVisibilityChanged = { editorOpen = it },
                )

                MainTab.DIARY -> DiaryScreen(
                    viewModel = diaryViewModel,
                    modifier = Modifier.fillMaxSize(),
                    onEditorVisibilityChanged = { editorOpen = it },
                )

                MainTab.PROFILE -> ProfileScreen(
                    viewModel = profileViewModel,
                    appointments = appointments,
                    results = results,
                    entries = entries,
                    medications = medications,
                    medicationDoses = medicationDoses,
                    onImport = { importedAppointments, importedResults, importedEntries, importedMedications, importedMedicationDoses ->
                        appointmentsViewModel.replaceAll(importedAppointments)
                        examsViewModel.replaceAll(importedResults)
                        diaryViewModel.replaceAll(importedEntries)
                        medicationsViewModel.replaceAll(
                            importedMedications,
                            importedMedicationDoses,
                        )
                    },
                    onLanguageChanged = onLanguageChanged,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
