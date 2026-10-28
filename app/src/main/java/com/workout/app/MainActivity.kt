package com.workout.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.workout.app.anatomy.AnatomyStudioScreen
import com.workout.app.camera.CameraCoachScreen
import com.workout.app.data.BackupScreen
import com.workout.app.data.DirectTransferScreen
import com.workout.app.data.EncryptedLocalStore
import com.workout.app.data.NutritionEngine
import com.workout.app.data.NutritionLogEntry
import com.workout.app.data.Recipe
import com.workout.app.data.UpdateScreen
import com.workout.app.music.MusicScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

private val Ink = Color(0xFF0B0D0C)
private val Graphite = Color(0xFF191D1B)
private val Lime = Color(0xFFB7F34A)
private val Muted = Color(0xFFABB2AB)
private val Surface2 = Color(0xFF232A25)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(11, 13, 12)
        window.navigationBarColor = android.graphics.Color.rgb(11, 13, 12)
        setContent { WOrkoutApp() }
    }
}

private enum class MainTab(val label: Int) {
    HOME(R.string.nav_home), WORKOUTS(R.string.nav_workouts), NUTRITION(R.string.nav_nutrition),
    PROGRESS(R.string.nav_progress), MORE(R.string.nav_more)
}

@Composable
private fun WOrkoutApp() {
    val context = LocalContext.current
    val localStore = remember { EncryptedLocalStore(context) }
    val loaded = remember {
        runCatching {
            val bytes = localStore.read()
            if (bytes == null) JSONObject().put("formatVersion", 1) else JSONObject(String(bytes, Charsets.UTF_8))
        }
    }
    val stored = loaded.getOrNull()
    var persistenceReady by remember { mutableStateOf(loaded.isSuccess) }
    var storageError by remember { mutableStateOf(loaded.exceptionOrNull()?.localizedMessage) }
    var tab by remember { mutableStateOf(MainTab.HOME) }
    var page by remember { mutableStateOf<String?>(null) }
    val todayKey = remember { LocalDate.now().toString() }
    var completedWorkouts by remember { mutableIntStateOf(stored?.optInt("completedWorkouts", 0) ?: 0) }
    var waterMl by remember { mutableIntStateOf(stored?.optInt("waterMl", 750) ?: 750) }
    var totalReps by remember { mutableIntStateOf(stored?.optInt("totalReps", 0) ?: 0) }
    var foodHistory by remember { mutableStateOf(loadFoodHistory(stored)) }
    var foodLog by remember { mutableStateOf(foodHistory[todayKey].orEmpty()) }
    var excludedAllergens by remember { mutableStateOf(loadExcludedAllergens(stored?.optJSONArray("excludedAllergens"))) }
    var nutritionGoal by remember {
        mutableStateOf(stored?.optString("nutritionGoal")?.takeIf { it in setOf("Fat loss", "Maintain fitness", "Muscle gain") } ?: "Maintain fitness")
    }
    var internet by remember { mutableStateOf(hasInternet(context)) }
    var beta by remember { mutableStateOf(false) }
    var selectedExercise by remember { mutableStateOf("Squat") }

    val updateFoodLog: (List<NutritionLogEntry>) -> Unit = { entries ->
        val limited = entries.take(1_000).filter { it.foodId in NutritionEngine.catalog && it.grams in 1..5_000 }
        foodLog = limited
        foodHistory = (foodHistory + (todayKey to limited)).toSortedMap().entries.toList().takeLast(60).associate { it.key to it.value }
    }

    val applyImportedState: (ByteArray) -> Unit = { bytes ->
        runCatching {
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            require(json.optInt("formatVersion", 0) == 1) { "Unsupported backup data format." }
            completedWorkouts = json.optInt("completedWorkouts", 0).coerceIn(0, 1_000_000)
            waterMl = json.optInt("waterMl", 750).coerceIn(0, 20_000)
            totalReps = json.optInt("totalReps", 0).coerceIn(0, 100_000)
            foodHistory = loadFoodHistory(json)
            foodLog = foodHistory[todayKey].orEmpty()
            excludedAllergens = loadExcludedAllergens(json.optJSONArray("excludedAllergens"))
            nutritionGoal = json.optString("nutritionGoal").takeIf { it in setOf("Fat loss", "Maintain fitness", "Muscle gain") } ?: "Maintain fitness"
            persistenceReady = true
            storageError = null
        }.onFailure { storageError = it.localizedMessage ?: "Could not restore backup." }
    }

    LaunchedEffect(completedWorkouts, waterMl, totalReps, persistenceReady, foodHistory, excludedAllergens, nutritionGoal) {
        if (persistenceReady) {
            val history = JSONObject().apply {
                foodHistory.toSortedMap().entries.toList().takeLast(60).forEach { (day, entries) ->
                    put(day, JSONArray().apply {
                        entries.take(1_000).forEach { entry ->
                            put(JSONObject().put("foodId", entry.foodId).put("grams", entry.grams))
                        }
                    })
                }
            }
            val state = JSONObject()
                .put("formatVersion", 1)
                .put("completedWorkouts", completedWorkouts.coerceIn(0, 1_000_000))
                .put("waterMl", waterMl.coerceIn(0, 20_000))
                .put("totalReps", totalReps.coerceIn(0, 100_000))
                .put("foodLogByDate", history)
                .put("excludedAllergens", JSONArray().apply { excludedAllergens.sorted().forEach { put(it) } })
                .put("nutritionGoal", nutritionGoal)
            val result = withContext(Dispatchers.IO) {
                runCatching { localStore.write(state.toString().toByteArray(Charsets.UTF_8)) }
            }
            if (result.isFailure) {
                persistenceReady = false
                storageError = result.exceptionOrNull()?.localizedMessage ?: "Encrypted save failed."
            }
        }
    }

    val closePage: () -> Unit = { page = null }
    BackHandler(enabled = page != null) { closePage() }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = Lime, onPrimary = Ink, background = Ink, onBackground = Color.White,
        surface = Graphite, onSurface = Color.White, secondary = Lime,
        surfaceVariant = Surface2, onSurfaceVariant = Muted,
        error = Color(0xFFFFB4A9)
    )) {
        when (page) {
            "camera" -> CameraCoachScreen(
                exercise = selectedExercise,
                onRepCount = {
                    totalReps = (totalReps + 1).coerceAtMost(100_000)
                },
                onBack = closePage
            )
            "anatomy" -> AnatomyStudioScreen(onBack = { page = null })
            "music" -> MusicScreen(onBack = { page = null })
            "backup" -> BackupScreen(onBack = { page = null }, onImported = applyImportedState)
            "transfer" -> DirectTransferScreen(onBack = { page = null }, onImported = applyImportedState)
            "updates" -> UpdateScreen(onBack = { page = null })
            else -> {
                if (!internet) {
                    ConnectionScreen(onRetry = { internet = hasInternet(context) }, storageError = storageError)
                } else if (!persistenceReady) {
                    RecoveryScreen(
                        message = storageError ?: "Encrypted local data could not be opened.",
                        onBackup = { page = "backup" },
                        onTransfer = { page = "transfer" }
                    )
                } else {
                    Scaffold(
                        containerColor = Ink,
                        bottomBar = {
                            NavigationBar(containerColor = Graphite) {
                                MainTab.entries.forEach { item ->
                                    val selected = tab == item
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = { tab = item },
                                        icon = {
                                            Icon(
                                                when (item) {
                                                    MainTab.HOME -> Icons.Filled.Home
                                                    MainTab.WORKOUTS -> Icons.Filled.FitnessCenter
                                                    MainTab.NUTRITION -> Icons.Filled.Restaurant
                                                    MainTab.PROGRESS -> Icons.Filled.ShowChart
                                                    MainTab.MORE -> Icons.Filled.MoreVert
                                                }, contentDescription = null
                                            )
                                        },
                                        label = { Text(stringResource(item.label), fontSize = 10.sp) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Ink, selectedTextColor = Lime,
                                            indicatorColor = Lime, unselectedIconColor = Muted,
                                            unselectedTextColor = Muted
                                        )
                                    )
                                }
                            }
                        }
                    ) { padding ->
                        Box(Modifier.fillMaxSize().padding(padding).background(Ink)) {
                            when (tab) {
                                MainTab.HOME -> HomeScreen(
                                    completedWorkouts = completedWorkouts,
                                    totalReps = totalReps,
                                    onStart = { tab = MainTab.WORKOUTS },
                                    onNutrition = { tab = MainTab.NUTRITION },
                                    onAnatomy = { page = "anatomy" }
                                )
                                MainTab.WORKOUTS -> WorkoutsScreen(
                                    onStart = { selectedExercise = it; page = "camera" },
                                    onAnatomy = { page = "anatomy" },
                                    onCompleted = { completedWorkouts++ }
                                )
                                MainTab.NUTRITION -> NutritionScreen(
                                    waterMl = waterMl,
                                    foodLog = foodLog,
                                    excludedAllergens = excludedAllergens,
                                    goal = nutritionGoal,
                                    onGoalChange = { nutritionGoal = it },
                                    onExcludedAllergensChange = { excludedAllergens = it },
                                    onAddFood = { foodId, grams ->
                                        if (foodLog.size < 1_000 && grams in 1..5_000 && foodId in NutritionEngine.catalog) {
                                            updateFoodLog(foodLog + NutritionLogEntry(foodId, grams))
                                        }
                                    },
                                    onRemoveFood = { index -> updateFoodLog(foodLog.filterIndexed { i, _ -> i != index }) },
                                    onAddRecipe = { recipe ->
                                        val additions = recipe.ingredients.map { (foodId, grams) -> NutritionLogEntry(foodId, grams) }
                                        if (foodLog.size + additions.size <= 1_000) updateFoodLog(foodLog + additions)
                                    },
                                    onAddWater = { waterMl = (waterMl + 250).coerceAtMost(20_000) }
                                )
                                MainTab.PROGRESS -> ProgressScreen(completedWorkouts, totalReps)
                                MainTab.MORE -> MoreScreen(
                                    beta = beta,
                                    onBetaChange = { beta = it },
                                    onMusic = { page = "music" },
                                    onAnatomy = { page = "anatomy" },
                                    onBackup = { page = "backup" },
                                    onTransfer = { page = "transfer" },
                                    onUpdates = { page = "updates" }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}


private fun loadFoodHistory(source: JSONObject?): Map<String, List<NutritionLogEntry>> {
    val history = source?.optJSONObject("foodLogByDate") ?: return emptyMap()
    val allowedDate = Regex("\\d{4}-\\d{2}-\\d{2}")
    val result = sortedMapOf<String, List<NutritionLogEntry>>()
    val keys = history.keys()
    while (keys.hasNext()) {
        val date = keys.next()
        if (!allowedDate.matches(date)) continue
        val array = history.optJSONArray(date) ?: continue
        val entries = mutableListOf<NutritionLogEntry>()
        for (index in 0 until minOf(array.length(), 1_000)) {
            val item = array.optJSONObject(index) ?: continue
            val foodId = item.optString("foodId")
            val grams = item.optInt("grams", 0)
            if (foodId in NutritionEngine.catalog && grams in 1..5_000) entries.add(NutritionLogEntry(foodId, grams))
        }
        result[date] = entries
    }
    return result.entries.toList().takeLast(60).associate { it.key to it.value }
}

private fun loadExcludedAllergens(array: JSONArray?): Set<String> {
    if (array == null) return emptySet()
    val allowed = setOf("dairy", "egg", "gluten", "soy", "tree-nuts")
    return (0 until minOf(array.length(), 20)).map { array.optString(it) }.filter { it in allowed }.toSet()
}

private fun hasInternet(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
}

@Composable
private fun ConnectionScreen(onRetry: () -> Unit, storageError: String?) {
    Column(Modifier.fillMaxSize().background(Ink).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.WifiOff, null, tint = Lime, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(20.dp))
        Text("WOrkout needs internet", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Connect to the internet to use the app. Personal training records remain encrypted on this device and are not uploaded.", color = Muted)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Retry connection") }
        if (storageError != null) Text("Local storage warning: $storageError", color = Color(0xFFFFB45B), fontSize = 12.sp)
    }
}

@Composable
private fun RecoveryScreen(message: String, onBackup: () -> Unit, onTransfer: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Ink).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Lock, null, tint = Color(0xFFFFB45B), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(18.dp))
        Text("Private data needs recovery", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(8.dp))
        Text("Encrypted storage could not be read. Automatic writes are disabled to avoid overwriting it.", color = Muted)
        Text(message, color = Color(0xFFFFB45B), fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onBackup, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Restore encrypted backup") }
        OutlinedButton(onClick = onTransfer) { Text("Restore from another phone") }
    }
}

@Composable
private fun HomeScreen(
    completedWorkouts: Int,
    totalReps: Int,
    onStart: () -> Unit,
    onNutrition: () -> Unit,
    onAnatomy: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    Modifier.size(54.dp).background(Lime, RoundedCornerShape(18.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("W", color = Ink, fontSize = 32.sp, fontWeight = FontWeight.Black)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("WOrkout", fontSize = 27.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
                    Text(stringResource(R.string.greeting), fontSize = 12.sp, color = Muted)
                }
                Box(
                    Modifier.size(42.dp).background(Graphite, RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Bolt, null, tint = Lime, modifier = Modifier.size(22.dp))
                }
            }
        }
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(
                            listOf(Color(0xFF293B20), Color(0xFF1A211B), Color(0xFF151917))
                        ),
                        RoundedCornerShape(30.dp)
                    )
                    .padding(22.dp)
            ) {
                Icon(
                    Icons.Filled.FitnessCenter,
                    null,
                    tint = Lime.copy(alpha = 0.15f),
                    modifier = Modifier.align(Alignment.TopEnd).size(94.dp).padding(4.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(Lime, RoundedCornerShape(4.dp)))
                        Text(
                            stringResource(R.string.home_overline),
                            color = Lime,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.2.sp
                        )
                    }
                    Text(
                        stringResource(R.string.home_hero_title),
                        modifier = Modifier.fillMaxWidth(0.88f),
                        fontSize = 31.sp,
                        fontWeight = FontWeight.Black,
                        lineHeight = 35.sp,
                        letterSpacing = (-0.7).sp
                    )
                    Text(
                        stringResource(R.string.home_hero_body),
                        modifier = Modifier.fillMaxWidth(0.94f),
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(Modifier.height(1.dp))
                    Button(
                        onClick = onStart,
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                        shape = RoundedCornerShape(17.dp),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 13.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.start_workout), fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(
                    stringResource(R.string.home_stat_workouts),
                    completedWorkouts.toString(),
                    stringResource(R.string.home_stat_workouts_hint),
                    Modifier.weight(1f)
                )
                StatTile(
                    stringResource(R.string.home_stat_reps),
                    totalReps.toString(),
                    stringResource(R.string.home_stat_reps_hint),
                    Modifier.weight(1f)
                )
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.home_today_heading), fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.home_today_subtitle), color = Muted, fontSize = 12.sp)
                }
                Icon(Icons.Filled.ArrowForward, null, tint = Lime, modifier = Modifier.size(21.dp))
            }
        }
        item {
            Card(
                onClick = onStart,
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(24.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier.size(60.dp).background(Color(0xFF303D26), RoundedCornerShape(19.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Lime, modifier = Modifier.size(30.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.home_recommended_title), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.home_recommended_subtitle), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(stringResource(R.string.start_workout), color = Lime, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Icon(Icons.Filled.ChevronRight, null, tint = Muted, modifier = Modifier.size(25.dp))
                }
            }
        }
        item {
            Text(stringResource(R.string.home_quick_actions), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(
                    stringResource(R.string.home_anatomy_title),
                    stringResource(R.string.home_anatomy_subtitle),
                    Icons.Filled.ViewInAr,
                    Modifier.weight(1f),
                    onAnatomy
                )
                FeatureCard(
                    stringResource(R.string.home_nutrition_title),
                    stringResource(R.string.home_nutrition_subtitle),
                    Icons.Filled.Restaurant,
                    Modifier.weight(1f),
                    onNutrition
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.Security, null, tint = Lime, modifier = Modifier.size(26.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(stringResource(R.string.saved_data), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.no_cloud), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }
        }
    }
}
@Composable
private fun WorkoutsScreen(onStart: (String) -> Unit, onAnatomy: () -> Unit, onCompleted: () -> Unit) {
    var seconds by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(false) }
    LaunchedEffect(running) {
        while (running) {
            kotlinx.coroutines.delay(1000)
            seconds++
        }
    }
    data class ExerciseCard(val id: String, val title: Int, val description: Int, val prescription: Int, val number: String)
    val exercises = listOf(
        ExerciseCard("Squat", R.string.exercise_squat, R.string.exercise_squat_description, R.string.exercise_squat_sets, "01"),
        ExerciseCard("Push-up", R.string.exercise_push_up, R.string.exercise_push_up_description, R.string.exercise_push_up_sets, "02"),
        ExerciseCard("Bicep curl", R.string.exercise_curl, R.string.exercise_curl_description, R.string.exercise_curl_sets, "03"),
        ExerciseCard("Lunge", R.string.exercise_lunge, R.string.exercise_lunge_description, R.string.exercise_lunge_sets, "04")
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.workouts_overline),
                    color = Lime,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(stringResource(R.string.workouts_intro_title), fontSize = 28.sp, fontWeight = FontWeight.Black, lineHeight = 32.sp)
                Text(stringResource(R.string.workouts_intro_body), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(26.dp)
            ) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF2A3824), Color(0xFF202720), Surface2)
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(42.dp).background(Color(0xFF35472A), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Timer, null, tint = Lime, modifier = Modifier.size(23.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.workouts_session_label), color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                stringResource(if (running) R.string.workouts_timer_running else R.string.workouts_timer_ready),
                                color = Lime,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            "LIVE",
                            color = if (running) Ink else Muted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier
                                .background(if (running) Lime else Color(0xFF303831), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                    Text(
                        String.format("%02d:%02d", seconds / 60, seconds % 60),
                        fontSize = 46.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        letterSpacing = (-1.1).sp,
                        lineHeight = 50.sp
                    )
                    Text(
                        stringResource(R.string.workouts_timer_hint),
                        color = Muted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { running = !running },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(if (running) R.string.workouts_timer_pause else R.string.workouts_timer_start), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = {
                                running = false
                                if (seconds > 0) onCompleted()
                                seconds = 0
                            },
                            enabled = seconds > 0,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(stringResource(R.string.workouts_timer_finish), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(stringResource(R.string.workouts_choose_header), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.workouts_choose_subtitle), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                }
                Text("04", color = Lime, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        items(exercises.size) { index ->
            val exercise = exercises[index]
            Card(
                colors = CardDefaults.cardColors(containerColor = Graphite),
                shape = RoundedCornerShape(22.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(54.dp).background(Surface2, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.FitnessCenter, null, tint = Lime, modifier = Modifier.size(25.dp))
                    }
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(stringResource(exercise.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
                        Text(stringResource(exercise.description), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        Text(stringResource(exercise.prescription), color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(exercise.number, color = Color(0xFF65705F), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        IconButton(
                            onClick = { onStart(exercise.id) },
                            modifier = Modifier.size(40.dp).background(Color(0xFF2E3B25), CircleShape)
                        ) {
                            Icon(Icons.Filled.PlayArrow, stringResource(R.string.workouts_camera_start), tint = Lime)
                        }
                    }
                }
            }
        }
        item {
            Card(
                onClick = onAnatomy,
                colors = CardDefaults.cardColors(containerColor = Surface2),
                shape = RoundedCornerShape(18.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Icon(Icons.Filled.ViewInAr, null, tint = Lime, modifier = Modifier.size(25.dp))
                    Text(stringResource(R.string.workouts_anatomy_link), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Icon(Icons.Filled.ChevronRight, null, tint = Muted)
                }
            }
        }
    }
}
@Composable
private fun NutritionScreen(
    waterMl: Int,
    foodLog: List<NutritionLogEntry>,
    excludedAllergens: Set<String>,
    goal: String,
    onGoalChange: (String) -> Unit,
    onExcludedAllergensChange: (Set<String>) -> Unit,
    onAddFood: (String, Int) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddRecipe: (Recipe) -> Unit,
    onAddWater: () -> Unit
) {
    val context = LocalContext.current
    val safeFoods = NutritionEngine.foods.filter { food -> food.allergens.none(excludedAllergens::contains) }
    val safeRecipes = NutritionEngine.recipes.filter { recipe ->
        recipe.ingredients.keys.mapNotNull(NutritionEngine.catalog::get)
            .none { food -> food.allergens.any(excludedAllergens::contains) }
    }
    var selectedFoodId by remember { mutableStateOf(safeFoods.firstOrNull()?.id.orEmpty()) }
    var selectedPortionGrams by remember { mutableFloatStateOf(100f) }
    var foodMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(excludedAllergens) {
        if (safeFoods.none { it.id == selectedFoodId }) selectedFoodId = safeFoods.firstOrNull()?.id.orEmpty()
    }
    LaunchedEffect(selectedFoodId) {
        selectedPortionGrams = safeFoods.firstOrNull { it.id == selectedFoodId }
            ?.servingGrams?.toFloat()?.coerceIn(25f, 500f) ?: 100f
    }
    val selectedFood = safeFoods.firstOrNull { it.id == selectedFoodId }
    val summary = NutritionEngine.summarizeEntries(foodLog)
    val currentConflicts = foodLog.count { entry ->
        NutritionEngine.catalog[entry.foodId]?.allergens?.any(excludedAllergens::contains) == true
    }
    val allergenOptions = listOf(
        "dairy" to R.string.nutrition_allergen_dairy,
        "egg" to R.string.nutrition_allergen_egg,
        "gluten" to R.string.nutrition_allergen_gluten,
        "soy" to R.string.nutrition_allergen_soy,
        "tree-nuts" to R.string.nutrition_allergen_tree_nuts
    )
    val goalOptions = listOf(
        "Fat loss" to R.string.nutrition_goal_fat_loss,
        "Maintain fitness" to R.string.nutrition_goal_maintain,
        "Muscle gain" to R.string.nutrition_goal_gain
    )

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.nutrition_overline),
                    color = Lime,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(stringResource(R.string.nav_nutrition), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.nutrition_intro), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(25.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF2B3924), Color(0xFF202720), Surface2)
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.nutrition_log_header), color = Muted, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                stringResource(R.string.nutrition_calories_total, summary.calories),
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                lineHeight = 37.sp,
                                color = Color.White
                            )
                        }
                        Box(
                            Modifier.size(44.dp).background(Color(0xFF35472A), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Restaurant, null, tint = Lime, modifier = Modifier.size(23.dp))
                        }
                    }
                    Text(stringResource(R.string.nutrition_calorie_note), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    LinearProgressIndicator(
                        progress = (summary.calories / 2200f).coerceIn(0f, 1f),
                        color = Lime,
                        trackColor = Color(0xFF394139),
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Macro(stringResource(R.string.protein), summary.proteinGrams, Lime)
                        Macro(stringResource(R.string.carbs), summary.carbsGrams, Color(0xFF6DABFF))
                        Macro(stringResource(R.string.fat), summary.fatGrams, Color(0xFFFFB45B))
                        Macro(stringResource(R.string.nutrition_fiber), summary.fiberGrams, Color(0xFFC4A7FF))
                    }
                    HorizontalDivider(color = Color(0x303D483B))
                    Text(stringResource(R.string.nutrition_log_count, foodLog.size), color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(21.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.size(37.dp).background(Surface2, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Shield, null, tint = Lime, modifier = Modifier.size(20.dp))
                        }
                        Text(stringResource(R.string.nutrition_exclusions_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Text(stringResource(R.string.nutrition_exclusions_body), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        allergenOptions.forEach { (id, labelRes) ->
                            FilterChip(
                                selected = id in excludedAllergens,
                                onClick = {
                                    onExcludedAllergensChange(
                                        if (id in excludedAllergens) excludedAllergens - id else excludedAllergens + id
                                    )
                                },
                                label = { Text(stringResource(labelRes), fontSize = 10.sp, maxLines = 1) }
                            )
                        }
                    }
                    if (currentConflicts > 0) {
                        Text(
                            stringResource(R.string.nutrition_conflict_warning, currentConflicts),
                            color = Color(0xFFFFB45B),
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(stringResource(R.string.nutrition_goal_header), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    goalOptions.forEach { (option, labelRes) ->
                        FilterChip(
                            selected = goal == option,
                            onClick = { onGoalChange(option) },
                            label = { Text(stringResource(labelRes), fontSize = 10.sp, maxLines = 1) }
                        )
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(19.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(Modifier.size(43.dp).background(Surface2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.WaterDrop, null, tint = Color(0xFF6DABFF), modifier = Modifier.size(25.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.water), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.nutrition_water_logged, waterMl), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                    }
                    FilledTonalButton(onClick = onAddWater) {
                        Text(stringResource(R.string.nutrition_add_water), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(stringResource(R.string.nutrition_add_food), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        OutlinedButton(onClick = { foodMenuOpen = true }, enabled = safeFoods.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (selectedFood != null) stringResource(foodNameResource(selectedFood.id))
                                else stringResource(R.string.nutrition_no_foods),
                                maxLines = 1
                            )
                        }
                        DropdownMenu(expanded = foodMenuOpen, onDismissRequest = { foodMenuOpen = false }) {
                            safeFoods.forEach { food ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(foodNameResource(food.id))) },
                                    onClick = {
                                        selectedFoodId = food.id
                                        foodMenuOpen = false
                                    }
                                )
                            }
                        }
                    }
                    Button(
                        onClick = { selectedFood?.let { onAddFood(it.id, selectedPortionGrams.toInt()) } },
                        enabled = selectedFood != null && foodLog.size < 1_000,
                        colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)
                    ) {
                        Text(stringResource(R.string.nutrition_add_button), fontWeight = FontWeight.Bold)
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(17.dp)) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.nutrition_portion_header), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.nutrition_portion_size, selectedPortionGrams.toInt()), color = Lime, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Slider(
                            value = selectedPortionGrams,
                            onValueChange = { selectedPortionGrams = it },
                            valueRange = 25f..500f,
                            steps = 18,
                            colors = SliderDefaults.colors(thumbColor = Lime, activeTrackColor = Lime, inactiveTrackColor = Color(0xFF394139))
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stringResource(R.string.nutrition_portion_small), color = Muted, fontSize = 10.sp)
                            Text(stringResource(R.string.nutrition_portion_large), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
        if (foodLog.isEmpty()) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(19.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(15.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        Icon(Icons.Filled.MenuBook, null, tint = Lime, modifier = Modifier.size(25.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.nutrition_empty_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(stringResource(R.string.nutrition_empty_log), color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
                        }
                    }
                }
            }
        }
        items(foodLog.size) { index ->
            val entry = foodLog[index]
            val food = NutritionEngine.catalog[entry.foodId]
            if (food != null) {
                val itemNutrition = NutritionEngine.nutritionForGrams(entry.foodId, entry.grams)
                Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(17.dp)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(foodNameResource(food.id)), fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(
                                    R.string.nutrition_food_summary,
                                    entry.grams,
                                    itemNutrition.calories,
                                    itemNutrition.proteinGrams,
                                    itemNutrition.carbsGrams,
                                    itemNutrition.fatGrams
                                ),
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                        TextButton(onClick = { onRemoveFood(index) }) {
                            Text(stringResource(R.string.nutrition_remove_button))
                        }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.recipes), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        items(safeRecipes.size) { index ->
            val recipe = safeRecipes[index]
            val recipeNutrition = NutritionEngine.recipeNutrition(recipe)
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(19.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(Modifier.size(42.dp).background(Surface2, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.RestaurantMenu, null, tint = Lime, modifier = Modifier.size(22.dp))
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(recipeNameResource(recipe.id)), fontWeight = FontWeight.Bold, fontSize = 13.sp, lineHeight = 17.sp)
                        Text(
                            stringResource(R.string.nutrition_recipe_summary, recipeNutrition.calories, recipeNutrition.proteinGrams, recipe.prepMinutes),
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                        Text(
                            recipe.ingredients.entries.joinToString { (id, grams) -> context.getString(foodNameResource(id)) + ": " + grams + " g" },
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                    FilledTonalButton(
                        onClick = { onAddRecipe(recipe) },
                        enabled = foodLog.size + recipe.ingredients.size <= 1_000
                    ) {
                        Text(stringResource(R.string.nutrition_add_meal), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item { Text(stringResource(R.string.nutrition_disclaimer), color = Muted, fontSize = 11.sp, lineHeight = 16.sp) }
    }
}

private fun foodNameResource(foodId: String): Int = when (foodId) {
    "oats" -> R.string.food_oats
    "yogurt" -> R.string.food_yogurt
    "banana" -> R.string.food_banana
    "eggs" -> R.string.food_eggs
    "chicken" -> R.string.food_chicken
    "rice" -> R.string.food_rice
    "lentils" -> R.string.food_lentils
    "salmon" -> R.string.food_salmon
    "tofu" -> R.string.food_tofu
    "milk" -> R.string.food_milk
    "almonds" -> R.string.food_almonds
    "spinach" -> R.string.food_spinach
    "potato" -> R.string.food_potato
    "olive-oil" -> R.string.food_olive_oil
    "bread" -> R.string.food_bread
    "beans" -> R.string.food_beans
    else -> R.string.food_unknown
}

private fun recipeNameResource(recipeId: String): Int = when (recipeId) {
    "oat-bowl" -> R.string.recipe_oat_bowl
    "egg-toast" -> R.string.recipe_egg_toast
    "chicken-rice" -> R.string.recipe_chicken_rice
    "lentil-bowl" -> R.string.recipe_lentil_bowl
    "salmon-rice" -> R.string.recipe_salmon_rice
    "tofu-beans" -> R.string.recipe_tofu_beans
    else -> R.string.recipe_unknown
}


@Composable
private fun ProgressScreen(completedWorkouts: Int, totalReps: Int) {
    val milestones = listOf(
        Triple(1, R.string.progress_milestone_one_title, R.string.progress_milestone_one_desc),
        Triple(10, R.string.progress_milestone_ten_title, R.string.progress_milestone_ten_desc),
        Triple(50, R.string.progress_milestone_fifty_title, R.string.progress_milestone_fifty_desc)
    )
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 14.dp, end = 18.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.progress_overline),
                    color = Lime,
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(stringResource(R.string.nav_progress), fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.progress_hero_body), color = Muted, fontSize = 13.sp, lineHeight = 19.sp)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(26.dp)) {
                Column(
                    Modifier.fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                listOf(Color(0xFF2A3824), Color(0xFF1D241E), Surface2)
                            )
                        )
                        .padding(19.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(43.dp).background(Color(0xFF35472A), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.EmojiEvents, null, tint = Lime, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(stringResource(R.string.progress_totals), color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.ExtraBold)
                            Text(stringResource(R.string.progress_hero_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(11.dp)) {
                        Column(
                            Modifier.weight(1f).background(Color(0x40242B25), RoundedCornerShape(17.dp)).padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stringResource(R.string.progress_sessions), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(completedWorkouts.toString(), color = Lime, fontSize = 31.sp, fontWeight = FontWeight.Black)
                        }
                        Column(
                            Modifier.weight(1f).background(Color(0x40242B25), RoundedCornerShape(17.dp)).padding(13.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(stringResource(R.string.progress_reps), color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(totalReps.toString(), color = Lime, fontSize = 31.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(stringResource(R.string.progress_milestones), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.progress_milestones_hint), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
        items(milestones.size) { index ->
            val milestone = milestones[index]
            val achieved = completedWorkouts >= milestone.first
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(19.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        Modifier.size(46.dp).background(if (achieved) Color(0xFF35472A) else Surface2, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (achieved) Icons.Filled.CheckCircle else Icons.Filled.Flag,
                            null,
                            tint = if (achieved) Lime else Muted,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(milestone.second), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(stringResource(milestone.third), color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = (completedWorkouts.toFloat() / milestone.first.toFloat()).coerceIn(0f, 1f),
                            color = if (achieved) Lime else Color(0xFF87966E),
                            trackColor = Surface2,
                            modifier = Modifier.fillMaxWidth().height(5.dp)
                        )
                    }
                    Text(
                        "${completedWorkouts.coerceAtMost(milestone.first)}/${milestone.first}",
                        color = if (achieved) Lime else Muted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(21.dp)) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Filled.FavoriteBorder, null, tint = Lime, modifier = Modifier.size(25.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(stringResource(R.string.progress_recovery_title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.progress_recovery_body), color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
                        Text(stringResource(R.string.progress_recovery_footer), color = Lime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
@Composable
private fun MoreScreen(
    beta: Boolean,
    onBetaChange: (Boolean) -> Unit,
    onMusic: () -> Unit,
    onAnatomy: () -> Unit,
    onBackup: () -> Unit,
    onTransfer: () -> Unit,
    onUpdates: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(stringResource(R.string.nav_more), fontSize = 29.sp, fontWeight = FontWeight.Black)
                Text(stringResource(R.string.more_subtitle), color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
            }
        }
        item {
            Text(
                stringResource(R.string.more_tools_header),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard(
                    stringResource(R.string.anatomy_studio),
                    stringResource(R.string.more_anatomy_subtitle),
                    Icons.Filled.ViewInAr,
                    Modifier.weight(1f),
                    onAnatomy
                )
                FeatureCard(
                    stringResource(R.string.music),
                    stringResource(R.string.more_music_subtitle),
                    Icons.Filled.MusicNote,
                    Modifier.weight(1f),
                    onMusic
                )
            }
        }
        item {
            Text(
                stringResource(R.string.more_data_header),
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp
            )
        }
        item { SettingRow(Icons.Filled.Lock, stringResource(R.string.export_backup), stringResource(R.string.more_backup_subtitle), onBackup) }
        item { SettingRow(Icons.Filled.Wifi, stringResource(R.string.transfer), stringResource(R.string.more_transfer_subtitle), onTransfer) }
        item { SettingRow(Icons.Filled.SystemUpdate, stringResource(R.string.more_updates_title), stringResource(R.string.more_updates_subtitle), onUpdates) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(22.dp)) {
                Column(
                    Modifier.fillMaxWidth().padding(17.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(
                            Modifier.size(36.dp).background(Surface2, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Tune, null, tint = Lime, modifier = Modifier.size(19.dp))
                        }
                        Text(stringResource(R.string.update_channel), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Text(
                        stringResource(R.string.more_channel_note),
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (beta) stringResource(R.string.beta) else stringResource(R.string.stable), color = Lime, fontWeight = FontWeight.SemiBold)
                            Text(
                                stringResource(if (beta) R.string.beta_channel_description else R.string.stable_channel_description),
                                color = Muted,
                                fontSize = 11.sp
                            )
                        }
                        Switch(checked = beta, onCheckedChange = onBetaChange)
                    }
                }
            }
        }
        item {
            SettingRow(Icons.Filled.Security, stringResource(R.string.saved_data), stringResource(R.string.no_cloud), {})
        }
    }
}
@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(44.dp).background(Surface2, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Lime, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
                Text(summary, color = Muted, fontSize = 12.sp, lineHeight = 17.sp)
            }
            Icon(Icons.Filled.ChevronRight, null, tint = Muted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Column(modifier.background(Graphite, RoundedCornerShape(18.dp)).padding(12.dp)) {
        Text(label, color = Muted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(7.dp))
        Text(value, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Lime)
        Text(hint, fontSize = 10.sp, color = Muted)
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(22.dp)) {
        Column(
            Modifier.fillMaxWidth().padding(15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            Box(
                Modifier.size(47.dp).background(Surface2, RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Lime, modifier = Modifier.size(26.dp))
            }
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}

@Composable
private fun Macro(label: String, grams: Int, tint: Color) {
    Column { Text(label, color = Muted, fontSize = 11.sp); Text("${grams}g", color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}
