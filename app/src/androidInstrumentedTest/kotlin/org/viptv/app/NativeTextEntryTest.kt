package org.viptv.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the dialog's native editable field without an account or real PIN. */
@RunWith(AndroidJUnit4::class)
class NativeTextEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nativeDoneSubmitsOneMaskedEightDigitPin() {
        val visible = mutableStateOf(true)
        val submitted = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true) {
                ViptvTheme(false, Color.White) {
                    if (visible.value) TextEntry("Enter parent PIN", "Enter a 4–8 digit parent PIN", secret = true,
                        onDone = { submitted += it; visible.value = false }, onCancel = { visible.value = false })
                }
            }
        }

        val field = compose.onNode(hasSetTextAction())
        field.assert(SemanticsMatcher("masked password field") { it.config.contains(SemanticsProperties.Password) })
        field.performTextInput("1x2x3x4x5x6x7x8x9")
        field.performImeAction()

        compose.runOnIdle { assertEquals(listOf("12345678"), submitted) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    @Test fun cancelDiscardsDraftWithoutSubmitting() {
        val visible = mutableStateOf(true)
        val submitted = mutableListOf<String>()
        var cancelled = 0
        compose.setContent {
            CompositionLocalProvider(LocalTv provides true) {
                ViptvTheme(false, Color.White) {
                    if (visible.value) TextEntry("Enter parent PIN", "Enter a 4–8 digit parent PIN", secret = true,
                        onDone = { submitted += it; visible.value = false }, onCancel = { cancelled++; visible.value = false })
                }
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("1234")
        compose.onNodeWithText("Cancel").performClick()

        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(emptyList<String>(), submitted) }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }
}
