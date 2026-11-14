package com.subnexa.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.subnexa.app.ui.components.PrimaryAccentButton
import com.subnexa.app.ui.components.SecondarySubNexaButton
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay

private const val MAX_PROJECT_NAME_LENGTH = 120

@Composable
fun RenameProjectDialog(
  currentName: String,
  onConfirm: (String) -> Unit,
  onDismiss: () -> Unit
) {
  // Whole name is pre-selected so the user can type a replacement straight away.
  var field by remember(currentName) {
    mutableStateOf(TextFieldValue(currentName, TextRange(0, currentName.length)))
  }
  val focusRequester = remember { FocusRequester() }
  val canSave = field.text.isNotBlank()

  Dialog(onDismissRequest = onDismiss) {
    DialogCard {
      DialogTitle("Rename project")
      Spacer(modifier = Modifier.size(14.dp))
      OutlinedTextField(
        value = field,
        onValueChange = { if (it.text.length <= MAX_PROJECT_NAME_LENGTH) field = it },
        singleLine = true,
        modifier = Modifier
          .fillMaxWidth()
          .focusRequester(focusRequester)
          .testTag("rename_project_field"),
        colors = OutlinedTextFieldDefaults.colors(
          focusedTextColor = TextPrimary,
          unfocusedTextColor = TextPrimary,
          focusedBorderColor = Accent,
          unfocusedBorderColor = Border,
          cursorColor = Accent
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (canSave) onConfirm(field.text) })
      )
      Spacer(modifier = Modifier.size(18.dp))
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        SecondarySubNexaButton(
          text = "Cancel",
          onClick = onDismiss,
          modifier = Modifier.weight(1f)
        )
        PrimaryAccentButton(
          text = "Save",
          onClick = { onConfirm(field.text) },
          modifier = Modifier.weight(1f),
          enabled = canSave,
          testTag = "rename_project_save"
        )
      }
    }
    // Ask for keyboard focus once the dialog window is on screen. Guarded because the
    // field may not be attached yet on the very first frame.
    LaunchedEffect(Unit) {
      delay(150)
      try {
        focusRequester.requestFocus()
      } catch (_: IllegalStateException) {
      }
    }
  }
}
