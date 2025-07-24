package org.session.libsession.utilities

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Util
import java.util.LinkedList

@Parcelize
data class Address private constructor(val address: String) : Parcelable, Comparable<Address> {
    val isLegacyGroup: Boolean
        get() = GroupUtil.isLegacyClosedGroup(address)
    val isGroupV2: Boolean
        get() = address.startsWith(IdPrefix.GROUP.value)
    val isCommunity: Boolean
        get() = GroupUtil.isCommunity(address)

    /**
     * Whether this address is an encoded blinded address. The encoded blinded address contains
     * information of:
     * - The community URL it belongs to
     * - The blinded address itself
     *
     * This is used to uniquely identify a blinded address in a particular community. You may
     * encounter a blinded address that is passed directly into [Address] but that blinded address
     * technically is not unique across communities. This is because the blinded address derives
     * only from the public key of the user, and not the server's pub key. So if two communities
     * share the same public key, they will have the same blinded address for the same user.
     *
     * We encode this information here so that it's unique across the board.
     */
    val isCommunityInbox: Boolean
        get() = GroupUtil.isCommunityInbox(address)
    val isGroupOrCommunity: Boolean
        get() = isGroup || isCommunity
    val isGroup: Boolean
        get() = isLegacyGroup || isGroupV2
    val isContact: Boolean
        get() = !(isGroupOrCommunity || isCommunityInbox)

    val isBlinded: Boolean
        get() = IdPrefix.fromValue(address)?.isBlinded() == true

    /**
     * Extracts the blinded ID from the address if it is somehow encoded within this address.
     */
    fun toBlindedId(): AccountId? {
        return when {
            isCommunityInbox -> AccountId.fromStringOrNull(GroupUtil.getDecodedOpenGroupInboxAccountId(address))
            !isGroupOrCommunity -> AccountId.fromStringOrNull(address)?.takeIf { it.prefix?.isBlinded() == true }
            else -> null
        }
    }

    fun contactIdentifier(): String {
        if (!isContact && !isCommunity) {
            if (isGroupOrCommunity) throw AssertionError("Not e164, is group")
            throw AssertionError("Not e164, unknown")
        }
        return address
    }

    fun toGroupString(): String {
        if (!isGroupOrCommunity) throw AssertionError("Not group")
        return address
    }

    override fun toString(): String = address

    override fun compareTo(other: Address): Int = address.compareTo(other.address)

    val debugString: String
        get() = "Address(address=${address.substring(0, address.length.coerceAtMost(5))}...)"

    companion object {
        val UNKNOWN = Address("Unknown")

        @JvmStatic
        fun fromSerialized(serialized: String): Address = Address(serialized.lowercase())

        @JvmStatic
        fun fromSerializedList(serialized: String, delimiter: Char): List<Address> {
            val escapedAddresses = DelimiterUtil.split(serialized, delimiter)
            val set = escapedAddresses.toSet().sorted()
            val addresses: MutableList<Address> = LinkedList()
            for (escapedAddress in set) {
                addresses.add(fromSerialized(DelimiterUtil.unescape(escapedAddress, delimiter)))
            }
            return addresses
        }

        @JvmStatic
        fun toSerializedList(addresses: List<Address>, delimiter: Char): String {
            val set = addresses.toSet().sorted()
            val escapedAddresses: MutableList<String> = LinkedList()
            for (address in set) {
                escapedAddresses.add(DelimiterUtil.escape(address.toString(), delimiter))
            }
            return Util.join(escapedAddresses, delimiter.toString() + "")
        }

        fun AccountId.toAddress(): Address {
            return fromSerialized(hexString)
        }
    }

}