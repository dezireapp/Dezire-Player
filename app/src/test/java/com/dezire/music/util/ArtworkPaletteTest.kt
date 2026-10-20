package com.dezire.music.util

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Pure colour-math tests for [ArtworkPalette.Companion]. Every helper under
 * test lives entirely in Kotlin + Compose's Color value class, so it runs on
 * the JVM with no Android runtime.
 */
class ArtworkPaletteTest {

    private fun rgb(r: Float, g: Float, b: Float): Color =
        Color(red = r, green = g, blue = b)

    @Test
    fun rgbToHsv_hsvToRgb_roundTrips() {
        val colours = listOf(
            rgb(0.4f, 0.7f, 0.2f),
            rgb(0.9f, 0.05f, 0.1f),
            rgb(0.1f, 0.1f, 0.6f),
            rgb(0f, 0f, 0f),
            rgb(1f, 1f, 1f),
        )
        for (c in colours) {
            val hsv = ArtworkPalette.rgbToHsv(c)
            val back = ArtworkPalette.hsvToRgb(hsv[0], hsv[1], hsv[2])
            assertEquals(c.red, back.red, 1e-3f)
            assertEquals(c.green, back.green, 1e-3f)
            assertEquals(c.blue, back.blue, 1e-3f)
        }
    }

    @Test
    fun complementary_rotatesHueByOneEighty() {
        val base = rgb(0.2f, 0.0f, 1.0f) // hue 240
        val baseHue = ArtworkPalette.rgbToHsv(base)[0]
        val comp = ArtworkPalette.complementary(base)
        val compHue = ArtworkPalette.rgbToHsv(comp)[0]
        assertEquals((baseHue + 180f) % 360f, compHue, 1e-3f)
    }

    @Test
    fun rotateHue_fullCycle_returnsOriginal() {
        val base = rgb(0.55f, 0.2f, 0.9f)
        val roundTrip = ArtworkPalette.rotateHue(ArtworkPalette.rotateHue(base, 120f), 240f)
        assertEquals(base.red, roundTrip.red, 1e-3f)
        assertEquals(base.green, roundTrip.green, 1e-3f)
        assertEquals(base.blue, roundTrip.blue, 1e-3f)
    }

    @Test
    fun distance_isZeroForSameColour_andPositiveForDifferent() {
        val a = rgb(0.3f, 0.6f, 0.1f)
        val b = rgb(0.3f, 0.6f, 0.1f)
        val c = rgb(0.9f, 0.9f, 0.9f)
        assertEquals(0f, ArtworkPalette.distance(a, b), 1e-6f)
        assertTrue(ArtworkPalette.distance(a, c) > 0.5f)
    }

    @Test
    fun distance_isSymmetric() {
        val a = rgb(0.1f, 0.4f, 0.8f)
        val b = rgb(0.8f, 0.2f, 0.3f)
        assertEquals(
            ArtworkPalette.distance(a, b),
            ArtworkPalette.distance(b, a),
            1e-6f,
        )
    }

    @Test
    fun darkened_scalesChannels() {
        val base = rgb(0.8f, 0.4f, 0.2f)
        val dim = ArtworkPalette.darkened(base, 0.45f)
        assertEquals(base.red * 0.45f, dim.red, 1e-3f)
        assertEquals(base.green * 0.45f, dim.green, 1e-3f)
        assertEquals(base.blue * 0.45f, dim.blue, 1e-3f)
    }

    @Test
    fun darkened_clampsToValidRange() {
        val bright = rgb(0.9f, 0.9f, 0.9f)
        val boosted = ArtworkPalette.darkened(bright, 3f)
        assertEquals(1f, boosted.red, 1e-6f)
        assertEquals(1f, boosted.green, 1e-6f)
        assertEquals(1f, boosted.blue, 1e-6f)
    }

    @Test
    fun shiftedAccent_differsFromSource() {
        val base = rgb(0.3f, 0.2f, 0.7f)
        val accent = ArtworkPalette.shiftedAccent(base)
        assertTrue(ArtworkPalette.distance(base, accent) > 0.01f)
    }

    @Test
    fun fromDominant_derivesDimAndAccent() {
        val dominant = rgb(0.2f, 0.3f, 0.5f)
        val palette = ArtworkPalette.fromDominant(dominant)
        assertEquals(palette.dominant, dominant)
        assertEquals(dominant.red * 0.45f, palette.dim.red, 1e-3f)
        assertTrue(ArtworkPalette.distance(palette.dominant, palette.accent) > 0.01f)
    }

    @Test
    fun fallback_isStable() {
        val fallback = ArtworkPalette.fallback
        assertTrue(fallback.dominant != fallback.accent)
        assertTrue(fallback.dominant != fallback.dim)
    }
}