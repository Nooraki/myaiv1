package ir.example.slmchat

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import ir.example.slmchat.ui.ChatScreen
import ir.example.slmchat.ui.ChatViewModel
import ir.example.slmchat.ui.HistoryScreen
import ir.example.slmchat.ui.ModelScreen
import ir.example.slmchat.ui.SettingsScreen
import ir.example.slmchat.ui.theme.SlmChatTheme

enum class Screen { MODELS, CHAT, SETTINGS, HISTORY }

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    // اعلان دانلود در اندروید ۱۳+ به اجازه نیاز دارد (بدون آن دانلود کار می‌کند ولی اعلان دیده نمی‌شود)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            SlmChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    App(viewModel)
                }
            }
        }
    }
}

@Composable
private fun App(vm: ChatViewModel) {
    val state by vm.uiState.collectAsState()
    val settings by vm.settings.collectAsState()

    var screen by rememberSaveable { mutableStateOf(Screen.MODELS) }
    var settingsReturn by rememberSaveable { mutableStateOf(Screen.MODELS) }

    // آخرین مدل را خودکار بارگذاری می‌کند و مستقیم وارد چت می‌شود
    LaunchedEffect(Unit) { vm.autoLoadLast { screen = Screen.CHAT } }

    // بدون مدل بارگذاری‌شده، چت/تاریخچه معنی ندارد
    val current =
        if ((screen == Screen.CHAT || screen == Screen.HISTORY) && !state.isModelLoaded) Screen.MODELS
        else screen

    val goBack: () -> Unit = {
        when (current) {
            Screen.CHAT -> screen = Screen.MODELS
            Screen.HISTORY -> screen = Screen.CHAT
            Screen.SETTINGS -> {
                vm.refreshSession() // اعمال تغییرات تنظیمات (و بارگذاری مجدد در صورت نیاز)
                screen = if (settingsReturn == Screen.CHAT && state.isModelLoaded) Screen.CHAT else Screen.MODELS
            }
            Screen.MODELS -> Unit
        }
    }

    BackHandler(enabled = current != Screen.MODELS, onBack = goBack)

    when (current) {
        Screen.MODELS -> ModelScreen(
            state = state,
            onDownload = vm::downloadModel,
            onCancelDownload = vm::cancelDownload,
            onImport = vm::importModel,
            onLoad = { file -> vm.loadModel(file) { screen = Screen.CHAT } },
            onDelete = vm::deleteModel,
            onDismissError = vm::clearError,
            onOpenChat = { screen = Screen.CHAT },
            onOpenSettings = {
                settingsReturn = Screen.MODELS
                screen = Screen.SETTINGS
            },
        )

        Screen.CHAT -> ChatScreen(
            state = state,
            onSend = vm::sendMessage,
            onStop = vm::stopGeneration,
            onBack = goBack,
            onNewChat = vm::newChat,
            onOpenHistory = { screen = Screen.HISTORY },
            onOpenSettings = {
                settingsReturn = Screen.CHAT
                screen = Screen.SETTINGS
            },
            onDismissInfo = vm::clearInfo,
            onDismissError = vm::clearError,
        )

        Screen.HISTORY -> HistoryScreen(
            conversations = state.conversations,
            currentId = state.currentConversationId,
            onBack = goBack,
            onOpen = { id -> vm.openConversation(id) { screen = Screen.CHAT } },
            onDelete = vm::deleteConversation,
        )

        Screen.SETTINGS -> SettingsScreen(
            settings = settings,
            onChange = vm::updateSettings,
            onBack = goBack,
        )
    }
}
