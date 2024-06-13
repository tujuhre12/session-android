package org.thoughtcrime.securesms.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Colors
import androidx.compose.material.ContentAlpha
import androidx.compose.material.LocalContentColor
import androidx.compose.material.MaterialTheme
import androidx.compose.material.RadioButtonDefaults
import androidx.compose.material.TabRowDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.primarySurface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import org.session.libsession.utilities.TextSecurePreferences

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

val oceanDark0 = Color(0xff000000)
val oceanDark1 = Color(0xff1A1C28)
val oceanDark2 = Color(0xff252735)
val oceanDark3 = Color(0xff2B2D40)
val oceanDark4 = Color(0xff3D4A5D)
val oceanDark5 = Color(0xffA6A9CE)
val oceanDark6 = Color(0xff5CAACC)
val oceanDark7 = Color(0xffFFFFFF)

val oceanLight0 = Color(0xff000000)
val oceanLight1 = Color(0xff19345D)
val oceanLight2 = Color(0xff6A6E90)
val oceanLight3 = Color(0xff5CAACC)
val oceanLight4 = Color(0xffB3EDF2)
val oceanLight5 = Color(0xffE7F3F4)
val oceanLight6 = Color(0xffECFAFB)
val oceanLight7 = Color(0xffFCFFFF)

val Colors.disabled @Composable get() = onSurface.copy(alpha = ContentAlpha.disabled)

val blackAlpha40 = Color.Black.copy(alpha = 0.4f)

val primaryGreen = Color(0xFF31F196)
val primaryBlue = Color(0xFF57C9FA)
val primaryPurple = Color(0xFFC993FF)
val primaryPink = Color(0xFFFF95EF)
val primaryRed = Color(0xFFFF9C8E)
val primaryOrange = Color(0xFFFCB159)
val primaryYellow = Color(0xFFFAD657)

val dangerDark = Color(0xFFFF3A3A)
val dangerLight = Color(0xFFE12D19)
val disabledDark = Color(0xFFA1A2A1)
val disabledLight = Color(0xFF6D6D6D)


@Composable
fun transparentButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent)

@Composable
fun destructiveButtonColors() = ButtonDefaults.buttonColors(backgroundColor = Color.Transparent, contentColor = LocalColors.current.danger)

@Composable
fun outlinedTextFieldColors(
    isError: Boolean
) = TextFieldDefaults.outlinedTextFieldColors(
    textColor = if (isError) LocalColors.current.danger else LocalContentColor.current,
    cursorColor = if (isError) LocalColors.current.danger else LocalContentColor.current,
    focusedBorderColor = LocalColors.current.borders,
    unfocusedBorderColor = LocalColors.current.borders,
    placeholderColor = if (isError) LocalColors.current.danger else LocalColors.current.textSecondary
)
