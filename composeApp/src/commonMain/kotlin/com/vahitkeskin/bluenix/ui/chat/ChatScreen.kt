package com.vahitkeskin.bluenix.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vahitkeskin.bluenix.core.model.ChatMessage
import com.vahitkeskin.bluenix.core.model.MessageType
import com.vahitkeskin.bluenix.ui.components.rememberImagePicker
import com.vahitkeskin.bluenix.ui.components.ChatImage
import com.vahitkeskin.bluenix.ui.components.rememberFilePicker
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    targetDeviceName: String,
    targetDeviceAddress: String,
    onBackClick: () -> Unit
) {
    val viewModel = koinViewModel<ChatViewModel>()

    // Veritabanından gelen mesajları canlı dinle
    val messages by viewModel.getMessages(targetDeviceAddress).collectAsState(initial = emptyList())

    // Karşı tarafın "Yazıyor..." bilgisini canlı dinle
    val isRemoteTyping by viewModel.isRemoteTyping(targetDeviceAddress)
        .collectAsState(initial = false)

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // --- Görsel Seçici ---
    val pickImage = rememberImagePicker { uriStr ->
        if (uriStr != null) {
            viewModel.sendImage(targetDeviceAddress, uriStr)
        }
    }

    // --- Dosya Seçici ---
    val pickFile = rememberFilePicker { uriStr ->
        if (uriStr != null) {
            // Şimdilik sendImage kullanıyoruz ama aslında sendFile olmalı.
            // ViewModel'e sendFile ekleyeceğiz.
            // viewModel.sendFile(targetDeviceAddress, uriStr)
             viewModel.sendImage(targetDeviceAddress, uriStr) // Geçici
        }
    }

    // --- Menu State ---
    var isMenuExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // --- 1. BİLDİRİM YÖNETİMİ (KRİTİK) ---
    // Bu ekrana girildiğinde Controller'a "Aktif sohbet bu" bilgisini veriyoruz.
    // Böylece AndroidChatController, bu adresten mesaj gelirse bildirim OLUŞTURMAZ.
    DisposableEffect(Unit) {
        // Ekran açıldı -> Bağlan
        viewModel.setActiveChat(targetDeviceAddress)
        onDispose {
            // Ekran kapandı -> Aktif sohbeti temizle
            viewModel.setActiveChat(null)
        }
    }

    // --- 2. OKUNDU BİLGİSİ VE OTOMATİK KAYDIRMA ---
    LaunchedEffect(messages.size) {
        // Yeni mesaj geldiyse veya ekran açıldıysa okundu yap
        viewModel.markAsRead(targetDeviceAddress)
        if (messages.isNotEmpty()) {
            // En yeni mesaja (Liste ters olduğu için index 0) kaydır
            listState.animateScrollToItem(0)
        }
    }

    // --- 3. "YAZIYOR..." SİNYALİ GÖNDERME ---
    // Kullanıcı yazı yazarken karşı tarafa sinyal gönderir (Debounce: 3sn duraksarsa keser)
    LaunchedEffect(inputText) {
        if (inputText.isNotEmpty()) {
            viewModel.onUserTyping(targetDeviceAddress, true)
            delay(3000)
            viewModel.onUserTyping(targetDeviceAddress, false)
        } else {
            viewModel.onUserTyping(targetDeviceAddress, false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        // Burada görünen isim, Home ekranından tıklanan "Gerçek İsim"dir.
                        Text(
                            text = targetDeviceName,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // INSTAGRAM TARZI YAZIYOR EFEKTİ
                        AnimatedVisibility(
                            visible = isRemoteTyping,
                            enter = fadeIn(animationSpec = tween(300)),
                            exit = fadeOut(animationSpec = tween(300))
                        ) {
                            Text(
                                "yazıyor...",
                                color = Color(0xFF00F2FF), // Neon Mavi
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Geri",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF111B2E))
            )
        },
        containerColor = Color(0xFF050B14)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            // --- Kapsayıcı Box (Menu Overlay için) ---
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // --- MESAJ LİSTESİ ---
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    reverseLayout = true
                ) {
                    // Karşı taraf yazıyorsa en alta baloncuk koy
                    if (isRemoteTyping) {
                        item { TypingBubble() }
                    }

                    items(messages) { msg ->
                        MessageBubble(msg)
                    }
                }

                // --- HIZLI MENÜ (SPEED DIAL) ---
                androidx.compose.animation.AnimatedVisibility(
                    visible = isMenuExpanded,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 8.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Galeri
                        FloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                pickImage()
                            },
                            containerColor = Color(0xFFE91E63),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Image, "Galeri")
                        }

                        // 2. Dosya
                        FloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                pickFile()
                            },
                            containerColor = Color(0xFF9C27B0),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.Description, "Dosya")
                        }

                        // 3. Konum
                        FloatingActionButton(
                            onClick = {
                                isMenuExpanded = false
                                viewModel.sendLocation(targetDeviceAddress)
                            },
                            containerColor = Color(0xFF4CAF50),
                            contentColor = Color.White,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, "Konum")
                        }
                    }
                }
            }

            // --- GİRİŞ ALANI ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111B2E))
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ATAŞ BUTONU (Menu Toggle)
                IconButton(onClick = { isMenuExpanded = !isMenuExpanded }) {
                    Icon(
                        imageVector = if (isMenuExpanded) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = "Ekle",
                        tint = Color(0xFF00F2FF),
                        modifier = Modifier.rotate(if (isMenuExpanded) 90f else 0f) // Opsiyonel animasyon
                    )
                }

                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Mesaj yaz...", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(targetDeviceAddress, targetDeviceName, inputText)
                            inputText = ""
                            viewModel.onUserTyping(targetDeviceAddress, false)
                        }
                    }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Color(0xFF00F2FF),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            viewModel.sendMessage(targetDeviceAddress, targetDeviceName, inputText)
                            inputText = ""
                            viewModel.onUserTyping(targetDeviceAddress, false) // Temizlik
                        }
                    },
                    enabled = inputText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Gönder",
                        tint = if (inputText.isNotBlank()) Color(0xFF00F2FF) else Color.Gray
                    )
                }
            }
        }
    }

    // Bottom Sheet Removed
}

@Composable
fun AttachmentOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        FilledIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color),
            modifier = Modifier.size(56.dp)
        ) {
            Icon(icon, contentDescription = text, tint = Color.White)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text, color = Color.White, fontSize = 14.sp)
    }
}

// --- YARDIMCI BİLEŞENLER ---

@Composable
fun MessageBubble(message: ChatMessage) {
    val isMe = message.isFromMe
    val align = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    // Renkler: Sizinki Koyu Yeşil (WhatsApp tarzı), Karşı taraf Koyu Gri
    val color = if (isMe) Color(0xFF005C4B) else Color(0xFF1F2C34)

    // Balon Şekli: Konuşma yönüne göre köşeleri yuvarla
    val shape = if (isMe) RoundedCornerShape(12.dp, 0.dp, 12.dp, 12.dp)
    else RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        contentAlignment = align
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 280.dp)
                .clip(shape)
                .background(color)
                .padding(12.dp)
        ) {

            when (message.type) {
                MessageType.IMAGE -> {
                    if (message.attachmentPath != null) {
                        ChatImage(
                            path = message.attachmentPath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        Text(text = "📷 Fotoğraf (Yüklenemedi)", color = Color.White)
                    }
                    // Metin varsa altına ekle
                    if (message.text.isNotEmpty() && message.text != "📷 Fotoğraf") {
                         Text(
                            text = message.text,
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                MessageType.FILE -> {
                     Text(
                        text = "📁 ${message.text}", 
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                else -> {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }
            }

            // Saat Bilgisi
            Text(
                text = formatTimestamp(message.timestamp),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 10.sp,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun TypingBubble() {
    Box(
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(0.dp, 12.dp, 12.dp, 12.dp))
            .background(Color(0xFF1F2C34))
            .padding(12.dp)
    ) {
        // Basit bir animasyonlu nokta efekti eklenebilir, şimdilik statik
        Text("...", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 18.sp)
    }
}