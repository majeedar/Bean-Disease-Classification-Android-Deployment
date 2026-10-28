package com.majeedar.beandoc.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.majeedar.beandoc.R
import com.majeedar.beandoc.ui.camera.CameraScreen
import com.majeedar.beandoc.ui.home.HomeScreen
import com.majeedar.beandoc.ui.result.ResultScreen
import com.majeedar.beandoc.ui.tips.PhotoTipsScreen
import com.majeedar.beandoc.viewmodel.ScanState
import com.majeedar.beandoc.viewmodel.ScanViewModel

private object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val RESULT = "result"
    const val TIPS = "tips"
}

@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController(),
    // One ViewModel for all screens: photo and result live here, never in navigation arguments.
    scanViewModel: ScanViewModel = viewModel(),
) {
    val context = LocalContext.current
    val state by scanViewModel.state.collectAsStateWithLifecycle()

    fun analyze(uri: Uri) {
        if (scanViewModel.analyze(uri)) {
            navController.navigate(Routes.RESULT) { popUpTo(Routes.HOME) }
        } else {
            Toast.makeText(context, R.string.home_busy, Toast.LENGTH_SHORT).show()
        }
    }

    fun goHome() {
        scanViewModel.reset()
        navController.popBackStack(Routes.HOME, inclusive = false)
    }

    fun openCamera() {
        navController.navigate(Routes.CAMERA) { popUpTo(Routes.HOME) }
    }

    val pickPhoto = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) analyze(uri)
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                onScan = ::openCamera,
                onGallery = { pickPhoto.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                onTips = { navController.navigate(Routes.TIPS) },
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onPhotoTaken = ::analyze,
                onBack = { navController.popBackStack() },
                onGallery = { pickPhoto.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
            )
        }
        composable(Routes.RESULT) {
            // After process death the result is gone: go back home instead of showing an empty screen.
            // Only while Result is the current screen, not during its exit animation.
            LaunchedEffect(state) {
                if (state == ScanState.Idle && navController.currentDestination?.route == Routes.RESULT) {
                    navController.popBackStack(Routes.HOME, inclusive = false)
                }
            }
            ResultScreen(
                state = state,
                onBack = ::goHome,
                onScanAnother = ::openCamera,
                onRetake = ::openCamera,
                onTips = { navController.navigate(Routes.TIPS) },
                onRetry = scanViewModel::retry,
            )
        }
        composable(Routes.TIPS) {
            PhotoTipsScreen(
                onBack = { navController.popBackStack() },
                onScan = ::openCamera,
            )
        }
    }
}
