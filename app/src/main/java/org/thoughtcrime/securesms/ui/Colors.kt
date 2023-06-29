package org.thoughtcrime.securesms.ui

import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val colorDestructive = Color(0xffFF453A)

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = colorDestructive)