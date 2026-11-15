package com.subnexa.app.ui.editor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.subnexa.app.ui.components.pressScale
import com.subnexa.app.ui.theme.Accent
import com.subnexa.app.ui.theme.Border
import com.subnexa.app.ui.theme.InputBorder
import com.subnexa.app.ui.theme.MonospaceTimestampStyle
import com.subnexa.app.ui.theme.Surface
import com.subnexa.app.ui.theme.SurfaceVariant
import com.subnexa.app.ui.theme.TextMuted
import com.subnexa.app.ui.theme.TextPrimary
import com.subnexa.app.ui.theme.TextSecondary

/** Inline find and replace bar shown above the subtitle list. */
@Composable
fun EditorSearchBar(
  state: SearchState,
  matchCount: Int,
  onQueryChange: (String) -> Unit,
  onReplacementChange: (String) -> Unit,
  onToggleMatchCase: () -> Unit,
  onToggleReplace: () -> Unit,
  onPrevious: () -> Unit,
  onNext: () -> Unit,
  onReplaceCurrent: () -> Unit,
  onReplaceAll: () -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  val hasQuery = state.query.isNotEmpty()
  val currentNumber =
    if (matchCount > 0) state.currentIndex.coerceIn(0, matchCount - 1) + 1 else 0
  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Accent,
    unfocusedBorderColor = InputBorder,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedPlaceholderColor = TextMuted,
    unfocusedPlaceholderColor = TextMuted,
    cursorColor = Accent
  )

  Column(
    modifier = modifier
      .fillMaxWidth()
      .background(Surface)
      .border(0.5.dp, Border)
      .padding(horizontal = 12.dp, vertical = 8.dp)
  ) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      OutlinedTextField(
        value = state.query,
        onValueChange = onQueryChange,
        placeholder = { Text("Find in subtitles") },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium,
        shape = RoundedCornerShape(8.dp),
        colors = fieldColors,
        modifier = Modifier
          .weight(1f)
          .focusRequester(focusRequester)
          .testTag("search_query_input")
      )

      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = when {
          !hasQuery -> ""
          matchCount == 0 -> "None"
          else -> "$currentNumber/$matchCount"
        },
        style = MonospaceTimestampStyle.copy(color = TextSecondary, fontSize = 12.sp)
      )

      IconButton(
        onClick = onPrevious,
        enabled = matchCount > 0,
        modifier = Modifier.size(36.dp).testTag("search_previous")
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowUp,
          contentDescription = "Previous match",
          tint = if (matchCount > 0) TextPrimary else TextMuted.copy(alpha = 0.5f)
        )
      }
      IconButton(
        onClick = onNext,
        enabled = matchCount > 0,
        modifier = Modifier.size(36.dp).testTag("search_next")
      ) {
        Icon(
          imageVector = Icons.Filled.KeyboardArrowDown,
          contentDescription = "Next match",
          tint = if (matchCount > 0) TextPrimary else TextMuted.copy(alpha = 0.5f)
        )
      }
      IconButton(
        onClick = onClose,
        modifier = Modifier.size(36.dp).testTag("search_close")
      ) {
        Icon(
          imageVector = Icons.Filled.Close,
          contentDescription = "Close search",
          tint = TextSecondary
        )
      }
    }

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SearchChip(
        label = "Match case",
        selected = state.matchCase,
        onClick = onToggleMatchCase,
        testTag = "search_match_case"
      )
      SearchChip(
        label = "Replace…",
        selected = state.showReplace,
        onClick = onToggleReplace,
        testTag = "search_toggle_replace"
      )
    }

    AnimatedVisibility(
      visible = state.showReplace,
      enter = expandVertically(tween(180)) + fadeIn(tween(150)),
      exit = shrinkVertically(tween(150)) + fadeOut(tween(100))
    ) {
      Column {
        Spacer(modifier = Modifier.height(8.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = state.replacement,
            onValueChange = onReplacementChange,
            placeholder = { Text("Replace with") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            shape = RoundedCornerShape(8.dp),
            colors = fieldColors,
            modifier = Modifier
              .weight(1f)
              .testTag("search_replacement_input")
          )
          SearchChip(
            label = "Replace",
            selected = false,
            enabled = matchCount > 0,
            onClick = onReplaceCurrent,
            testTag = "search_replace_one"
          )
          SearchChip(
            label = "All",
            selected = false,
            enabled = matchCount > 0,
            onClick = onReplaceAll,
            testTag = "search_replace_all"
          )
        }
      }
    }
  }
}

@Composable
private fun SearchChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  enabled: Boolean = true
) {
  val shape = RoundedCornerShape(16.dp)
  val interactionSource = remember { MutableInteractionSource() }
  val background by animateColorAsState(
    targetValue = if (selected) Accent.copy(alpha = 0.18f) else SurfaceVariant,
    animationSpec = tween(150),
    label = "search_chip_background"
  )
  Box(
    modifier = modifier
      .pressScale(interactionSource)
      .clip(shape)
      .background(background)
      .border(0.5.dp, if (selected) Accent else Border, shape)
      .clickable(
        enabled = enabled,
        interactionSource = interactionSource,
        indication = LocalIndication.current,
        onClick = onClick
      )
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium.copy(
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
      ),
      color = when {
        !enabled -> TextMuted.copy(alpha = 0.5f)
        selected -> Accent
        else -> TextPrimary
      }
    )
  }
}
