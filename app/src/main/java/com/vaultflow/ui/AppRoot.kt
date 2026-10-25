package com.vaultflow.ui

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.vaultflow.R
import com.vaultflow.core.security.EncryptedFileCipher
import com.vaultflow.feature.VaultMessage
import com.vaultflow.feature.VaultViewModel
import kotlinx.coroutines.delay

private data class Destination(val route: String, val title: Int, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun AppRoot(activity: FragmentActivity, viewModel: VaultViewModel = hiltViewModel()) {
    val onboarding by viewModel.onboardingComplete.collectAsState()
    val locked by viewModel.locked.collectAsState()
    val biometricEnabled by viewModel.biometricEnabled.collectAsState()
    val progress by viewModel.importProgress.collectAsState()
    val importFailed = stringResource(R.string.import_failed)
    val tooLarge = stringResource(R.string.file_too_large)
    val unsupportedFile = stringResource(R.string.unsupported_file)
    val operationFailed = stringResource(R.string.operation_failed)
    val dataCleared = stringResource(R.string.data_cleared)
    val documentSaved = stringResource(R.string.document_saved)
    var showImportChooser by remember { mutableStateOf(false) }
    val owner = LocalLifecycleOwner.current
    val snackbar = remember { SnackbarHostState() }

    DisposableEffect(owner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.lockOnBackground()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(viewModel, importFailed, tooLarge, unsupportedFile, operationFailed, dataCleared) {
        viewModel.messages.collect { message ->
            val text = when (message) {
                VaultMessage.ImportFailed -> importFailed
                VaultMessage.TooLarge -> tooLarge
                VaultMessage.UnsupportedFile -> unsupportedFile
                VaultMessage.OperationFailed -> operationFailed
                VaultMessage.DataCleared -> dataCleared
            }
            snackbar.showSnackbar(text)
        }
    }

    LaunchedEffect(progress?.finished, documentSaved) {
        if (progress?.finished == true) {
            snackbar.showSnackbar(documentSaved)
            delay(500)
            viewModel.dismissImportProgress()
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        when {
            onboarding == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            onboarding == false -> OnboardingScreen(viewModel)
            locked && biometricEnabled -> BiometricLockScreen(activity, viewModel)
            else -> VaultNavigation(viewModel, snackbar, onAddDocument = { showImportChooser = true })
        }
    }

    val activeProgress = progress?.takeUnless { it.finished }
    if (activeProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.importing)) },
            text = {
                androidx.compose.foundation.layout.Column {
                    CircularProgressIndicator()
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))
                    Text("${(activeProgress.bytesRead * 100 / EncryptedFileCipher.MAX_IMPORT_BYTES).coerceIn(0, 99)}%")
                }
            },
            confirmButton = {},
        )
    }
    ImportPickerHost(viewModel, showImportChooser, onChooserHandled = { showImportChooser = false })
}

@Composable
private fun VaultNavigation(viewModel: VaultViewModel, snackbar: SnackbarHostState, onAddDocument: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val destinations = listOf(
        Destination("home", R.string.home, Icons.Default.Home),
        Destination("categories", R.string.categories, Icons.Default.Category),
        Destination("favorites", R.string.favorites, Icons.Default.Favorite),
        Destination("settings", R.string.settings, Icons.Default.Settings),
    )
    val showNavigation = route in destinations.map { it.route }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (showNavigation) {
                NavigationBar {
                    destinations.forEach { destination ->
                        NavigationBarItem(
                            selected = route == destination.route,
                            onClick = {
                                nav.navigate(destination.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                if (destination.route != "favorites") viewModel.clearFilters()
                                if (destination.route == "favorites") viewModel.showFavorites(true)
                            },
                            icon = { Icon(destination.icon, contentDescription = stringResource(destination.title)) },
                            label = { Text(stringResource(destination.title)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(navController = nav, startDestination = "home", modifier = Modifier.padding(padding)) {
            composable("home") { HomeScreen(viewModel, onAddDocument, onOpenDocument = { nav.navigate("document/$it") }) }
            composable("categories") {
                CategoriesScreen(viewModel) { id ->
                    viewModel.selectCategory(id)
                    nav.navigate("home") { popUpTo("home") { inclusive = true } }
                }
            }
            composable("favorites") { FavoritesScreen(viewModel, onOpenDocument = { nav.navigate("document/$it") }) }
            composable("settings") { SettingsScreen(viewModel) }
            composable(
                route = "document/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType }),
            ) { backStack ->
                DocumentDetailsScreen(
                    id = backStack.arguments?.getString("id").orEmpty(),
                    viewModel = viewModel,
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }

    val viewer by viewModel.viewer.collectAsState()
    viewer?.let { DocumentViewerDialog(it, onClose = viewModel::closeViewer) }
}

@Composable
private fun BiometricLockScreen(activity: FragmentActivity, viewModel: VaultViewModel) {
    val context = LocalContext.current
    val manager = remember(context) { BiometricManager.from(context) }
    val flags = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val available = manager.canAuthenticate(flags) == BiometricManager.BIOMETRIC_SUCCESS
    val prompt = remember(activity, viewModel) {
        BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = viewModel.unlock()
            },
        )
    }
    val title = stringResource(R.string.app_name)
    val subtitle = stringResource(R.string.biometric_lock)
    androidx.compose.runtime.LaunchedEffect(available) {
        if (available) prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setAllowedAuthenticators(flags)
                .build(),
        )
    }

    androidx.compose.foundation.layout.Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.biometric_lock), style = MaterialTheme.typography.headlineSmall)
        if (!available) Text(stringResource(R.string.biometric_unavailable))
        TextButton(onClick = {
            if (available) prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).setAllowedAuthenticators(flags).build(),
            )
        }) { Text(stringResource(R.string.unlock)) }
    }
}
