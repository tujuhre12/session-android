package org.thoughtcrime.securesms.util

import org.session.libsession.messaging.sending_receiving.attachments.Attachment

private const val ZERO_SIZE = "0.00"
private const val KILO_SIZE = 1024

fun Attachment.displaySize(): String {

    val kbSize = size / KILO_SIZE
    val needsMb = kbSize > KILO_SIZE
    val sizeText = "%.2f".format(if (needsMb) kbSize / KILO_SIZE else kbSize)
    return when {
        sizeText == ZERO_SIZE -> "0.01"
        sizeText.endsWith(".00") -> sizeText.takeWhile { it != '.' }
        else -> sizeText
    }
}