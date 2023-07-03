package org.thoughtcrime.securesms.ui

import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val colorDestructive = Color(0xffFF453A)

val classicDark0 = Color(0xff111111)

val classicDark1 = Color(0xff1B1B1B)
val classicDark2 = Color(0xff2D2D2D)
val classicDark3 = Color(0xff414141)
val classicDark4 = Color(0xff767676)
val classicDark5 = Color(0xffA1A2A1)
val classicDark6 = Color(0xffFFFFFF)

val classicLight0 = Color(0xff000000)
val classicLight1 = Color(0xff6D6D6D)
val classicLight2 = Color(0xffA1A2A1)
val classicLight3 = Color(0xffDFDFDF)
val classicLight4 = Color(0xffF0F0F0)
val classicLight5 = Color(0xffF9F9F9)
val classicLight6 = Color(0xffFFFFFF)

@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = colorDestructive)
