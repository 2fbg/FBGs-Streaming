package com.example.ui

import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Substituição segura para o `AndroidTextToolbar` padrão do Compose.
 *
 * No Compose 1.7.x, ao clicar em Colar/Copiar/Recortar na barra flutuante de texto,
 * `TextActionModeCallback.onActionItemClicked` executa o callback (que chama `hide()` e
 * `actionMode.finish()`) e logo em seguida chama `mode.finish()` uma segunda vez. Como
 * `FloatingActionMode.finish()` no AOSP não possui guarda contra chamadas repetidas, o
 * `DecorView` registra:
 * `E/DecorView[]: Destroying unexpected ActionMode instance of TYPE_FLOATING; ... Expected null`.
 *
 * Esta implementação garante que cada instância de `ActionMode` seja finalizada no máximo uma vez
 * e nunca após o desacoplamento da janela (`onViewDetachedFromWindow`).
 */
class SafeAndroidTextToolbar(
    private val view: View
) : TextToolbar {

    private companion object {
        const val MENU_ID_COPY = 0
        const val MENU_ID_PASTE = 1
        const val MENU_ID_CUT = 2
        const val MENU_ID_SELECT_ALL = 3
    }

    private var actionMode: ActionMode? = null
    private var contentRect: Rect = Rect.Zero
    private var onCopyRequested: (() -> Unit)? = null
    private var onPasteRequested: (() -> Unit)? = null
    private var onCutRequested: (() -> Unit)? = null
    private var onSelectAllRequested: (() -> Unit)? = null

    override var status: TextToolbarStatus = TextToolbarStatus.Hidden
        private set

    private val actionModeCallback = object : ActionMode.Callback2() {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (mode == null || menu == null) return false
            syncMenuItems(menu)
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            if (mode == null || menu == null) return false
            syncMenuItems(menu)
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            val action = when (item?.itemId) {
                MENU_ID_COPY -> onCopyRequested
                MENU_ID_PASTE -> onPasteRequested
                MENU_ID_CUT -> onCutRequested
                MENU_ID_SELECT_ALL -> onSelectAllRequested
                else -> return false
            }
            action?.invoke()
            // Só finaliza se o próprio callback ainda não tiver chamado hide() / encerrado este mode.
            finishActionModeSafely(mode)
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            if (actionMode === mode) {
                actionMode = null
            }
            status = TextToolbarStatus.Hidden
        }

        override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: android.graphics.Rect?) {
            outRect?.set(
                contentRect.left.toInt(),
                contentRect.top.toInt(),
                contentRect.right.toInt(),
                contentRect.bottom.toInt()
            )
        }
    }

    private fun syncMenuItems(menu: Menu) {
        updateMenuItem(menu, MENU_ID_COPY, 0, android.R.string.copy, onCopyRequested)
        updateMenuItem(menu, MENU_ID_PASTE, 1, android.R.string.paste, onPasteRequested)
        updateMenuItem(menu, MENU_ID_CUT, 2, android.R.string.cut, onCutRequested)
        updateMenuItem(menu, MENU_ID_SELECT_ALL, 3, android.R.string.selectAll, onSelectAllRequested)
    }

    private fun updateMenuItem(
        menu: Menu,
        itemId: Int,
        order: Int,
        titleRes: Int,
        callback: (() -> Unit)?
    ) {
        val existing = menu.findItem(itemId)
        if (callback != null && existing == null) {
            menu.add(Menu.NONE, itemId, order, titleRes)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        } else if (callback == null && existing != null) {
            menu.removeItem(itemId)
        }
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?
    ) {
        this.contentRect = rect
        this.onCopyRequested = onCopyRequested
        this.onPasteRequested = onPasteRequested
        this.onCutRequested = onCutRequested
        this.onSelectAllRequested = onSelectAllRequested

        if (!view.isAttachedToWindow) {
            onWindowDetached()
            return
        }

        val current = actionMode
        if (current == null) {
            status = TextToolbarStatus.Shown
            val started = try {
                view.startActionMode(actionModeCallback, ActionMode.TYPE_FLOATING)
            } catch (_: Throwable) {
                null
            }
            actionMode = started
            if (started == null) {
                status = TextToolbarStatus.Hidden
            }
        } else {
            try {
                current.invalidate()
                current.invalidateContentRect()
            } catch (_: Throwable) {
                actionMode = null
                status = TextToolbarStatus.Hidden
            }
        }
    }

    override fun hide() {
        finishActionModeSafely(actionMode)
    }

    internal fun onWindowDetached() {
        actionMode = null
        status = TextToolbarStatus.Hidden
    }

    private fun finishActionModeSafely(target: ActionMode?) {
        val current = actionMode
        if (target == null || current !== target) return
        // Zera a referência ANTES de chamar finish() para impedir reentrância ou chamada dupla.
        actionMode = null
        status = TextToolbarStatus.Hidden
        if (!view.isAttachedToWindow) return
        try {
            current.finish()
        } catch (_: Throwable) {
            // Ignora corrida de desmontagem da janela/DecorView
        }
    }
}

@Composable
fun ProvideSafeTextToolbar(content: @Composable () -> Unit) {
    val view = LocalView.current
    val safeToolbar = remember(view) { SafeAndroidTextToolbar(view) }

    DisposableEffect(view, safeToolbar) {
        val attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = Unit
            override fun onViewDetachedFromWindow(v: View) {
                safeToolbar.onWindowDetached()
            }
        }
        view.addOnAttachStateChangeListener(attachListener)
        onDispose {
            view.removeOnAttachStateChangeListener(attachListener)
            safeToolbar.hide()
        }
    }

    CompositionLocalProvider(LocalTextToolbar provides safeToolbar) {
        content()
    }
}

@Composable
fun SafeDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        ProvideSafeTextToolbar(content)
    }
}
