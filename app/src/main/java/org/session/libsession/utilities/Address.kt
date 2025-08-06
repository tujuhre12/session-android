package org.session.libsession.utilities

import android.os.Parcel
import android.os.Parcelable
import org.session.libsession.messaging.open_groups.OpenGroup
import org.session.libsignal.utilities.AccountId
import org.session.libsignal.utilities.IdPrefix
import org.session.libsignal.utilities.Util
import java.util.LinkedList

sealed class Address : Parcelable, Comparable<Address> {
    /**
     * The serialized form of the address.
     */
    abstract val address: String

    /**
     * A debug string that is safe to log.
     */
    abstract val debugString: String

    override fun compareTo(other: Address) = address.compareTo(other.address)

    data class Group(override val accountId: AccountId) : Conversable(), WithAccountId {
        override val address: String
            get() = accountId.hexString

        override val debugString: String
            get() = accountId.toString()

        init {
            check(accountId.prefix == IdPrefix.GROUP) {
                "AccountId must have a GROUP prefix, but was: ${accountId.prefix}"
            }
        }

        override fun toString(): String = address
    }

    data class Standard(override val accountId: AccountId) : Conversable(), WithAccountId {
        override val address: String
            get() = accountId.hexString

        override val debugString: String
            get() = accountId.toString()

        init {
            check(accountId.prefix == IdPrefix.STANDARD) {
                "AccountId must have a STANDARD prefix, but was: ${accountId.prefix}"
            }
        }

        override fun toString(): String = address
    }

    data class Blinded(val blindedId: AccountId) : Address(), WithAccountId {
        override val accountId: AccountId
            get() = blindedId

        override val address: String
            get() = blindedId.hexString

        override val debugString: String
            get() = blindedId.toString()

        init {
            check(blindedId.prefix?.isBlinded() == true) {
                "AccountId must have a BLINDED prefix, but was: ${blindedId.prefix}"
            }
        }

        override fun toString(): String = address
    }

    data class LegacyGroup(val groupPublicKeyHex: String) : Conversable() {
        override val address: String by lazy(LazyThreadSafetyMode.NONE) {
            GroupUtil.doubleEncodeGroupID(groupPublicKeyHex)
        }

        override val debugString: String
            get() = "LegacyGroup(groupPublicKeyHex=${groupPublicKeyHex.substring(0, 8)}...)"

        override fun toString(): String = address
    }

    data class CommunityBlindedId(val serverUrl: String, val serverPubKey: String, val blindedId: Blinded) : Conversable(), WithAccountId {
        override val accountId: AccountId
            get() = blindedId.blindedId

        override val address: String by lazy(LazyThreadSafetyMode.NONE) {
            GroupUtil.getEncodedOpenGroupInboxAddress(
                server = serverUrl,
                pubKey = serverPubKey,
                blindedAccountId = blindedId.blindedId
            )
        }

        override val debugString: String
            get() = "CommunityBlindedId(serverUrl=$serverUrl, blindedId=$blindedId)"

        override fun toString(): String = address
    }

    data class Community(val serverUrl: String, val room: String) : Conversable() {
        constructor(openGroup: OpenGroup): this(
            serverUrl = openGroup.server,
            room = openGroup.room
        )

        override val address: String by lazy(LazyThreadSafetyMode.NONE) {
            GroupUtil.getEncodedOpenGroupID("$serverUrl.$room".toByteArray())
        }

        override val debugString: String
            get() = "Community(serverUrl=${serverUrl.substring(10)}, room=xxxx)"

        override fun toString(): String = address
    }

    data class Unknown(val serialized: String) : Address() {
        override val address: String
            get() = serialized

        override val debugString: String
            get() = "Unknown(serialized=$serialized)"

        override fun toString(): String = address
    }

    /**
     * A marker interface for addresses that can be used to start a conversation
     */
    sealed class Conversable : Address()
    sealed interface WithAccountId {
        val accountId: AccountId
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(address)
    }

    companion object {
        @JvmStatic
        fun fromSerialized(serialized: String): Address {
            if (serialized.startsWith(GroupUtil.COMMUNITY_INBOX_PREFIX)) {
                val (url, key, id) = requireNotNull(GroupUtil.getDecodedOpenGroupInboxID(serialized)) {
                    "Invalid serialized community inbox address: $serialized"
                }

                return CommunityBlindedId(url, key, Blinded(id))
            }

            if (serialized.startsWith(GroupUtil.COMMUNITY_PREFIX)) {
                val groupId = GroupUtil.getDecodedGroupID(serialized)
                val lastDot = groupId.lastIndexOf('.')
                require(lastDot >= 0) {
                    "Invalid community address: $serialized"
                }

                val serverUrl = groupId.substring(0, lastDot)
                val room = groupId.substring(lastDot + 1)
                return Community(serverUrl, room)
            }

            if (serialized.startsWith(GroupUtil.LEGACY_CLOSED_GROUP_PREFIX)) {
                val groupId = GroupUtil.doubleDecodeGroupId(serialized)
                return LegacyGroup(groupId)
            }

            AccountId.fromStringOrNull(serialized)?.let {
                return it.toAddress()
            }

            return Unknown(serialized)
        }

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

        fun String.toAddress(): Address {
            return fromSerialized(this)
        }

        fun AccountId.toAddress(): Address {
            return when (prefix) {
                IdPrefix.GROUP -> Group(this)
                IdPrefix.STANDARD -> Standard(this)
                IdPrefix.BLINDED, IdPrefix.BLINDEDV2 -> Blinded(this)
                else -> throw IllegalArgumentException("Unknown address prefix: $prefix")
            }
        }

        fun AccountId.toConversableAddress(): Address.Conversable? {
            return toAddress() as? Address.Conversable
        }

        @JvmField
        val CREATOR: Parcelable.Creator<Address> = object : Parcelable.Creator<Address> {
            override fun createFromParcel(parcel: Parcel): Address {
                val address = requireNotNull(parcel.readString()) {
                    "Invalid address from parcel. Must not be null."
                }

                return fromSerialized(address)
            }

            override fun newArray(size: Int): Array<Address?> = arrayOfNulls(size)
        }
    }
}

val Address.isStandard: Boolean
    get() = this is Address.Standard

val Address.isLegacyGroup: Boolean
    get() = this is Address.LegacyGroup

val Address.isGroupV2: Boolean
    get() = this is Address.Group

val Address.isCommunity: Boolean
    get() = this is Address.Community

val Address.isCommunityInbox: Boolean
    get() = this is Address.CommunityBlindedId

val Address.isGroup: Boolean
    get() = this is Address.Group || this is Address.LegacyGroup

val Address.isGroupOrCommunity: Boolean
    get() = isGroup || isCommunity

val Address.isBlinded: Boolean
    get() = this is Address.Blinded

/**
 * Converts this address to a blind [AccountId] if this address contains a blinded ID.
 */
fun Address.toBlinded(): Address.Blinded? {
    return (this as? Address.Blinded)
        ?: (this as? Address.CommunityBlindedId)?.blindedId
}

fun Address.toGroupString(): String {
    return when (this) {
        is Address.LegacyGroup, is Address.Community, is Address.Group -> address
        else -> throw IllegalArgumentException("Address is not a group: $this")
    }
}