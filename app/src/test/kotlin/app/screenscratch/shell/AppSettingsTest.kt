package app.screenscratch.shell

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.screenscratch.canvas.FrontBufferContent
import app.screenscratch.ui.theme.ThemeSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class AppSettingsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private fun prefs() = context.getSharedPreferences("settings-test", Context.MODE_PRIVATE)

    @Test
    fun defaults() {
        val s = AppSettings(prefs()) { false }
        assertEquals(ThemeSetting.System, s.theme)
        assertFalse(s.swapSideButtons)
        assertEquals(FrontBufferContent.INK_ONLY, s.frontBuffer)
    }

    @Test
    fun valuesSurviveANewInstance() {
        AppSettings(prefs()) { true }.apply {
            theme = ThemeSetting.Dark
            swapSideButtons = true
            frontBuffer = FrontBufferContent.SCENE
            penOnly = false
        }
        val s = AppSettings(prefs()) { true }
        assertEquals(ThemeSetting.Dark, s.theme)
        assertTrue(s.swapSideButtons)
        assertEquals(FrontBufferContent.SCENE, s.frontBuffer)
        assertFalse(s.penOnly)
    }

    @Test
    fun penOnlyDefaultsToWhetherTheDeviceHasAStylus() {
        assertTrue(AppSettings(prefs()) { true }.penOnly)
        assertFalse(AppSettings(prefs()) { false }.penOnly)
        AppSettings(prefs()) { false }.penOnly = true
        assertTrue(AppSettings(prefs()) { false }.penOnly)
    }

    @Test
    fun cardDates() {
        val today = LocalDate.of(2026, 11, 24)
        fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        fun fmt(ms: Long) = AppModel.formatDate(ms, today, ZoneOffset.UTC, Locale.US)
        assertEquals("Today", fmt(at(2026, 11, 24)))
        assertEquals("Yesterday", fmt(at(2026, 11, 23)))
        assertEquals("Nov 21", fmt(at(2026, 11, 21)))
        assertEquals("Dec 2, 2025", fmt(at(2025, 12, 2)))
    }

    @Test
    fun newerFormatIsRecognised() {
        assertTrue(AppModel.isNewerFormat("{\n  \"format\": 999,\n  \"id\": \"x\"}"))
        assertFalse(AppModel.isNewerFormat("{\"format\": 1}"))
        assertFalse(AppModel.isNewerFormat("not json"))
    }
}
