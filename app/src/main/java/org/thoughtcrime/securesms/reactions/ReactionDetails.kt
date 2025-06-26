package org.thoughtcrime.securesms.reactions

import org.session.libsession.utilities.recipients.RecipientV2
import org.thoughtcrime.securesms.database.model.MessageId

/**
 * A UI model for a reaction in the [ReactionsDialogFragment]
 */
data class ReactionDetails(
  val sender: RecipientV2,
  val baseEmoji: String,
  val displayEmoji: String,
  val timestamp: Long,
  val serverId: String,
  val localId: MessageId,
  val count: Int
)
