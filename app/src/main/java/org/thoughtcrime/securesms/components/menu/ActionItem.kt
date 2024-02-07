package org.thoughtcrime.securesms.components.menu

import androidx.annotation.AttrRes

/**
 * Represents an action to be rendered
 */
data class ActionItem(
  @AttrRes val iconRes: Int,
  val title: Int,
  val action: Runnable,
  val contentDescription: Int? = null,
  val subtitle: (() -> CharSequence?)? = null
)
