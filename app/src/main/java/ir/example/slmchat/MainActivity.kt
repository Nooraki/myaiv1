package ir.example.slmchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import ir.example.slmchat.ui.ChatScreen
import ir.example.slmchat.ui.ChatViewModel
import ir.example.slmchat.ui.ModelScreen
import ir.example.slmchat.ui.theme.SlmChatTheme

class MainActivity : ComponentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SlmChatTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsState()
                    var showChat by remember { mutableStateOf(false) }

                    if (state.isModelLoaded && showChat) {
                        ChatScreen(
                            state = state,
                            onSend = viewModel::sendMessage,
                            onBack = { showChat = false },
                        )
                    } else {
                        ModelScreen(
                            state = state,
                            onDownload = viewModel::downloadModel,
                            onImport = viewModel::importModel,
                            onLoad = {
                                viewModel.loadModel(it)
                                showChat = true
                            },
                            onDelete = viewModel::deleteModel,
                            onDismissError = viewModel::clearError,
                            onOpenChat = { showChat = true },
                        )
                    }
                }
            }
        }
    }
}
