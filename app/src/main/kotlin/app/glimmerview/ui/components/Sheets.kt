package app.glimmerview.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.glimmerview.ui.theme.GlimmerColors
import app.glimmerview.ui.theme.GlimmerType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Longueur maximale d'un titre d'œuvre. */
const val MAX_TITLE_LENGTH = 60

/**
 * Panneau qui monte du bas de l'écran, sur un voile d'encre.
 * Le voile, le geste retour et [content] via `close` le referment avec la même animation.
 */
@Composable
fun GlimmerSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.(close: () -> Unit) -> Unit) {
    var visible by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val close: () -> Unit = {
        if (visible) {
            visible = false
            scope.launch {
                delay(SHEET_EXIT_MILLIS.toLong())
                onDismiss()
            }
        }
    }
    LaunchedEffect(Unit) { visible = true }
    BackHandler(onBack = close)

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(SHEET_EXIT_MILLIS))) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(GlimmerColors.Ink.copy(alpha = 0.32f))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = close),
            )
        }
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(tween(280)) { it } + fadeIn(tween(160)),
            exit = slideOutVertically(tween(SHEET_EXIT_MILLIS)) { it } + fadeOut(tween(160)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(28.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp), ambientColor = GlimmerColors.Ink, spotColor = GlimmerColors.Ink)
                    .background(GlimmerColors.Plaster, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                    // Un toucher sur le panneau ne doit pas atteindre le voile (qui le fermerait).
                    .pointerInput(Unit) { detectTapGestures { } }
                    .navigationBarsPadding()
                    .imePadding()
                    .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(40.dp, 4.dp)
                        .background(GlimmerColors.Stone, CircleShape),
                )
                content(close)
            }
        }
    }
}

/** Champ de titre au trait : Spectral italique sur un filet d'encre, sans cadre ni fond. */
@Composable
fun TitleField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onDone: () -> Unit = {},
) {
    val style = GlimmerType.italic.copy(fontSize = 22.sp, lineHeight = 28.sp, color = GlimmerColors.Ink)
    BasicTextField(
        value = value,
        onValueChange = { next ->
            if (next.text.length <= MAX_TITLE_LENGTH) onValueChange(next.copy(text = next.text.replace('\n', ' ')))
        },
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(GlimmerColors.Prussian),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier),
        decorationBox = { inner ->
            Column {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    if (value.text.isEmpty()) Text(placeholder, style = style, color = GlimmerColors.Taupe, maxLines = 1)
                    inner()
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(GlimmerColors.Ink))
            }
        },
    )
}

/** Panneau « Renommer » : le titre actuel est présélectionné, il suffit de taper le nouveau. */
@Composable
fun RenameSheet(
    kicker: String,
    currentTitle: String,
    cancelLabel: String,
    saveLabel: String,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    GlimmerSheet(onDismiss) { close ->
        var value by remember { mutableStateOf(TextFieldValue(currentTitle, selection = TextRange(0, currentTitle.length))) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            delay(SHEET_EXIT_MILLIS.toLong())
            focus.requestFocus()
        }
        val save = {
            onRename(value.text)
            close()
        }
        Cartel(kicker)
        TitleField(value = value, onValueChange = { value = it }, placeholder = currentTitle, focusRequester = focus, onDone = save)
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InlineLink(cancelLabel, onClick = close, lineColor = GlimmerColors.Stone)
            InkPill(saveLabel, onClick = save, enabled = value.text.isNotBlank())
        }
    }
}

private const val SHEET_EXIT_MILLIS = 220
