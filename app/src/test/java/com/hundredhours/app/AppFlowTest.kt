package com.hundredhours.app

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Drives the real app on the JVM (Robolectric): taps buttons like a user would,
 * checks what is shown, runs the background service, and saves screenshots
 * to app/build/outputs/roborazzi when run with `recordRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class AppFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<Application>()

    @Before
    fun freshState() {
        StudyStore.init(app)
        StudyStore.update { StudyState() }
        StudyStore.setTheme(ThemeMode.LIGHT)
        // The screen re-renders every second forever; drive the clock by hand.
        compose.mainClock.autoAdvance = false
        settle()
    }

    /**
     * Let posted work run (dialogs and menus open in their own window a moment later),
     * then advance the frozen clock so their open animations finish.
     */
    private fun settle() {
        repeat(3) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.mainClock.advanceTimeBy(1_100)
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    @Test
    fun startWeekStartsTimerAndBackgroundNotification() {
        compose.onNodeWithText("Start week").assertIsDisplayed()
        compose.onNodeWithText("Goal (hours)").assertIsDisplayed()
        shot("1_setup")

        compose.onNodeWithText("Start week").performClick()
        settle()

        val state = StudyStore.state.value
        assertTrue("week started", state.hasWeek)
        assertTrue("timer running", state.isRunning)
        assertEquals(100 * HOUR_MS, state.goalMs)
        compose.onNodeWithText("Pause").assertIsDisplayed()
        compose.onNodeWithText("left of 100h goal").assertIsDisplayed()
        compose.onNodeWithText("Day 1 of 7").assertIsDisplayed()

        // The app asked Android to start the background timer service.
        val started = shadowOf(app).nextStartedService
        assertNotNull("service started", started)
        assertEquals(TimerService::class.java.name, started.component?.className)

        // Run that service and check its notification.
        val service = Robolectric.buildService(TimerService::class.java, started).create().startCommand(0, 1).get()
        val nm = app.getSystemService(NotificationManager::class.java)
        val notification = shadowOf(nm).allNotifications.single()
        assertEquals("Studying", notification.extras.getString(Notification.EXTRA_TITLE))
        assertEquals("left of 100h goal", notification.extras.getString(Notification.EXTRA_TEXT))
        assertTrue("counts down", notification.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("Pause", notification.actions.single().title)
        assertTrue("is foreground", shadowOf(service).isForegroundStopped.not())

        // Tapping Pause in the notification stops the timer.
        val pauseIntent = shadowOf(notification.actions.single().actionIntent).savedIntent
        Robolectric.buildService(TimerService::class.java, Intent(pauseIntent)).create().startCommand(0, 2)
        assertFalse("paused from notification", StudyStore.state.value.isRunning)
        assertEquals(1, StudyStore.state.value.sessions.size)

        settle()
        compose.onNodeWithText("Start").assertIsDisplayed()
    }

    @Test
    fun pauseAndResumeFromApp() {
        compose.onNodeWithText("Start week").performClick()
        settle()
        compose.onNodeWithText("Pause").performClick()
        settle()
        assertFalse(StudyStore.state.value.isRunning)
        compose.onNodeWithText("Start").performClick()
        settle()
        assertTrue(StudyStore.state.value.isRunning)
    }

    @Test
    fun customGoal() {
        compose.onNodeWithText("100").performTextReplacement("80")
        compose.onNodeWithText("Start week").performClick()
        settle()
        assertEquals(80 * HOUR_MS, StudyStore.state.value.goalMs)
        compose.onNodeWithText("left of 80h goal").assertIsDisplayed()
    }

    @Test
    fun midWeekScreen() {
        seedMidWeek(running = true)
        compose.onNodeWithText("Day 3 of 7").assertIsDisplayed()
        compose.onNodeWithText("Pause").assertIsDisplayed()
        shot("2_timer_light")
        StudyStore.setTheme(ThemeMode.DARK)
        settle()
        shot("3_timer_dark")
    }

    @Test
    fun adjustAddsTime() {
        seedMidWeek(running = false)
        val before = StudyStore.state.value.studiedMs(System.currentTimeMillis())
        compose.onNodeWithContentDescription("Menu").performClick()
        settle()
        shot("4_menu")
        compose.onNodeWithText("Adjust time").performClick()
        settle()
        compose.onNodeWithText("Hours").performTextInput("1")
        compose.onNodeWithText("Minutes").performTextInput("30")
        settle()
        shot("5_adjust")
        compose.onNodeWithText("Apply").performClick()
        settle()
        val after = StudyStore.state.value.studiedMs(System.currentTimeMillis())
        assertEquals(90 * MINUTE_MS, after - before)
    }

    @Test
    fun undoLastSession() {
        seedMidWeek(running = false)
        val sessions = StudyStore.state.value.sessions.size
        compose.onNodeWithContentDescription("Menu").performClick()
        settle()
        compose.onNodeWithText("Undo last session").performClick()
        settle()
        compose.onNodeWithText("Remove").performClick()
        settle()
        assertEquals(sessions - 1, StudyStore.state.value.sessions.size)
    }

    @Test
    fun weekOverShowsResultThenNewWeek() {
        val now = System.currentTimeMillis()
        val ws = now - WEEK_MS - HOUR_MS
        StudyStore.update {
            StudyState(
                weekStart = ws,
                sessions = (0 until 7).map { d -> Session(ws + d * DAY_MS + 8 * HOUR_MS, ws + d * DAY_MS + (8 + 12 + d % 3) * HOUR_MS) },
                runningSince = now - 3 * HOUR_MS, // left running past the week end
            )
        }
        settle()
        compose.onNodeWithText("Start new week").assertIsDisplayed()
        assertFalse("stopped at week end", StudyStore.state.value.isRunning)
        assertEquals(ws + WEEK_MS, StudyStore.state.value.sessions.last().end)
        shot("6_result")
        compose.onNodeWithText("Start new week").performClick()
        settle()
        compose.onNodeWithText("Start week").assertIsDisplayed()
    }

    @Test
    fun darkTheme() {
        StudyStore.setTheme(ThemeMode.DARK)
        settle()
        compose.onNodeWithText("100 Hours").assertIsDisplayed()
        shot("1b_setup_dark")
        seedMidWeek(running = false)
        compose.onNodeWithContentDescription("Menu").performClick()
        settle()
        shot("4b_menu_dark")
    }

    /** Day 3 of the week, ~31h studied over the first days, timer optionally running. */
    private fun seedMidWeek(running: Boolean) {
        val now = System.currentTimeMillis()
        val ws = now - 2 * DAY_MS - 10 * HOUR_MS
        StudyStore.update {
            StudyState(
                weekStart = ws,
                sessions = listOf(
                    Session(ws + 1 * HOUR_MS, ws + 13 * HOUR_MS),
                    Session(ws + DAY_MS + 2 * HOUR_MS, ws + DAY_MS + 12 * HOUR_MS),
                    Session(ws + 2 * DAY_MS + 1 * HOUR_MS, ws + 2 * DAY_MS + 8 * HOUR_MS),
                ),
                runningSince = if (running) now - 47 * MINUTE_MS else null,
            )
        }
        settle()
    }
}
