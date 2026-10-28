package com.workout.app.data

import org.junit.Assert.*
import org.junit.Test

class NutritionEngineTest {
    @Test fun dailySummaryCombinesMacros() {
        val summary = NutritionEngine.dailySummary(listOf("oats", "banana"))
        assertEquals(257, summary.calories)
        assertEquals(6, summary.proteinGrams)
        assertEquals(54, summary.carbsGrams)
    }

    @Test fun recipesWithAllergensAreFiltered() {
        val safe = NutritionEngine.recipesExcluding(setOf("egg", "dairy"))
        assertTrue(safe.none { recipe -> recipe.ingredients.keys.any { it in setOf("eggs", "yogurt", "milk") } })
    }

    @Test fun adultGoalEstimatorRejectsImplausibleInputs() {
        assertNull(NutritionEngine.estimateGoal(12.0, 160.0, 30, 1.2, "fat_loss"))
        assertNull(NutritionEngine.estimateGoal(65.0, 160.0, 15, 1.2, "fat_loss"))
    }

    @Test fun recipeMacrosScaleByGrams() {
        val recipe = Recipe("test", "test", mapOf("banana" to 236), 2)
        assertEquals(210, NutritionEngine.recipeNutrition(recipe).calories)
    }
    @Test fun gramsAreScaledAndDailyLogTotalsAreCorrect() {
        val banana = NutritionEngine.nutritionForGrams("banana", 118)
        assertEquals(105, banana.calories)
        val total = NutritionEngine.summarizeEntries(
            listOf(NutritionLogEntry("banana", 118), NutritionLogEntry("oats", 40))
        )
        assertEquals(257, total.calories)
        assertEquals(6, total.proteinGrams)
    }

    @Test fun unknownFoodsAndInvalidAmountsDoNotAddCalories() {
        assertEquals(0, NutritionEngine.nutritionForGrams("not-in-catalog", 100).calories)
        assertEquals(0, NutritionEngine.nutritionForGrams("banana", 0).calories)
    }

}
