package com.example.polywake

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.app.AlarmManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.work.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import java.time.temporal.ChronoUnit
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.zIndex

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkAndRequestPermissions()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            androidx.core.app.ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), 100)
        }

        if (!Settings.canDrawOverlays(this)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("Affichage du réveil")
                .setMessage("Pour que le réveil puisse s'afficher sur votre écran verrouillé, vous devez autoriser PolyWake à apparaître par-dessus les autres applications.")
                .setPositiveButton("Autoriser") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Plus tard", null)
                .show()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Précision de l'alarme")
                    .setMessage("Pour garantir que le réveil sonne exactement à la bonne heure, veuillez autoriser les alarmes exactes pour PolyWake.")
                    .setPositiveButton("Autoriser") { _, _ ->
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:$packageName")
                        )
                        startActivity(intent)
                    }
                    .setNegativeButton("Plus tard", null)
                    .show()
            }
        }
    }
}

@Composable
fun AppNavigation() {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    val scope = rememberCoroutineScope()
    var isConfigured by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        val prefs = settingsManager.userSettingsFlow.first()
        isConfigured = prefs.groups.isNotEmpty()
    }

    if (isConfigured == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator()
        }
    } else if (isConfigured == false) {
        SetupScreen(settingsManager) {
            scope.launch {
                isConfigured = true
                scheduleDailyWorkerAt1AM(context)
            }
        }
    } else {
        MainScreen(settingsManager)
    }
}

@Composable
fun MainScreen(settingsManager: SettingsManager) {
    var currentScreen by remember { mutableStateOf("home") }
    // NOUVEAU : Un déclencheur pour signaler qu'on veut revenir à aujourd'hui
    var resetHomeTrigger by remember { mutableStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Notifications, contentDescription = "Alarmes") },
                    label = { Text("Alarmes") },
                    selected = currentScreen == "alarms",
                    onClick = { currentScreen = "alarms" }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Accueil") },
                    label = { Text("Accueil") },
                    selected = currentScreen == "home",
                    onClick = {
                        if (currentScreen == "home") {
                            // Si on est DÉJÀ sur l'accueil, on envoie le signal de retour à aujourd'hui
                            resetHomeTrigger++
                        } else {
                            currentScreen = "home"
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Paramètres") },
                    label = { Text("Paramètres") },
                    selected = currentScreen == "settings",
                    onClick = { currentScreen = "settings" }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (currentScreen) {
                "alarms" -> AlarmsScreen(settingsManager)
                // On passe le déclencheur à HomeScreen
                "home" -> HomeScreen(settingsManager, resetHomeTrigger)
                "settings" -> SetupScreen(settingsManager, onSave = { currentScreen = "home" })
            }
        }
    }
}

@Composable
fun SetupScreen(settingsManager: SettingsManager, onSave: () -> Unit) {
    val scope = rememberCoroutineScope()
    var availableGroups by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var selectedGroups by remember { mutableStateOf(setOf<String>()) }
    var address by remember { mutableStateOf("") }
    var xTime by remember { mutableStateOf("10") }
    var zTime by remember { mutableStateOf("15") }
    var nAlarms by remember { mutableStateOf("3") }
    var interval by remember { mutableStateOf("5") }
    var showGroupDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val prefs = settingsManager.userSettingsFlow.first()
        selectedGroups = prefs.groups
        address = prefs.address
        xTime = prefs.x_time.toString()
        zTime = prefs.z_time.toString()
        nAlarms = prefs.n_alarms.toString()
        interval = prefs.interval.toString()
        try {
            availableGroups = NetworkManager.fetchGroups()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Configuration de l'alarme", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse du domicile") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = xTime, onValueChange = { xTime = it }, label = { Text("Temps pour se lever (min)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = zTime, onValueChange = { zTime = it }, label = { Text("Temps pour sortir (min)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = nAlarms, onValueChange = { nAlarms = it }, label = { Text("Nombre de réveils") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = interval, onValueChange = { interval = it }, label = { Text("Intervalle entre réveils (min)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = { showGroupDialog = true }, modifier = Modifier.fillMaxWidth()) {
            Text(if (selectedGroups.isEmpty()) "Sélectionner vos groupes..." else "${selectedGroups.size} groupe(s) sélectionné(s)")
        }
        Button(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            onClick = {
                scope.launch {
                    settingsManager.saveSettings(
                        UserSettings(
                            x_time = xTime.toIntOrNull() ?: 10,
                            z_time = zTime.toIntOrNull() ?: 15,
                            address = address,
                            n_alarms = nAlarms.toIntOrNull() ?: 3,
                            interval = interval.toIntOrNull() ?: 5,
                            groups = selectedGroups
                        )
                    )
                    onSave()
                }
            }
        ) {
            Text("Sauvegarder et Activer")
        }
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Rechercher et choisir vos groupes") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = { Text("Rechercher (ex: INFO5)...") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true
                    )
                    val filteredGroups = availableGroups.filter { it.second.contains(searchQuery, ignoreCase = true) }
                    if (availableGroups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(filteredGroups) { (id, label) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { selectedGroups = if (selectedGroups.contains(id)) selectedGroups - id else selectedGroups + id }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = selectedGroups.contains(id), onCheckedChange = { isChecked -> selectedGroups = if (isChecked) selectedGroups + id else selectedGroups - id })
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = { showGroupDialog = false }) { Text("Valider") } }
        )
    }
}

@Composable
fun AlarmsScreen(settingsManager: SettingsManager) {
    var scheduledAlarms by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefsState by settingsManager.userSettingsFlow.collectAsState(initial = UserSettings())
    val isAlarmsEnabled = prefsState.alarms_enabled

    LaunchedEffect(isAlarmsEnabled) {
        try {
            val alarmResult = executeAlarmCheck(context, LocalDate.now(), useCache = true)
            if (alarmResult != null) {
                scheduledAlarms = alarmResult.second
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Mes Réveils", style = MaterialTheme.typography.headlineMedium)
            Switch(
                checked = isAlarmsEnabled,
                onCheckedChange = { isChecked ->
                    scope.launch {
                        settingsManager.saveSettings(prefsState.copy(alarms_enabled = isChecked))
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (scheduledAlarms.isEmpty()) {
            Text("Aucune alarme calculée pour aujourd'hui.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Indication visuelle si les alarmes sont coupées
                    if (isAlarmsEnabled) {
                        Text("⏰ Alarmes actives :", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    } else {
                        Text("🔕 Alarmes désactivées (ne sonneront pas) :", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    scheduledAlarms.forEach { alarme ->
                        Text("• $alarme", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))
        Text("Outils de développement", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                scope.launch {
                    val tomorrow = LocalDate.now().plusDays(1)
                    val result = executeAlarmCheck(context, tomorrow)
                    if (result != null) {
                        scheduledAlarms = result.second
                        android.widget.Toast.makeText(context, "Alarmes de J+1 configurées !", android.widget.Toast.LENGTH_LONG).show()
                    } else {
                        android.widget.Toast.makeText(context, "Aucun cours demain.", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Programmer pour Demain (J+1)")
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = {
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
                val triggerTime = java.time.Instant.now().plusSeconds(3)
                setSystemAlarmClock(context, alarmManager, triggerTime, "Ceci est un test (3 sec) !", 888)
                android.widget.Toast.makeText(context, "Alarme configurée dans 3 sec ! Verrouillez l'écran.", android.widget.Toast.LENGTH_LONG).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("TESTER UNE ALARME (3 SEC)")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(settingsManager: SettingsManager, resetTrigger: Int = 0) {
    val context = LocalContext.current
    val prefsState by settingsManager.userSettingsFlow.collectAsState(initial = UserSettings())

    val baseDate = remember { LocalDate.now() }
    val initialPage = 5000
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { 10000 })

    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60.seconds) // Attend 60 secondes
            currentTime = LocalTime.now()
        }
    }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger > 0) {
            pagerState.animateScrollToPage(initialPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val newDate = baseDate.plusDays((pagerState.currentPage - initialPage).toLong())
        if (selectedDate != newDate) {
            selectedDate = newDate
        }
    }

    LaunchedEffect(selectedDate) {
        val targetPage = initialPage + ChronoUnit.DAYS.between(baseDate, selectedDate).toInt()
        if (pagerState.currentPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        selectedDate = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("Valider") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Annuler") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val formatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", Locale.FRANCE)
            Text(
                text = selectedDate.format(formatter).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                datePickerState.selectedDateMillis = selectedDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
                showDatePicker = true
            }) {
                Icon(Icons.Default.DateRange, contentDescription = "Choisir une date")
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f)
        ) { page ->
            val pageDate = baseDate.plusDays((page - initialPage).toLong())
            val dateStr = pageDate.format(DateTimeFormatter.ISO_LOCAL_DATE)

            var dayCourses by remember { mutableStateOf<List<Course>>(emptyList()) }
            var isLoading by remember { mutableStateOf(true) }

            // État pour afficher la modale du cours sélectionné
            var selectedCourse by remember { mutableStateOf<Course?>(null) }

            LaunchedEffect(pageDate, prefsState.groups) {
                isLoading = true
                val cached = NetworkManager.getCachedEvents(context, dateStr)
                if (cached.isNotEmpty()) {
                    dayCourses = cached.sortedBy { it.start_at }
                    isLoading = false
                }
                try {
                    if (prefsState.groups.isNotEmpty()) {
                        val courses = NetworkManager.getEvents(context, dateStr, dateStr, prefsState.groups)
                        dayCourses = courses.sortedBy { it.start_at }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }

            // --- MODALE DE DÉTAIL DU COURS ---
            if (selectedCourse != null) {
                val course = selectedCourse!!
                val start = OffsetDateTime.parse(course.start_at).format(DateTimeFormatter.ofPattern("HH:mm"))
                val end = OffsetDateTime.parse(course.end_at).format(DateTimeFormatter.ofPattern("HH:mm"))

                AlertDialog(
                    onDismissRequest = { selectedCourse = null },
                    title = { Text(course.modules_for_blocks ?: "Détails du cours") },
                    text = {
                        Column {
                            Text("⏰ Heure : $start - $end")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("📍 Salle : ${course.rooms_for_blocks ?: "Non précisée"}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("👨‍🏫 Professeur : ${course.teachers_for_blocks ?: "Non précisé"}")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("🏷️ Catégorie : ${course.categories ?: "Non précisée"}")
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedCourse = null }) { Text("Fermer") }
                    }
                )
            }

            Box(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                if (isLoading && dayCourses.isEmpty()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.TopCenter).padding(top = 32.dp))
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (dayCourses.isEmpty()) {
                            Text(
                                text = "Aucun cours prévu 😎",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }

                        val startHour = 8
                        val endHour = 18
                        val totalHours = endHour - startHour
                        val hourHeight = 75.dp
                        val totalHeight = hourHeight * totalHours

                        // ALGORITHME DE CHEVAUCHEMENT
                        val courseLayouts = remember(dayCourses) {
                            val layouts = mutableMapOf<Course, Pair<Int, Int>>() // Course -> (colIndex, totalCols)
                            val overlappingGroups = mutableListOf<MutableList<Course>>()

                            dayCourses.forEach { course ->
                                val start = OffsetDateTime.parse(course.start_at).toEpochSecond()
                                val end = OffsetDateTime.parse(course.end_at).toEpochSecond()
                                val group = overlappingGroups.find { group ->
                                    group.any { other ->
                                        val oStart = OffsetDateTime.parse(other.start_at).toEpochSecond()
                                        val oEnd = OffsetDateTime.parse(other.end_at).toEpochSecond()
                                        start < oEnd && end > oStart
                                    }
                                }
                                if (group != null) group.add(course) else overlappingGroups.add(mutableListOf(course))
                            }

                            overlappingGroups.forEach { group ->
                                val columns = mutableListOf<MutableList<Course>>()
                                group.forEach { course ->
                                    val start = OffsetDateTime.parse(course.start_at).toEpochSecond()
                                    val end = OffsetDateTime.parse(course.end_at).toEpochSecond()
                                    val col = columns.find { col ->
                                        col.none { other ->
                                            val oStart = OffsetDateTime.parse(other.start_at).toEpochSecond()
                                            val oEnd = OffsetDateTime.parse(other.end_at).toEpochSecond()
                                            start < oEnd && end > oStart
                                        }
                                    }
                                    if (col != null) col.add(course) else columns.add(mutableListOf(course))
                                }
                                val totalCols = columns.size
                                columns.forEachIndexed { colIndex, colCourses ->
                                    colCourses.forEach { course -> layouts[course] = Pair(colIndex, totalCols) }
                                }
                            }
                            layouts
                        }

                        Row(modifier = Modifier.height(totalHeight).fillMaxWidth()) {
                            Column(modifier = Modifier.width(60.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                                for (hour in startHour..endHour) {
                                    Text(
                                        text = String.format(Locale.getDefault(), "%02d h", hour),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(start = 4.dp)
                                    )
                                }
                            }

                            // On utilise BoxWithConstraints pour diviser la largeur pour les colonnes
                            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                val parentWidth = maxWidth

                                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                    for (hour in startHour..endHour) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 1.dp)
                                    }
                                }

                                dayCourses.forEach { course ->
                                    val startDateTime = OffsetDateTime.parse(course.start_at)
                                    val endDateTime = OffsetDateTime.parse(course.end_at)
                                    val startMinutesFrom8 = (startDateTime.hour - startHour) * 60 + startDateTime.minute
                                    val durationMinutes = Duration.between(startDateTime, endDateTime).toMinutes().coerceAtLeast(30)

                                    val topOffset = (startMinutesFrom8 / 60f) * hourHeight.value
                                    val blockHeight = (durationMinutes / 60f) * hourHeight.value

                                    val (colIndex, totalCols) = courseLayouts[course] ?: Pair(0, 1)
                                    val courseWidth = parentWidth / totalCols
                                    val offsetX = courseWidth * colIndex

                                    Surface(
                                        modifier = Modifier
                                            .width(courseWidth)
                                            .offset(x = offsetX, y = topOffset.dp)
                                            .padding(horizontal = 2.dp)
                                            .height(blockHeight.dp.coerceAtLeast(70.dp))
                                            .clickable { selectedCourse = course },
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.small
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize().padding(6.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            // HAUT : Heure et Catégorie sur la même ligne
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${startDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} - ${endDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                                if (totalCols < 3) {
                                                    Text(
                                                        text = course.categories ?: "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }

                                            // MILIEU : Titre du cours (prend l'espace restant sans déborder)
                                            Box(
                                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = course.modules_for_blocks ?: "Cours",
                                                    style = MaterialTheme.typography.titleSmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            // BAS : Professeur et Salle
                                            if (totalCols < 3) {
                                                Column(modifier = Modifier.fillMaxWidth()) {
                                                    if (!course.teachers_for_blocks.isNullOrBlank()) {
                                                        Text(
                                                            text = course.teachers_for_blocks,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (!course.rooms_for_blocks.isNullOrBlank()) {
                                                        Text(
                                                            text = "📍 ${course.rooms_for_blocks}",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (pageDate == LocalDate.now()) {
                                    val currentMinutes = (currentTime.hour - startHour) * 60 + currentTime.minute
                                    if (currentMinutes >= 0 && currentTime.hour < endHour) {
                                        val indicatorY = (currentMinutes / 60f) * hourHeight.value
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .offset(y = indicatorY.dp - 4.dp)
                                                .zIndex(10f), // La syntaxe correcte
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .offset(x = (-4).dp)
                                                    .background(androidx.compose.ui.graphics.Color.Red, androidx.compose.foundation.shape.CircleShape)
                                            )
                                            HorizontalDivider(
                                                color = androidx.compose.ui.graphics.Color.Red,
                                                thickness = 2.dp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

fun scheduleDailyWorkerAt1AM(context: Context) {
    val now = LocalDateTime.now()
    var target = now.with(LocalTime.of(1, 0))
    if (now.isAfter(target) || now.isEqual(target)) {
        target = target.plusDays(1)
    }
    val initialDelay = Duration.between(now, target).toMillis()
    val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    val workRequest = PeriodicWorkRequestBuilder<DailyEdtWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
        .setConstraints(constraints)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork("EdT_Worker_1AM", ExistingPeriodicWorkPolicy.UPDATE, workRequest)
}