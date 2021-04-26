package com.github.ashutoshgngwr.noice.fragment

import android.widget.Button
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.ashutoshgngwr.noice.EspressoX
import com.github.ashutoshgngwr.noice.InAppReviewFlowManager
import com.github.ashutoshgngwr.noice.MediaPlayerService
import com.github.ashutoshgngwr.noice.R
import com.github.ashutoshgngwr.noice.RetryTestRule
import com.github.ashutoshgngwr.noice.ShortcutHandlerActivity
import com.github.ashutoshgngwr.noice.repository.PresetRepository
import com.github.ashutoshgngwr.noice.sound.Preset
import com.github.ashutoshgngwr.noice.sound.player.Player
import com.github.ashutoshgngwr.noice.sound.player.PlayerManager
import io.mockk.clearStaticMockk
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetFragmentTest {

  @Rule
  @JvmField
  val retryTestRule = RetryTestRule(5)

  private lateinit var fragmentScenario: FragmentScenario<PresetFragment>
  private lateinit var mockPreset: Preset
  private lateinit var mockPresetRepository: PresetRepository

  @Before
  fun setup() {
    mockkStatic(ShortcutManagerCompat::class)
    mockkObject(
      InAppReviewFlowManager,
      Preset.Companion,
      MediaPlayerService.Companion,
      PresetRepository.Companion
    )

    mockPreset = mockk(relaxed = true) {
      every { id } returns "test-id"
      every { name } returns "test"
      every { playerStates } returns arrayOf(
        Preset.PlayerState("test-1", Player.DEFAULT_VOLUME, Player.DEFAULT_TIME_PERIOD),
        Preset.PlayerState("test-2", Player.MAX_VOLUME, Player.MAX_TIME_PERIOD)
      )
    }

    mockPresetRepository = mockk {
      every { list() } returns arrayOf(mockPreset, mockk(relaxed = true) {
        every { name } returns "test-exists"
      })
    }

    every { PresetRepository.newInstance(any()) } returns mockPresetRepository
    fragmentScenario = launchFragmentInContainer(null, R.style.Theme_App)
  }

  @After
  fun teardown() {
    unmockkAll()
  }

  @Test
  fun testInitialLayout() {
    onView(withId(R.id.empty_list_hint))
      .check(matches(withEffectiveVisibility(Visibility.GONE)))

    every { mockPresetRepository.list() } returns arrayOf()
    fragmentScenario.recreate()
    onView(withId(R.id.empty_list_hint))
      .check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
  }

  @Test
  fun testRecyclerViewItem_playButton() {
    // stub the original method. Without stubbing, mockk will also run the real implementation.
    every { MediaPlayerService.playPreset(any(), any()) } returns Unit

    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { MediaPlayerService.playPreset(any(), "test-id") }
  }

  @Test
  fun testRecyclerViewItem_stopButton() {
    // ensure that PresetFragment assumes it is playing a preset
    every { Preset.from(any(), any()) } returns mockPreset
    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
      })
    }

    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.play_button)
      )
    )

    verify(exactly = 1) { MediaPlayerService.stopPlayback(any()) }
  }

  @Test
  fun testRecyclerViewItem_deleteOption() {
    every { mockPresetRepository.delete(any()) } returns true

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    onView(withText("test")).check(doesNotExist())
    verify(exactly = 1) {
      mockPresetRepository.delete("test-id")
      ShortcutManagerCompat.removeDynamicShortcuts(any(), listOf("test-id"))
      InAppReviewFlowManager.maybeAskForReview(any())
    }
  }

  @Test
  fun testRecyclerViewItem_deleteOption_onPresetPlaying() {
    // ensure that PresetFragment assumes it is playing a preset
    every { Preset.from(any(), any()) } returns mockPreset
    every { mockPresetRepository.delete(any()) } returns true

    fragmentScenario.onFragment {
      it.onPlayerManagerUpdate(mockk(relaxed = true) {
        every { state } returns PlayerManager.State.PLAYING
      })
    }

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.delete)).perform(click()) // select delete option
    onView(allOf(instanceOf(Button::class.java), withText(R.string.delete)))
      .perform(click()) // click delete button in confirmation dialog

    // should publish a stop playback event if preset was playing
    verify(exactly = 1) { MediaPlayerService.stopPlayback(any()) }
  }

  @Test
  fun testRecyclerViewItem_renameOption() {
    every { mockPresetRepository.update(any()) } returns Unit

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.rename)).perform(click()) // select rename option
    onView(withId(R.id.editText))
      .check(matches(isDisplayed())) // check if the rename dialog was displayed
      .perform(replaceText("test-exists")) // replace text in input field

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .check(matches(not(isEnabled())))

    onView(withId(R.id.editText))
      .check(matches(isDisplayed()))
      .perform(replaceText("test-does-not-exists"))

    onView(allOf(instanceOf(Button::class.java), withText(R.string.save)))
      .check(matches(isEnabled()))
      .perform(click()) // click on positive button

    val presetSlot = slot<Preset>()
    verify(exactly = 1) {
      mockPreset.name = "test-does-not-exists"
      mockPresetRepository.update(capture(presetSlot))
      InAppReviewFlowManager.maybeAskForReview(any())
    }

    assertEquals(mockPreset, presetSlot.captured)
  }

  @Test
  fun testRecyclerViewItem_AddToHomeScreenOption() {
    val pinShortcutSupportedExpectations = arrayOf(false, true)
    val expectedRequestPinShortcutCalls = arrayOf(0, 1)

    for (i in pinShortcutSupportedExpectations.indices) {
      // prevent system dialog from showing up.
      every { ShortcutManagerCompat.requestPinShortcut(any(), any(), any()) } returns true
      every {
        ShortcutManagerCompat.isRequestPinShortcutSupported(any())
      } returns pinShortcutSupportedExpectations[i]

      // open context menu
      onView(withId(R.id.preset_list)).perform(
        RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
          hasDescendant(allOf(withId(R.id.title), withText("test"))),
          EspressoX.clickInItem(R.id.menu_button)
        )
      )

      onView(withText(R.string.add_to_home_screen)).perform(click()) // select add to home screen option

      val shortcutInfoSlot = slot<ShortcutInfoCompat>()
      verify(exactly = expectedRequestPinShortcutCalls[i]) {
        ShortcutManagerCompat.requestPinShortcut(any(), capture(shortcutInfoSlot), any())
      }

      if (pinShortcutSupportedExpectations[i]) {
        assertTrue(
          "should capture a ShortcutInfo from requestPinShortcut() call",
          shortcutInfoSlot.isCaptured
        )

        assertNotNull(shortcutInfoSlot.captured.id)
        assertEquals("test", shortcutInfoSlot.captured.shortLabel)

        val intent = shortcutInfoSlot.captured.intent
        assertEquals(ShortcutHandlerActivity::class.qualifiedName, intent.component?.className)
        assertNotNull(intent.getStringExtra(ShortcutHandlerActivity.EXTRA_SHORTCUT_ID))
        assertEquals("test-id", intent.getStringExtra(ShortcutHandlerActivity.EXTRA_PRESET_ID))
      } else {
        assertFalse(
          "should not capture a ShortcutInfo from requestPinShortcut() call",
          shortcutInfoSlot.isCaptured
        )

        onView(withId(com.google.android.material.R.id.snackbar_text))
          .check(matches(withText(R.string.pinned_shortcuts_not_supported)))
      }

      clearStaticMockk(ShortcutManagerCompat::class)
    }
  }

  @Test
  fun testRecyclerViewItem_addToAppShortcuts() {
    every { ShortcutManagerCompat.addDynamicShortcuts(any(), any()) } returns true
    every { ShortcutManagerCompat.getDynamicShortcuts(any()) } returns mutableListOf()

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.add_to_app_shortcuts)).perform(click())

    val shortcutInfoSlot = slot<List<ShortcutInfoCompat>>()
    verify(exactly = 1) {
      ShortcutManagerCompat.addDynamicShortcuts(any(), capture(shortcutInfoSlot))
    }

    assertEquals(1, shortcutInfoSlot.captured.size)
    assertEquals("test-id", shortcutInfoSlot.captured[0].id)
  }

  @Test
  fun testRecyclerViewItem_removeFromAppShortcuts() {
    every { ShortcutManagerCompat.getDynamicShortcuts(any()) } returns listOf(
      mockk(relaxed = true) {
        every { id } returns mockPreset.id
      }
    )

    // open context menu
    onView(withId(R.id.preset_list)).perform(
      RecyclerViewActions.actionOnItem<PresetFragment.ViewHolder>(
        hasDescendant(allOf(withId(R.id.title), withText("test"))),
        EspressoX.clickInItem(R.id.menu_button)
      )
    )

    onView(withText(R.string.remove_from_app_shortcuts)).perform(click())

    verify(exactly = 1) {
      ShortcutManagerCompat.removeDynamicShortcuts(any(), listOf("test-id"))
    }
  }
}
