package dev.damado.nordpass.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.damado.nordpass.di.AppContainer
import dev.damado.nordpass.ui.carnet.CarnetScreen
import dev.damado.nordpass.ui.carnet.CarnetViewModel
import dev.damado.nordpass.ui.login.LoginScreen
import dev.damado.nordpass.ui.login.LoginViewModel
import dev.damado.nordpass.ui.settings.SettingsScreen
import dev.damado.nordpass.ui.settings.SettingsViewModel

private object Route {
    const val CARNET = "carnet"
    const val LOGIN = "login"
    const val SETTINGS = "settings"
    const val LOGIN_WITH_REASON = "$LOGIN?reason={reason}"
}

private const val LOGOUT_REASON = "You have been logged out."

@Composable
fun CarnetApp(
    container: AppContainer,
    onInitialCheckComplete: () -> Unit = {},
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.CARNET) {
        composable(Route.CARNET) {
            val vm: CarnetViewModel = viewModel(
                factory = simpleFactory { CarnetViewModel(container.session, container.carnet) },
            )
            CarnetScreen(
                state = vm.uiState,
                onRefresh = { vm.refresh() },
                onOpenSettings = { navController.navigate(Route.SETTINGS) },
                onNeedLogin = { reason ->
                    // Uri.encode uses %20 for spaces; URLEncoder uses '+', which
                    // Navigation does not decode back to a space.
                    navController.navigate("${Route.LOGIN}?reason=${Uri.encode(reason)}") {
                        popUpTo(Route.CARNET) { inclusive = true }
                    }
                },
                onAutoRefreshEnabled = { enabled -> vm.setAutoRefreshEnabled(enabled) },
                onInitialCheckComplete = onInitialCheckComplete,
            )
        }

        composable(
            route = Route.LOGIN_WITH_REASON,
            arguments = listOf(navArgument("reason") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStackEntry ->
            val reason = backStackEntry.arguments?.getString("reason")
            val vm: LoginViewModel = viewModel(
                factory = simpleFactory { LoginViewModel(container.auth, container.secrets) },
            )
            LoginScreen(
                state = vm.uiState,
                reason = reason,
                onUsernameChange = vm::onUsernameChange,
                onPasswordChange = vm::onPasswordChange,
                onSavePasswordChange = vm::onSavePasswordChange,
                onSubmit = vm::submit,
                onLoggedIn = {
                    navController.navigate(Route.CARNET) {
                        popUpTo(Route.LOGIN_WITH_REASON) { inclusive = true }
                    }
                },
                onNavigated = vm::onNavigated,
            )
        }

        composable(Route.SETTINGS) {
            val vm: SettingsViewModel = viewModel(
                factory = simpleFactory {
                    SettingsViewModel(
                        settings = container.settings,
                        secrets = container.secrets,
                        auth = container.auth,
                        cookieJar = container.cookieJar,
                        onLoggedOut = {
                            // Fresh Login root: no Back path to Settings/Carnet,
                            // and the carnet ViewModel dies with its entry.
                            navController.navigate("${Route.LOGIN}?reason=${Uri.encode(LOGOUT_REASON)}") {
                                popUpTo(Route.CARNET) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                },
            )
            SettingsScreen(
                state = vm.uiState,
                onQrModeChange = vm::onQrModeChange,
                onLogout = vm::logout,
            )
        }
    }
}

private fun <VM : ViewModel> simpleFactory(create: () -> VM): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = create() as T
    }
