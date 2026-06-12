package com.example.nutrease.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.nutrease.ui.screens.auth.LoginScreen
import com.example.nutrease.ui.screens.auth.RegisterScreen
import com.example.nutrease.ui.screens.auth.ResetPasswordScreen
import com.example.nutrease.ui.screens.chat.ChatListScreen
import com.example.nutrease.ui.screens.chat.ChatScreen
import com.example.nutrease.ui.screens.diary.AddMealScreen
import com.example.nutrease.ui.screens.diary.AddSymptomScreen
import com.example.nutrease.ui.screens.diary.DiaryScreen
import com.example.nutrease.ui.screens.home.PatientHomeScreen
import com.example.nutrease.ui.screens.home.SpecialistHomeScreen
import com.example.nutrease.ui.screens.linkedpatients.LinkedPatientsScreen
import com.example.nutrease.ui.screens.patientdiary.PatientDiaryScreen
import com.example.nutrease.ui.screens.patientstats.PatientStatsScreen
import com.example.nutrease.ui.screens.profile.ProfileScreen
import com.example.nutrease.ui.screens.reminder.ReminderScreen
import com.example.nutrease.ui.screens.requests.LinkRequestsScreen
import com.example.nutrease.ui.screens.specialists.SpecialistsScreen
import com.example.nutrease.ui.screens.splash.SplashScreen
import kotlinx.datetime.LocalDate

private const val ROUTE_SPLASH = "splash"
private const val ROUTE_LOGIN = "login"
private const val ROUTE_REGISTER = "register"
private const val ROUTE_RESET_PASSWORD = "reset_password"
private const val ROUTE_PATIENT_HOME = "patient_home"
private const val ROUTE_SPECIALIST_HOME = "specialist_home"
private const val ROUTE_PROFILE = "profile"
private const val ROUTE_DIARY = "diary"
private const val ROUTE_ADD_MEAL = "add_meal"
private const val ROUTE_ADD_SYMPTOM = "add_symptom"
private const val ROUTE_SPECIALISTS = "specialists"
private const val ROUTE_LINK_REQUESTS = "link_requests"
private const val ROUTE_LINKED_PATIENTS = "linked_patients"
private const val ROUTE_PATIENT_DIARY = "patient_diary"
private const val ROUTE_PATIENT_STATS = "patient_stats"
private const val ROUTE_REMINDER = "reminder_settings"
private const val ROUTE_CHAT_LIST = "chat_list"
private const val ROUTE_CHAT = "chat"
private const val ARG_DATE = "date"
private const val ARG_CHAT_ID = "chat_id"
private const val ARG_COUNTERPART_NAME = "counterpart_name"
private const val ARG_MEAL_ID = "meal_id"
private const val ARG_SYMPTOM_ID = "symptom_id"
private const val ARG_FASCICOLO_ID = "fascicolo_id"
private const val ARG_PATIENT_NAME = "patient_name"
private const val ARG_EMAIL = "email"

/**
 * Grafo di navigazione unico dell'app. Ogni rotta è una stringa con eventuali
 * parametri in query (es. `add_meal?date=&meal_id=`): i ViewModel li rileggono dal
 * [androidx.lifecycle.SavedStateHandle], così le screen non si passano oggetti.
 * Le screen ricevono solo callback `onNavigate*`: la conoscenza delle rotte sta tutta qui.
 */
@Composable
fun AppNavGraph(navController: NavHostController) {

    fun goHome(route: String) = navController.navigate(route) {
        popUpTo(ROUTE_LOGIN) { inclusive = true }
    }

    fun goLogin() = navController.navigate(ROUTE_LOGIN) {
        popUpTo(0) { inclusive = true }
    }

    fun goFromSplash(route: String) = navController.navigate(route) {
        popUpTo(ROUTE_SPLASH) { inclusive = true }
    }

    NavHost(navController = navController, startDestination = ROUTE_SPLASH) {

        composable(ROUTE_SPLASH) {
            SplashScreen(
                onNavigateToLogin = { goFromSplash(ROUTE_LOGIN) },
                onNavigateToPatientHome = { goFromSplash(ROUTE_PATIENT_HOME) },
                onNavigateToSpecialistHome = { goFromSplash(ROUTE_SPECIALIST_HOME) }
            )
        }

        composable(ROUTE_LOGIN) {
            LoginScreen(
                onNavigateToPatientHome = { goHome(ROUTE_PATIENT_HOME) },
                onNavigateToSpecialistHome = { goHome(ROUTE_SPECIALIST_HOME) },
                onNavigateToRegister = { navController.navigate(ROUTE_REGISTER) },
                onNavigateToResetPassword = { email ->
                    navController.navigate("$ROUTE_RESET_PASSWORD?$ARG_EMAIL=${Uri.encode(email)}")
                }
            )
        }

        composable(
            route = "$ROUTE_RESET_PASSWORD?$ARG_EMAIL={$ARG_EMAIL}",
            arguments = listOf(
                navArgument(ARG_EMAIL) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ResetPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onPasswordReset = { navController.popBackStack() } // torna al login
            )
        }

        composable(ROUTE_REGISTER) {
            RegisterScreen(
                onNavigateToPatientHome = { goHome(ROUTE_PATIENT_HOME) },
                onNavigateToSpecialistHome = { goHome(ROUTE_SPECIALIST_HOME) },
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(ROUTE_PATIENT_HOME) {
            PatientHomeScreen(
                onNavigateToProfile = { navController.navigate(ROUTE_PROFILE) },
                onNavigateToLogin = { goLogin() },
                onNavigateToDiary = { navController.navigate(ROUTE_DIARY) },
                onNavigateToSpecialists = { navController.navigate(ROUTE_SPECIALISTS) },
                onNavigateToReminder = { navController.navigate(ROUTE_REMINDER) },
                onNavigateToChats = { navController.navigate(ROUTE_CHAT_LIST) }
            )
        }

        composable(ROUTE_REMINDER) {
            ReminderScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_CHAT_LIST) {
            ChatListScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectChat = { chat ->
                    val name = Uri.encode(chat.counterpartName)
                    navController.navigate(
                        "$ROUTE_CHAT?$ARG_CHAT_ID=${chat.chatId}&$ARG_COUNTERPART_NAME=$name"
                    )
                }
            )
        }

        composable(
            route = "$ROUTE_CHAT?$ARG_CHAT_ID={$ARG_CHAT_ID}&$ARG_COUNTERPART_NAME={$ARG_COUNTERPART_NAME}",
            arguments = listOf(
                navArgument(ARG_CHAT_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                },
                navArgument(ARG_COUNTERPART_NAME) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            ChatScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_SPECIALISTS) {
            SpecialistsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_SPECIALIST_HOME) {
            SpecialistHomeScreen(
                onNavigateToProfile = { navController.navigate(ROUTE_PROFILE) },
                onNavigateToLogin = { goLogin() },
                onNavigateToLinkRequests = { navController.navigate(ROUTE_LINK_REQUESTS) },
                onNavigateToLinkedPatients = { navController.navigate(ROUTE_LINKED_PATIENTS) },
                onNavigateToChats = { navController.navigate(ROUTE_CHAT_LIST) }
            )
        }

        composable(ROUTE_LINK_REQUESTS) {
            LinkRequestsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_LINKED_PATIENTS) {
            LinkedPatientsScreen(
                onNavigateBack = { navController.popBackStack() },
                onSelectPatient = { p ->
                    val name = Uri.encode("${p.firstName} ${p.lastName}")
                    navController.navigate(
                        "$ROUTE_PATIENT_DIARY?$ARG_FASCICOLO_ID=${p.fascicoloId}&$ARG_PATIENT_NAME=$name"
                    )
                }
            )
        }

        composable(
            route = "$ROUTE_PATIENT_DIARY?$ARG_FASCICOLO_ID={$ARG_FASCICOLO_ID}&$ARG_PATIENT_NAME={$ARG_PATIENT_NAME}",
            arguments = listOf(
                navArgument(ARG_FASCICOLO_ID) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(ARG_PATIENT_NAME) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            PatientDiaryScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToStats = { fascicoloId, patientName ->
                    val name = Uri.encode(patientName.orEmpty())
                    navController.navigate(
                        "$ROUTE_PATIENT_STATS?$ARG_FASCICOLO_ID=$fascicoloId&$ARG_PATIENT_NAME=$name"
                    )
                }
            )
        }

        composable(
            route = "$ROUTE_PATIENT_STATS?$ARG_FASCICOLO_ID={$ARG_FASCICOLO_ID}&$ARG_PATIENT_NAME={$ARG_PATIENT_NAME}",
            arguments = listOf(
                navArgument(ARG_FASCICOLO_ID) {
                    type = NavType.IntType
                    defaultValue = 0
                },
                navArgument(ARG_PATIENT_NAME) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            PatientStatsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(ROUTE_PROFILE) {
            ProfileScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = { goLogin() }
            )
        }

        composable(ROUTE_DIARY) {
            DiaryScreen(
                onNavigateBack = { navController.popBackStack() },
                onAddMeal = { date ->
                    navController.navigate("$ROUTE_ADD_MEAL?$ARG_DATE=$date")
                },
                onAddSymptom = { date ->
                    navController.navigate("$ROUTE_ADD_SYMPTOM?$ARG_DATE=$date")
                },
                onEditMeal = { mealId ->
                    navController.navigate("$ROUTE_ADD_MEAL?$ARG_MEAL_ID=$mealId")
                },
                onEditSymptom = { symptomId ->
                    navController.navigate("$ROUTE_ADD_SYMPTOM?$ARG_SYMPTOM_ID=$symptomId")
                }
            )
        }

        composable(
            route = "$ROUTE_ADD_MEAL?$ARG_DATE={$ARG_DATE}&$ARG_MEAL_ID={$ARG_MEAL_ID}",
            arguments = listOf(
                navArgument(ARG_DATE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_MEAL_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val dateArg = backStackEntry.arguments?.getString(ARG_DATE)
            val initialDate = dateArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            AddMealScreen(
                initialDate = initialDate,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }

        composable(
            route = "$ROUTE_ADD_SYMPTOM?$ARG_DATE={$ARG_DATE}&$ARG_SYMPTOM_ID={$ARG_SYMPTOM_ID}",
            arguments = listOf(
                navArgument(ARG_DATE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(ARG_SYMPTOM_ID) {
                    type = NavType.LongType
                    defaultValue = 0L
                }
            )
        ) { backStackEntry ->
            val dateArg = backStackEntry.arguments?.getString(ARG_DATE)
            val initialDate = dateArg?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            AddSymptomScreen(
                initialDate = initialDate,
                onNavigateBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() }
            )
        }
    }
}