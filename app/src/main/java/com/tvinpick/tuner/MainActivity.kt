package com.tvinpick.tuner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tvinpick.tuner.ui.PermissionScreen
import com.tvinpick.tuner.ui.TunerColors
import com.tvinpick.tuner.ui.TunerScreen
import com.tvinpick.tuner.ui.TunerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Экран не гаснет, пока настраиваешь
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = TunerColors.Good,
                    background = TunerColors.Bg1,
                    surface = TunerColors.Card,
                    surfaceContainer = TunerColors.Card,
                )
            ) {
                val context = LocalContext.current
                fun hasMic() = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED

                val vm: TunerViewModel = viewModel()
                var granted by remember { mutableStateOf(hasMic()) }
                var denied by remember { mutableStateOf(false) }
                val launcher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { ok ->
                    granted = ok
                    if (ok) {
                        vm.start()
                    } else {
                        denied = true
                    }
                }

                val state by vm.state.collectAsStateWithLifecycle()
                val lifecycleOwner = LocalLifecycleOwner.current

                // Микрофон работает только пока приложение на экране
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_RESUME -> {
                                granted = hasMic()
                                if (granted) vm.start()
                            }
                            Lifecycle.Event.ON_PAUSE -> vm.stop()
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                        vm.stop()
                    }
                }

                if (granted) {
                    TunerScreen(state, vm)
                } else {
                    PermissionScreen(openSettings = denied) {
                        if (denied) {
                            // Пользователь отказал — разрешить можно только в настройках
                            startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", packageName, null)
                                )
                            )
                        } else {
                            launcher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            }
        }
    }
}
