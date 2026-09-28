package com.sengine.studio

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sengine.studio.ui.Dashboard
import com.sengine.studio.ui.EditorScreen
import com.sengine.studio.ui.StudioColors
import com.sengine.studio.ui.StudioTheme

class MainActivity : ComponentActivity() {
    private val vm: StudioViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = AndroidColor.rgb(11, 16, 27)
        window.navigationBarColor = AndroidColor.rgb(11, 16, 27)
        setContent { StudioApp(vm) }
    }

    override fun onStop() {
        vm.saveNow()
        super.onStop()
    }
}

@Composable
private fun StudioApp(vm: StudioViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importImage(uri)
    }
    val archivePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importArchive(uri)
    }
    val archiveWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportProject(uri)
    }
    val webWriter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportWebGame(uri)
    }
    val snackbars = remember { SnackbarHostState() }
    BackHandler(enabled = state.project != null) { vm.closeProject() }
    LaunchedEffect(state.notice) {
        state.notice?.let { message ->
            vm.dismissNotice()
            snackbars.showSnackbar(message)
        }
    }
    StudioTheme {
        Box(Modifier.fillMaxSize().background(StudioColors.background).statusBarsPadding().navigationBarsPadding()) {
            if (state.project == null) {
                Dashboard(
                    projects = state.projects,
                    onCreate = vm::createProject,
                    onOpen = vm::openProject,
                    onDelete = vm::deleteProject,
                    onImport = { archivePicker.launch(arrayOf("*/*")) },
                )
            } else {
                EditorScreen(
                    state = state,
                    vm = vm,
                    onPickImage = { imagePicker.launch(arrayOf("image/*")) },
                    onExportPackage = { name ->
                        archiveWriter.launch("${safeFileName(name)}.sengine")
                    },
                    onExportWeb = { name ->
                        webWriter.launch("${safeFileName(name)}-web.zip")
                    },
                )
            }
            SnackbarHost(snackbars, modifier = Modifier.align(Alignment.BottomCenter))
            if (state.busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = .6f)).clickable { },
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = StudioColors.violet) }
            }
        }
    }
}
private fun safeFileName(name: String): String =
    name.replace(Regex("[^a-zA-Z0-9 _-]"), "").ifBlank { "S Engine project" }
