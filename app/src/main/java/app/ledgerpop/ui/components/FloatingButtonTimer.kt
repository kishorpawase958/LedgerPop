package app.ledgerpop.ui.components

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Stable
class FloatingButtonTimerState(
    var timerSeconds: Float,
    private val scope: CoroutineScope,
    isMenuOpen: Boolean = false,
    var onHide: (() -> Unit)? = null
) {
    var isVisible by mutableStateOf(true)
        private set

    var isMenuOpen by mutableStateOf(isMenuOpen)

    private var timerJob: Job? = null

    fun resetTimer() {
        isVisible = true
        timerJob?.cancel()
        if (!isMenuOpen) {
            timerJob = scope.launch {
                delay((timerSeconds * 1000f).toLong())
                isVisible = false
                onHide?.invoke()
            }
        }
    }

    fun cancelTimer() {
        isVisible = true
        timerJob?.cancel()
    }
}

@Composable
fun rememberFloatingButtonTimerState(
    timerSeconds: Float? = null,
    isMenuOpen: Boolean = false,
    onHide: (() -> Unit)? = null
): FloatingButtonTimerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnHide by rememberUpdatedState(onHide)

    val sharedPrefs = remember(context) {
        context.getSharedPreferences("ledgerpop_prefs", Context.MODE_PRIVATE)
    }

    var prefTimerSeconds by remember {
        mutableFloatStateOf(sharedPrefs.getFloat("floating_button_timer", 3.0f))
    }

    DisposableEffect(sharedPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "floating_button_timer") {
                prefTimerSeconds = prefs.getFloat("floating_button_timer", 3.0f)
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val effectiveTimer = timerSeconds ?: prefTimerSeconds

    val timerState = remember(scope) {
        FloatingButtonTimerState(
            timerSeconds = effectiveTimer,
            scope = scope,
            isMenuOpen = isMenuOpen,
            onHide = { currentOnHide?.invoke() }
        )
    }

    LaunchedEffect(currentOnHide) {
        timerState.onHide = { currentOnHide?.invoke() }
    }

    LaunchedEffect(effectiveTimer) {
        timerState.timerSeconds = effectiveTimer
        timerState.resetTimer()
    }

    LaunchedEffect(isMenuOpen) {
        timerState.isMenuOpen = isMenuOpen
        if (isMenuOpen) {
            timerState.cancelTimer()
        } else {
            timerState.resetTimer()
        }
    }

    return timerState
}

fun Modifier.trackInteractionForFloatingButton(timerState: FloatingButtonTimerState): Modifier =
    this.pointerInput(timerState) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (event.changes.any { it.pressed }) {
                    timerState.resetTimer()
                }
            }
        }
    }

