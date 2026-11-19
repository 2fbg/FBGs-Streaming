package com.example

import android.content.Context
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.test.core.app.ApplicationProvider
import com.example.ui.SafeAndroidTextToolbar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SafeAndroidTextToolbarTest {

    private class FakeActionMode(
        private val callback: ActionMode.Callback
    ) : ActionMode() {
        var finishCount: Int = 0
        var invalidateCount: Int = 0

        override fun setTitle(title: CharSequence?) = Unit
        override fun setTitle(resId: Int) = Unit
        override fun setSubtitle(subtitle: CharSequence?) = Unit
        override fun setSubtitle(resId: Int) = Unit
        override fun setCustomView(view: View?) = Unit
        override fun invalidate() {
            invalidateCount++
        }

        override fun finish() {
            finishCount++
            callback.onDestroyActionMode(this)
        }

        override fun getMenu(): Menu? = null
        override fun getTitle(): CharSequence? = null
        override fun getSubtitle(): CharSequence? = null
        override fun getCustomView(): View? = null
        override fun getMenuInflater(): MenuInflater? = null
    }

    private class TestToolbarView(context: Context) : View(context) {
        var attached: Boolean = true
        var lastCallback: ActionMode.Callback? = null
        var lastActionMode: FakeActionMode? = null

        override fun isAttachedToWindow(): Boolean = attached

        override fun startActionMode(callback: ActionMode.Callback?, type: Int): ActionMode? {
            if (callback == null) return null
            lastCallback = callback
            val mode = FakeActionMode(callback)
            lastActionMode = mode
            return mode
        }
    }

    @Test
    fun `clicking paste when callback calls hide finishes floating ActionMode only once`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TestToolbarView(context)
        val toolbar = SafeAndroidTextToolbar(view)

        var pasteInvoked = false
        toolbar.showMenu(
            rect = Rect(0f, 0f, 100f, 40f),
            onPasteRequested = {
                pasteInvoked = true
                // Simulates Compose TextFieldSelectionManager.paste() -> hideSelectionToolbar()
                toolbar.hide()
            }
        )

        val callback = view.lastCallback
        val mode = view.lastActionMode
        assertNotNull(callback)
        assertNotNull(mode)
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        val pasteMenuItem = org.robolectric.fakes.RoboMenuItem(1)
        callback!!.onActionItemClicked(mode, pasteMenuItem)

        assertEquals(true, pasteInvoked)
        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertEquals(
            "FloatingActionMode.finish() must be called exactly once to prevent DecorView error",
            1,
            mode!!.finishCount
        )

        // Subsequent hide() calls must remain no-ops
        toolbar.hide()
        assertEquals(1, mode.finishCount)
    }

    @Test
    fun `detaching window clears ActionMode without calling finish on detached DecorView`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val view = TestToolbarView(context)
        val toolbar = SafeAndroidTextToolbar(view)

        toolbar.showMenu(
            rect = Rect(0f, 0f, 100f, 40f),
            onCopyRequested = {}
        )

        val mode = view.lastActionMode!!
        assertEquals(TextToolbarStatus.Shown, toolbar.status)

        view.attached = false
        toolbar.hide()

        assertEquals(TextToolbarStatus.Hidden, toolbar.status)
        assertEquals(0, mode.finishCount)
    }
}
