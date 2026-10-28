package com.workout.app.data

data class Nutrition(
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val fiberGrams: Int = 0
) {
    operator fun plus(other: Nutrition) = Nutrition(
        calories + other.calories, proteinGrams + other.proteinGrams,
        carbsGrams + other.carbsGrams, fatGrams + other.fatGrams, fiberGrams + other.fiberGrams
    )
}

data class FoodItem(
    val id: String,
    val name: String,
    val servingGrams: Int,
    val nutrition: Nutrition,
    val allergens: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val approximatePrice: Double = 0.0
)

data class Recipe(
    val id: String,
    val name: String,
    val ingredients: Map<String, Int>,
    val prepMinutes: Int,
    val tags: Set<String> = emptySet()
)

data class DailySummary(
    val calories: Int, val proteinGrams: Int, val carbsGrams: Int,
    val fatGrams: Int, val fiberGrams: Int
)

data class NutritionGoal(val calories: Int, val proteinGrams: Int, val carbsGrams: Int, val fatGrams: Int)

data class NutritionLogEntry(val foodId: String, val grams: Int)

object NutritionEngine {
    val foods = listOf(
        FoodItem("oats", "Oats", 40, Nutrition(152, 5, 27, 3, 4), tags=setOf("vegetarian","breakfast"), approximatePrice=.14),
        FoodItem("yogurt", "Plain Greek yogurt", 170, Nutrition(100, 17, 6, 0), allergens=setOf("dairy"), tags=setOf("vegetarian","breakfast"), approximatePrice=.65),
        FoodItem("banana", "Banana", 118, Nutrition(105, 1, 27, 0, 3), tags=setOf("vegan","fruit"), approximatePrice=.25),
        FoodItem("eggs", "Eggs", 100, Nutrition(143, 13, 1, 10), allergens=setOf("egg"), tags=setOf("vegetarian"), approximatePrice=.45),
        FoodItem("chicken", "Chicken breast, cooked", 120, Nutrition(198, 37, 0, 4), tags=setOf("high-protein","gluten-free"), approximatePrice=1.15),
        FoodItem("rice", "Brown rice, cooked", 150, Nutrition(167, 4, 35, 1, 2), tags=setOf("vegan","gluten-free"), approximatePrice=.22),
        FoodItem("lentils", "Lentils, cooked", 150, Nutrition(174, 13, 30, 1, 12), tags=setOf("vegan","high-fiber"), approximatePrice=.28),
        FoodItem("salmon", "Salmon, cooked", 120, Nutrition(247, 27, 0, 15), tags=setOf("high-protein","gluten-free"), approximatePrice=2.40),
        FoodItem("tofu", "Firm tofu", 120, Nutrition(173, 20, 3, 10), allergens=setOf("soy"), tags=setOf("vegan","high-protein"), approximatePrice=.70),
        FoodItem("milk", "Cow milk", 200, Nutrition(122, 6, 10, 7), allergens=setOf("dairy"), tags=setOf("vegetarian"), approximatePrice=.26),
        FoodItem("almonds", "Almonds", 20, Nutrition(116, 4, 4, 10, 2), allergens=setOf("tree-nuts"), tags=setOf("vegan"), approximatePrice=.30),
        FoodItem("spinach", "Spinach", 80, Nutrition(18, 2, 3, 0, 2), tags=setOf("vegan","vegetable"), approximatePrice=.30),
        FoodItem("potato", "Potato, baked", 180, Nutrition(167, 4, 38, 0, 4), tags=setOf("vegan","gluten-free"), approximatePrice=.20),
        FoodItem("olive-oil", "Olive oil", 10, Nutrition(88, 0, 0, 10), tags=setOf("vegan"), approximatePrice=.10),
        FoodItem("bread", "Whole grain bread", 40, Nutrition(100, 4, 18, 1, 3), allergens=setOf("gluten"), tags=setOf("vegetarian"), approximatePrice=.18),
        FoodItem("beans", "Beans, cooked", 150, Nutrition(190, 13, 34, 1, 11), tags=setOf("vegan","gluten-free"), approximatePrice=.32)
    )
    val catalog = foods.associateBy { it.id }
    val recipes = listOf(
        Recipe("oat-bowl", "Oat & banana bowl", mapOf("oats" to 40, "yogurt" to 170, "banana" to 118), 5, setOf("breakfast","vegetarian")),
        Recipe("egg-toast", "Eggs on whole-grain toast", mapOf("eggs" to 100, "bread" to 40, "spinach" to 40), 10, setOf("breakfast","vegetarian")),
        Recipe("chicken-rice", "Chicken, rice & greens", mapOf("chicken" to 120, "rice" to 150, "spinach" to 80, "olive-oil" to 5), 20, setOf("high-protein")),
        Recipe("lentil-bowl", "Lentil & potato bowl", mapOf("lentils" to 150, "potato" to 180, "spinach" to 80, "olive-oil" to 5), 20, setOf("vegan","high-fiber")),
        Recipe("salmon-rice", "Salmon with brown rice", mapOf("salmon" to 120, "rice" to 150, "spinach" to 80), 25, setOf("high-protein","gluten-free")),
        Recipe("tofu-beans", "Tofu and bean skillet", mapOf("tofu" to 120, "beans" to 100, "spinach" to 80), 18, setOf("vegan","high-protein"))
    )

    fun nutritionForGrams(foodId: String, grams: Int): Nutrition {
        val food = catalog[foodId] ?: return Nutrition(0, 0, 0, 0, 0)
        if (grams !in 1..5_000) return Nutrition(0, 0, 0, 0, 0)
        val scale = grams.toDouble() / food.servingGrams
        return Nutrition(
            (food.nutrition.calories * scale).toInt(),
            (food.nutrition.proteinGrams * scale).toInt(),
            (food.nutrition.carbsGrams * scale).toInt(),
            (food.nutrition.fatGrams * scale).toInt(),
            (food.nutrition.fiberGrams * scale).toInt()
        )
    }

    fun summarizeEntries(entries: List<NutritionLogEntry>): Nutrition =
        entries.take(1_000).fold(Nutrition(0, 0, 0, 0, 0)) { total, entry ->
            total + nutritionForGrams(entry.foodId, entry.grams)
        }

    fun recipeNutrition(recipe: Recipe): Nutrition =
        summarizeEntries(recipe.ingredients.map { (foodId, grams) -> NutritionLogEntry(foodId, grams) })


    fun dailySummary(foodIds: List<String>): DailySummary {
        val total = foodIds.mapNotNull(catalog::get).fold(Nutrition(0,0,0,0,0)) { acc, food -> acc + food.nutrition }
        return DailySummary(total.calories, total.proteinGrams, total.carbsGrams, total.fatGrams, total.fiberGrams)
    }

    fun recipesExcluding(allergens: Set<String>): List<Recipe> = recipes.filter { recipe ->
        recipe.ingredients.keys.mapNotNull(catalog::get).none { food -> food.allergens.any(allergens::contains) }
    }

    fun substitutions(foodId: String, excludedAllergens: Set<String> = emptySet()): List<FoodItem> {
        val original = catalog[foodId] ?: return emptyList()
        return foods.asSequence()
            .filter { it.id != foodId && it.allergens.none(excludedAllergens::contains) }
            .filter { food -> food.tags.intersect(original.tags).isNotEmpty() }
            .sortedBy { kotlin.math.abs(it.nutrition.calories - original.nutrition.calories) }
            .take(5)
            .toList()
    }

    /** Rough planning estimate for adults only; not a medical or dietetic prescription. */
    fun estimateGoal(weightKg: Double, heightCm: Double, ageYears: Int, activityFactor: Double, goal: String): NutritionGoal? {
        if (weightKg !in 30.0..300.0 || heightCm !in 120.0..240.0 || ageYears !in 18..100) return null
        val activity = activityFactor.coerceIn(1.2, 1.75)
        val bmr = 10 * weightKg + 6.25 * heightCm - 5 * ageYears - 78
        val maintenance = (bmr * activity).toInt().coerceAtLeast(1500)
        val calories = when (goal) {
            "fat_loss" -> (maintenance - 300).coerceAtLeast(1500)
            "muscle_gain" -> maintenance + 200
            else -> maintenance
        }
        val protein = (weightKg * 1.6).toInt().coerceAtLeast(60)
        val fat = (calories * .27 / 9).toInt().coerceAtLeast(45)
        val carbs = ((calories - protein * 4 - fat * 9).coerceAtLeast(0) / 4)
        return NutritionGoal(calories, protein, carbs, fat)
    }
}
