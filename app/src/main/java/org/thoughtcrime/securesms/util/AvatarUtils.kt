package org.thoughtcrime.securesms.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import network.loki.messenger.R
import org.session.libsession.avatars.ContactPhoto
import org.session.libsession.utilities.recipients.Recipient
import org.session.libsignal.utilities.IdPrefix
import java.math.BigInteger
import java.security.MessageDigest
import java.util.Locale
import androidx.core.graphics.toColorInt
import org.session.libsession.avatars.ProfileContactPhoto

object AvatarUtils {
    private const val EMPTY_LABEL = "0"

    //todo AVATAR should we have a way to get these from our theme?

    // Hardcoded possible bg colors for avatar backgrounds
    private val avatarBgColors = arrayOf(
        "#ff31F196".toColorInt(),
        "#ff57C9FA".toColorInt(),
        "#ffFAD657".toColorInt(),
        "#ffFF95EF".toColorInt(),
        "#ffC993FF".toColorInt(),
        "#ffFCB159".toColorInt(),
        "#ffFF9C8E".toColorInt()
    )

    fun getUIDataFromRecipient(recipient: Recipient): AvatarUIData {
        return getUIDataFromRecipient(recipient.name, recipient)
    }

    fun getUIDataFromRecipient(name: String, recipient: Recipient): AvatarUIData {
        return AvatarUIData(
            name = extractLabel(name.uppercase()),
            color = Color(getColorFromKey(recipient.address.toString())),
            contactPhoto = if(hasAvatar(recipient.contactPhoto)) recipient.contactPhoto else null
        )
    }

    private fun hasAvatar(contactPhoto: ContactPhoto?): Boolean {
        val avatar = (contactPhoto as? ProfileContactPhoto)?.avatarObject
        return contactPhoto != null && avatar != "0" && avatar != ""
    }

    @JvmStatic
    fun getColorFromKey(hashString: String): Int {
        val hash: Long
        if (hashString.length >= 12 && hashString.matches(Regex("^[0-9A-Fa-f]+\$"))) {
            hash = getSha512(hashString).substring(0 until 12).toLong(16)
        } else {
            hash = 0
        }

        return avatarBgColors[(hash % avatarBgColors.size).toInt()]
    }

    @JvmStatic
    fun generateTextBitmap(context: Context, pixelSize: Int, hashString: String, displayName: String?): BitmapDrawable {
        val colorPrimary = getColorFromKey(hashString)

        val labelText = when {
            !TextUtils.isEmpty(displayName) -> extractLabel(displayName!!.capitalize(Locale.ROOT))
            !TextUtils.isEmpty(hashString) -> extractLabel(hashString)
            else -> EMPTY_LABEL
        }

        val bitmap = Bitmap.createBitmap(pixelSize, pixelSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw background/frame
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = colorPrimary
        canvas.drawCircle(pixelSize.toFloat() / 2, pixelSize.toFloat() / 2, pixelSize.toFloat() / 2, paint)

        // Draw text
        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        textPaint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textPaint.textSize = pixelSize * 0.5f
        textPaint.color = android.graphics.Color.WHITE
        val areaRect = Rect(0, 0, pixelSize, pixelSize)
        val textBounds = RectF(areaRect)
        textBounds.right = textPaint.measureText(labelText)
        textBounds.bottom = textPaint.descent() - textPaint.ascent()
        textBounds.left += (areaRect.width() - textBounds.right) * 0.5f
        textBounds.top += (areaRect.height() - textBounds.bottom) * 0.5f
        canvas.drawText(labelText, textBounds.left, textBounds.top - textPaint.ascent(), textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun extractLabel(content: String): String {
        val trimmedContent = content.trim()
        if (trimmedContent.isEmpty()) return EMPTY_LABEL
        return if (trimmedContent.length > 2 && IdPrefix.fromValue(trimmedContent) != null) {
            trimmedContent[2].toString()
        } else {
            val splitWords = trimmedContent.split(Regex("\\W"))
            if (splitWords.size < 2) {
                trimmedContent.take(2)
            } else {
                splitWords.filter { word -> word.isNotEmpty() }.take(2).map { it.first() }.joinToString("")
            }
        }.uppercase()
    }

    private fun getSha512(input: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-512").digest(input.toByteArray())

        // Convert byte array into signum representation
        val no = BigInteger(1, messageDigest)

        // Convert message digest into hex value
        var hashText: String = no.toString(16)

        // Add preceding 0s to make it 32 bytes
        if (hashText.length < 128) {
            val sb = StringBuilder()
            for (i in 0 until 128 - hashText.length) {
                sb.append('0')
            }
            hashText = sb.append(hashText).toString()
        }

        return hashText
    }
}

data class AvatarUIData(
    val name: String? = null,
    val color: Color? = null,
    val contactPhoto: ContactPhoto? = null,
)

sealed class AvatarBadge(@DrawableRes val icon: Int){
    data object None: AvatarBadge(0)
    data object Admin: AvatarBadge(R.drawable.ic_crown_custom)
    data class Custom(@DrawableRes val iconRes: Int): AvatarBadge(iconRes)
}