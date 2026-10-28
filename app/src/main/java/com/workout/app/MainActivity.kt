package com.workout.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
    var cameraSessionReps by remember { mutableIntStateOf(0) }
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

    val closePage: () -> Unit = {
        if (page == "camera") cameraSessionReps = 0
        page = null
    }
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
                onRepCount = { sessionCount ->
                    if (sessionCount > cameraSessionReps) {
                        totalReps = (totalReps + sessionCount - cameraSessionReps).coerceAtMost(100_000)
                        cameraSessionReps = sessionCount
                    }
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
                                    onAddFood = { foodId ->
                                        NutritionEngine.catalog[foodId]?.let { food ->
                                            if (foodLog.size < 1_000) updateFoodLog(foodLog + NutritionLogEntry(foodId, food.servingGrams))
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
private fun HomeScreen(completedWorkouts: Int, onStart: () -> Unit, onNutrition: () -> Unit, onAnatomy: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).background(Lime, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                    Text("W", color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Black)
                }
                Column {
                    Text("WOrkout", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(stringResource(R.string.greeting), fontSize = 12.sp, color = Muted)
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().background(
                androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF263220), Graphite, Ink)),
                RoundedCornerShape(28.dp)
            ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("TODAY · PERSONAL PLAN", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
                Text("Build strength.\nMove with control.", fontSize = 30.sp, fontWeight = FontWeight.Black, lineHeight = 34.sp)
                Text("Training that adapts to your goals, equipment and recovery feedback.", color = Muted, fontSize = 13.sp)
                Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink), shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.start_workout), fontWeight = FontWeight.Bold)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("WORKOUTS", completedWorkouts.toString(), "logged sessions", Modifier.weight(1f))
                StatTile("WEEKLY GOAL", if (completedWorkouts == 0) "0%" else "${(completedWorkouts.coerceAtMost(5) * 20)}%", "personal target", Modifier.weight(1f))
            }
        }
        item { Text(stringResource(R.string.today_plan), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FeatureCard("3D anatomy", "Explore movement", Icons.Filled.ViewInAr, Modifier.weight(1f), onAnatomy)
                FeatureCard("Nutrition", "Meals and macros", Icons.Filled.Restaurant, Modifier.weight(1f), onNutrition)
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, tint = Lime, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.saved_data), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.no_cloud), color = Muted, fontSize = 12.sp)
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
        while (running) { kotlinx.coroutines.delay(1000); seconds++ }
    }
    val exercises = listOf(
        Triple("Squat", "Legs · glutes · core", "3 × 10"),
        Triple("Push-up", "Chest · shoulders · triceps", "3 × 8"),
        Triple("Bicep curl", "Biceps · forearms", "3 × 12"),
        Triple("Lunge", "Legs · balance", "3 × 8 / side")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.nav_workouts), fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Surface2), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("SESSION TIMER", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(String.format("%02d:%02d", seconds / 60, seconds % 60), fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { running = !running }, colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text(if (running) "Pause" else stringResource(R.string.start)) }
                        OutlinedButton(onClick = { running = false; seconds = 0; onCompleted() }) { Text("Finish & log") }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.choose_exercise), fontSize = 19.sp, fontWeight = FontWeight.Bold) }
        items(exercises.size) { index ->
            val exercise = exercises[index]
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(46.dp).background(Surface2, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Filled.FitnessCenter, null, tint = Lime) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.first, fontWeight = FontWeight.Bold)
                        Text(exercise.second, color = Muted, fontSize = 12.sp)
                        Text(exercise.third, color = Lime, fontSize = 12.sp)
                    }
                    IconButton(onClick = { onStart(exercise.first) }) { Icon(Icons.Filled.Videocam, "Camera", tint = Lime) }
                }
            }
        }
        item { OutlinedButton(onClick = onAnatomy, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.ViewInAr, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.anatomy_studio)) } }
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
    onAddFood: (String) -> Unit,
    onRemoveFood: (Int) -> Unit,
    onAddRecipe: (Recipe) -> Unit,
    onAddWater: () -> Unit
) {
    val safeFoods = NutritionEngine.foods.filter { food -> food.allergens.none(excludedAllergens::contains) }
    val safeRecipes = NutritionEngine.recipes.filter { recipe ->
        recipe.ingredients.keys.mapNotNull(NutritionEngine.catalog::get)
            .none { food -> food.allergens.any(excludedAllergens::contains) }
    }
    var selectedFoodId by remember { mutableStateOf(safeFoods.firstOrNull()?.id.orEmpty()) }
    var foodMenuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(excludedAllergens) {
        if (safeFoods.none { it.id == selectedFoodId }) selectedFoodId = safeFoods.firstOrNull()?.id.orEmpty()
    }
    val selectedFood = safeFoods.firstOrNull { it.id == selectedFoodId }
    val summary = NutritionEngine.summarizeEntries(foodLog)
    val currentConflicts = foodLog.count { entry ->
        NutritionEngine.catalog[entry.foodId]?.allergens?.any(excludedAllergens::contains) == true
    }
    val allergenOptions = listOf(
        "dairy" to "Dairy", "egg" to "Egg", "gluten" to "Gluten", "soy" to "Soy", "tree-nuts" to "Tree nuts"
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.nav_nutrition), fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TODAY'S FOOD LOG", color = Lime, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text("${summary.calories} ${stringResource(R.string.calories)}", fontSize = 34.sp, fontWeight = FontWeight.Black)
                    Text("Reference only: 2,200 kcal is not a personalized target.", color = Muted, fontSize = 11.sp)
                    LinearProgressIndicator(progress = (summary.calories / 2200f).coerceIn(0f, 1f), color = Lime, trackColor = Surface2, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Macro("Protein", summary.proteinGrams, Lime)
                        Macro("Carbs", summary.carbsGrams, Color(0xFF6DABFF))
                        Macro("Fat", summary.fatGrams, Color(0xFFFFB45B))
                        Macro("Fiber", summary.fiberGrams, Color(0xFFC4A7FF))
                    }
                    Text("${foodLog.size} logged items · stored on device by date", color = Muted, fontSize = 11.sp)
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dietary exclusions", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Excluded allergens are hidden from food selection and recipe suggestions.", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        allergenOptions.take(3).forEach { (id, label) ->
                            FilterChip(selected = id in excludedAllergens, onClick = {
                                onExcludedAllergensChange(if (id in excludedAllergens) excludedAllergens - id else excludedAllergens + id)
                            }, label = { Text(label, fontSize = 10.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        allergenOptions.drop(3).forEach { (id, label) ->
                            FilterChip(selected = id in excludedAllergens, onClick = {
                                onExcludedAllergensChange(if (id in excludedAllergens) excludedAllergens - id else excludedAllergens + id)
                            }, label = { Text(label, fontSize = 10.sp) })
                        }
                    }
                    if (currentConflicts > 0) {
                        Text("Warning: $currentConflicts logged items include a currently excluded allergen. Review or remove them.", color = Color(0xFFFFB45B), fontSize = 11.sp)
                    }
                }
            }
        }
        item {
            Text("Goal", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("Fat loss", "Maintain fitness", "Muscle gain").forEach { option ->
                    FilterChip(selected = goal == option, onClick = { onGoalChange(option) }, label = { Text(option, fontSize = 10.sp) })
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, null, tint = Lime, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.water), fontWeight = FontWeight.Bold)
                        Text("${waterMl} ml logged · optional reminder", color = Muted, fontSize = 12.sp)
                    }
                    FilledTonalButton(onClick = onAddWater) { Text("+250") }
                }
            }
        }
        item {
            Text("Add food", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(onClick = { foodMenuOpen = true }, enabled = safeFoods.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                        Text(selectedFood?.name ?: "No foods match exclusions")
                    }
                    DropdownMenu(expanded = foodMenuOpen, onDismissRequest = { foodMenuOpen = false }) {
                        safeFoods.forEach { food ->
                            DropdownMenuItem(text = { Text(food.name) }, onClick = {
                                selectedFoodId = food.id
                                foodMenuOpen = false
                            })
                        }
                    }
                }
                Button(onClick = { selectedFood?.let { onAddFood(it.id) } }, enabled = selectedFood != null && foodLog.size < 1_000,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink)) { Text("Add") }
            }
        }
        if (foodLog.isEmpty()) {
            item { Text("Nothing logged yet. Choose a food or add a recipe below.", color = Muted, fontSize = 12.sp) }
        }
        items(foodLog.size) { index ->
            val entry = foodLog[index]
            val food = NutritionEngine.catalog[entry.foodId]
            if (food != null) {
                val itemNutrition = NutritionEngine.nutritionForGrams(entry.foodId, entry.grams)
                Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(food.name, fontWeight = FontWeight.SemiBold)
                            Text("${entry.grams} g · ${itemNutrition.calories} kcal · P ${itemNutrition.proteinGrams} g · C ${itemNutrition.carbsGrams} g · F ${itemNutrition.fatGrams} g", color = Muted, fontSize = 11.sp)
                        }
                        TextButton(onClick = { onRemoveFood(index) }) { Text("Remove") }
                    }
                }
            }
        }
        item { Text(stringResource(R.string.recipes), fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        items(safeRecipes.size) { index ->
            val recipe = safeRecipes[index]
            val recipeNutrition = NutritionEngine.recipeNutrition(recipe)
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(recipe.name, fontWeight = FontWeight.Bold)
                        Text("${recipeNutrition.calories} kcal · ${recipeNutrition.proteinGrams} g protein · ${recipe.prepMinutes} min", color = Muted, fontSize = 12.sp)
                        Text(recipe.ingredients.entries.joinToString { (id, grams) -> "${NutritionEngine.catalog[id]?.name ?: id}: ${grams} g" }, color = Muted, fontSize = 10.sp)
                    }
                    FilledTonalButton(onClick = { onAddRecipe(recipe) }, enabled = foodLog.size + recipe.ingredients.size <= 1_000) { Text("Add meal") }
                }
            }
        }
        item { Text("Values are approximate. Verify food labels and ingredients; exclusions only filter the included sample catalog.", color = Muted, fontSize = 11.sp) }
    }
}

@Composable
private fun ProgressScreen(completedWorkouts: Int, totalReps: Int) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { Text(stringResource(R.string.nav_progress), fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("WORKOUTS", completedWorkouts.toString(), "logged", Modifier.weight(1f))
                StatTile("REPS", totalReps.toString(), "camera/manual", Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Recovery", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                    Text("Readiness must be based on sleep, subjective fatigue and recovery feedback. This starter build does not yet estimate medical or wearable readiness.", color = Muted, fontSize = 13.sp)
                    Text("Rest is progress, too.", color = Lime, fontWeight = FontWeight.SemiBold)
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text(stringResource(R.string.nav_more), fontSize = 28.sp, fontWeight = FontWeight.Black) }
        item { SettingRow(Icons.Filled.ViewInAr, stringResource(R.string.anatomy_studio), "Interactive stylized 3D model", onAnatomy) }
        item { SettingRow(Icons.Filled.MusicNote, stringResource(R.string.music), "Local audio and independent volume", onMusic) }
        item { SettingRow(Icons.Filled.Lock, stringResource(R.string.export_backup), "Password-protected encrypted archive", onBackup) }
        item { SettingRow(Icons.Filled.Wifi, stringResource(R.string.transfer), "Direct transfer over trusted local network", onTransfer) }
        item { SettingRow(Icons.Filled.SystemUpdate, "Stable / Beta updates", "Check releases, verify APK and signer", onUpdates) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.update_channel), fontWeight = FontWeight.Bold)
                    Text("Beta may contain unstable features. Stable releases require a maintainer-signed APK.", color = Muted, fontSize = 12.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (beta) stringResource(R.string.beta) else stringResource(R.string.stable), color = Lime, modifier = Modifier.weight(1f))
                        Switch(checked = beta, onCheckedChange = onBetaChange)
                    }
                }
            }
        }
        item { SettingRow(Icons.Filled.Security, stringResource(R.string.saved_data), stringResource(R.string.no_cloud), {}) }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Lime, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(summary, color = Muted, fontSize = 12.sp) }
            Icon(Icons.Filled.ChevronRight, null, tint = Muted)
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
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = Graphite), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, null, tint = Lime, modifier = Modifier.size(28.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun Macro(label: String, grams: Int, tint: Color) {
    Column { Text(label, color = Muted, fontSize = 11.sp); Text("${grams}g", color = tint, fontWeight = FontWeight.Bold, fontSize = 17.sp) }
}
