package com.whoami22888.mediavault.model

import org.junit.Assert.assertEquals
import org.junit.Test

class EditModelsTest {
    @Test
    fun normalizedRectClampsCoordinatesWithoutChangingTheRecipeShape() {
        val rect = NormalizedRect(left = -0.4f, top = 0.2f, right = 1.7f, bottom = 1.2f)

        val normalized = rect.normalized()

        assertEquals(0f, normalized.left, 0f)
        assertEquals(0.2f, normalized.top, 0f)
        assertEquals(1f, normalized.right, 0f)
        assertEquals(1f, normalized.bottom, 0f)
    }

    @Test
    fun videoRecipeDefaultsPreserveSourceUntilAnOwnerChoosesAnEdit() {
        val recipe = VideoEditRecipe()

        assertEquals(0L, recipe.trimStartMs)
        assertEquals(null, recipe.trimEndMs)
        assertEquals(false, recipe.removeAudio)
        assertEquals(null, recipe.overlayText)
    }
}
