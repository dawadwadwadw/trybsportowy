package com.trybsportowy.presentation.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import com.trybsportowy.data.local.ChatMessageEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    var textState by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val isTyping = viewModel.isTyping
    val errorMessage = viewModel.errorMessage

    // Okno dialogowe potwierdzenia czyszczenia historii
    var showClearDialog by remember { mutableStateOf(false) }

    // Auto-scroll do ostatniej wiadomości
    LaunchedEffect(viewModel.messages.size, isTyping) {
        val itemCount = viewModel.messages.size + (if (isTyping) 1 else 0)
        if (itemCount > 0) {
            listState.animateScrollToItem(itemCount - 1)
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Wyczyść historię?") },
            text = {
                Text(
                    "Wszystkie wiadomości z dzisiaj zostaną trwale usunięte z bazy danych.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatHistory()
                        showClearDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Usuń")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Trener AI",
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Analiza 7-dniowa • Naukowy Wojownik",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Wróć")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showClearDialog = true },
                        enabled = viewModel.messages.isNotEmpty() && !isTyping
                    ) {
                        Icon(
                            Icons.Outlined.DeleteSweep,
                            contentDescription = "Wyczyść historię",
                            tint = if (viewModel.messages.isNotEmpty() && !isTyping)
                                MaterialTheme.colorScheme.onSurfaceVariant
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = Modifier.imePadding()
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // ─── Lista wiadomości ─────────────────────────────────────────────
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (viewModel.messages.isEmpty() && !isTyping) {
                    item {
                        WelcomeBanner()
                    }
                }

                items(viewModel.messages, key = { it.id }) { msg ->
                    ChatMessageItem(
                        message = msg,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(msg.content))
                        },
                        onDelete = {
                            viewModel.deleteMessage(msg)
                        }
                    )
                }

                // Wskaźnik ładowania pod listą
                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }
            }

            // ─── Pasek błędu ──────────────────────────────────────────────────
            errorMessage?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.dismissError() }) {
                            Text(
                                "OK",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ─── Pole wprowadzania ────────────────────────────────────────────
            MessageInputBar(
                text = textState,
                onTextChange = { textState = it },
                onSend = {
                    val trimmed = textState.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.sendMessage(trimmed)
                        textState = ""
                        keyboardController?.hide()
                    }
                },
                isEnabled = !isTyping
            )
        }
    }
}

// ─── Komponenty pomocnicze ────────────────────────────────────────────────────

@Composable
private fun ChatMessageItem(
    message: ChatMessageEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val isUser = message.role == "user"
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = { showMenu = true }
                )
            }
    ) {
        // Label nadawcy
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Text(
                text = if (isUser) "Ty" else "🥊 Trener AI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (isUser)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.secondary
            )
        }

        // Treść wiadomości
        if (isUser) {
            SelectionContainer {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        } else {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(
                    topStart = 4.dp,
                    topEnd = 12.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp
                )
            ) {
                SelectionContainer {
                    RichText(modifier = Modifier.padding(12.dp)) {
                        Markdown(content = message.content)
                    }
                }
            }
        }

        // Separator po wiadomości AI
        if (!isUser) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }

        // Menu kontekstowe (długie przytrzymanie)
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = DpOffset(0.dp, 4.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Kopiuj") },
                onClick = {
                    onCopy()
                    showMenu = false
                }
            )
            DropdownMenuItem(
                text = {
                    Text(
                        "Usuń",
                        color = MaterialTheme.colorScheme.error
                    )
                },
                onClick = {
                    onDelete()
                    showMenu = false
                }
            )
        }
    }
}

@Composable
private fun TypingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 8.dp)
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(14.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.secondary
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "🥊 Trener analizuje dane...",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alpha(alpha)
        )
    }
}

@Composable
private fun WelcomeBanner() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🥊", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                "Naukowy Wojownik",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Analizuję 7 dni Twoich danych fizjologicznych. " +
                    "Zadaj pytanie o gotowość, regenerację lub planowanie treningu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    isEnabled: Boolean
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(
                    "Zapytaj trenera...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            shape = RoundedCornerShape(24.dp),
            maxLines = 4,
            enabled = isEnabled,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
        Spacer(modifier = Modifier.width(10.dp))
        FilledIconButton(
            onClick = onSend,
            enabled = isEnabled && text.isNotBlank(),
            modifier = Modifier.size(48.dp)
        ) {
            Text("→", fontSize = 20.sp)
        }
    }
}
