package com.example.polywake

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlin.math.sqrt

class RingActivity : ComponentActivity() {

    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }

        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AlarmScreenUI(
                        onDismiss = {
                            mediaPlayer?.stop()
                            finishAndRemoveTask()
                        }
                    )
                }
            }
        }

        playAlarmSound()
    }

    private fun playAlarmSound() {
        try {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@RingActivity, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
fun AlarmScreenUI(onDismiss: () -> Unit) {
    // 1. Gestion de l'heure en temps réel
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            currentTime = LocalTime.now()
        }
    }
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    // 2. Récupération du prochain cours depuis le cache local
    var upcomingCourse by remember { mutableStateOf<Course?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        // ON AJOUTE LA DATE DU JOUR ICI :
        val todayStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val courses = NetworkManager.getCachedEvents(context, todayStr)

        upcomingCourse = courses.minByOrNull { it.start_at }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // --- HAUT : Titre et Heure ---
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "POLYWAKE",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = currentTime.format(timeFormatter),
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        // --- MILIEU : Carte du cours ---
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (upcomingCourse != null) {
                    val startDateTime = OffsetDateTime.parse(upcomingCourse!!.start_at)
                    Text(
                        text = "Prochain cours à ${startDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = upcomingCourse!!.modules_for_blocks ?: "Cours sans nom",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "📍 ${upcomingCourse!!.rooms_for_blocks ?: "Salle non précisée"}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    Text(
                        text = "Aucun cours prévu aujourd'hui !",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        // --- BAS : Cercle de désactivation avec Swipe ---
        SwipeToDismissCircle(onDismiss = onDismiss)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SwipeToDismissCircle(onDismiss: () -> Unit) {
    // Animations de pulsation infinie pour l'effet dégradé
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    // Gestion du glissement (Swipe)
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) } // NOUVEAU : Détecte si le doigt touche l'écran
    val threshold = 300f // Distance de glissement nécessaire pour éteindre

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(300.dp) // Zone tactile agrandie pour laisser de la place au glissement
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = {
                        isDragging = false
                        // Calcul de la distance parcourue avec le théorème de Pythagore
                        val distance = sqrt((offsetX * offsetX) + (offsetY * offsetY))
                        if (distance > threshold) {
                            onDismiss() // Si la distance est suffisante, on coupe l'alarme
                        } else {
                            // Sinon, le bouton revient au centre
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onDragCancel = {
                        isDragging = false
                        offsetX = 0f
                        offsetY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                )
            }
    ) {
        // Cercles d'animation en arrière-plan (qui pulsent autour du centre)
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale)
                .alpha(alpha)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .size(100.dp)
                .scale(scale * 1.3f)
                .alpha(alpha * 0.5f)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
        )

        // 1. CERCLE CENTRAL FIXE (ne bouge plus)
        Box(
            modifier = Modifier
                .size(100.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Glisser",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold
            )
        }

        // 2. CERCLE "OMBRE" (suit le doigt)
        // Il n'apparaît que lorsqu'on touche ou qu'on est en train de glisser
        if (isDragging || offsetX != 0f || offsetY != 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(60.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape) // Cercle semi-transparent gris/noir
            )
        }
    }
}