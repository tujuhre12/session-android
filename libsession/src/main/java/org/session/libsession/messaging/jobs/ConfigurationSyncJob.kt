package org.session.libsession.messaging.jobs

import network.loki.messenger.libsession_util.Config
import network.loki.messenger.libsession_util.ConfigBase
import nl.komponents.kovenant.functional.bind
import org.session.libsession.messaging.MessagingModuleConfiguration
import org.session.libsession.messaging.messages.Destination
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.snode.RawResponse
import org.session.libsession.snode.SnodeAPI
import org.session.libsession.snode.SnodeAPI.SnodeBatchRequestInfo
import org.session.libsession.utilities.ConfigFactoryProtocol
import org.session.libsignal.utilities.Log
import java.util.concurrent.atomic.AtomicBoolean

class InvalidDestination: Exception("Trying to push configs somewhere other than our swarm or a closed group")
class InvalidContactDestination: Exception("Trying to push to non-user config swarm")

// only contact (self) and closed group destinations will be supported
data class ConfigurationSyncJob(val destination: Destination): Job {

    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 10

    val shouldRunAgain = AtomicBoolean(false)

    data class ConfigMessageInformation(val batch: SnodeBatchRequestInfo, val config: Config, val seqNo: Long?) // seqNo will be null for keys type

    data class SyncInformation(val configs: List<ConfigMessageInformation>, val toDelete: List<String>)

    private fun destinationConfigs(delegate: JobDelegate, configFactoryProtocol: ConfigFactoryProtocol): SyncInformation {
        TODO()
    }

    override suspend fun execute(dispatcherName: String) {
        val storage = MessagingModuleConfiguration.shared.storage

        val userPublicKey = storage.getUserPublicKey()
        val delegate = delegate ?: return Log.e("ConfigurationSyncJob", "No Delegate")
        if (destination is Destination.Contact && destination.publicKey != userPublicKey) {
            return delegate.handleJobFailedPermanently(this, dispatcherName, InvalidContactDestination())
        } else if (destination !is Destination.ClosedGroup) {
            return delegate.handleJobFailedPermanently(this, dispatcherName, InvalidDestination())
        }

        // configFactory singleton instance will come in handy for modifying hashes and fetching configs for namespace etc
        val configFactory = MessagingModuleConfiguration.shared.configFactory

        // **** start user ****
        // get latest states, filter out configs that don't need push
        val configsRequiringPush = configFactory.getUserConfigs().filter { config -> config.needsPush() }

        // don't run anything if we don't need to push anything
        if (configsRequiringPush.isEmpty()) return delegate.handleJobSucceeded(this, dispatcherName)

        // **** end user ****
        // allow null results here so the list index matches configsRequiringPush
        val sentTimestamp: Long = SnodeAPI.nowWithOffset
        val (batchObjects, toDeleteHashes) = destinationConfigs(delegate, configFactory)

        val toDeleteRequest = toDeleteHashes.let { toDeleteFromAllNamespaces ->
            if (toDeleteFromAllNamespaces.isEmpty()) null
            else SnodeAPI.buildAuthenticatedDeleteBatchInfo(destination.destinationPublicKey(), toDeleteFromAllNamespaces)
        }

        val allRequests = mutableListOf<SnodeBatchRequestInfo>()
        allRequests += batchObjects.map { (request) -> request }
        // add in the deletion if we have any hashes
        if (toDeleteRequest != null) {
            allRequests += toDeleteRequest
            Log.d(TAG, "Including delete request for current hashes")
        }

        val batchResponse = SnodeAPI.getSingleTargetSnode(destination.destinationPublicKey()).bind { snode ->
            SnodeAPI.getRawBatchResponse(
                snode,
                destination.destinationPublicKey(),
                allRequests,
                sequence = true
            )
        }

        try {
            val rawResponses = batchResponse.get()
            @Suppress("UNCHECKED_CAST")
            val responseList = (rawResponses["results"] as List<RawResponse>)
            // we are always adding in deletions at the end
            val deletionResponse = if (toDeleteRequest != null && responseList.isNotEmpty()) responseList.last() else null
            val deletedHashes = deletionResponse?.let {
                @Suppress("UNCHECKED_CAST")
                // get the sub-request body
                (deletionResponse["body"] as? RawResponse)?.let { body ->
                    // get the swarm dict
                    body["swarm"] as? RawResponse
                }?.mapValues { (_, swarmDict) ->
                    // get the deleted values from dict
                    ((swarmDict as? RawResponse)?.get("deleted") as? List<String>)?.toSet() ?: emptySet()
                }?.values?.reduce { acc, strings ->
                    // create an intersection of all deleted hashes (common between all swarm nodes)
                    acc intersect strings
                }
            } ?: emptySet()

            // at this point responseList index should line up with configsRequiringPush index
            batchObjects.forEachIndexed { index, (message, config, seqNo) ->
                val response = responseList[index]
                val responseBody = response["body"] as? RawResponse
                val insertHash = responseBody?.get("hash") as? String ?: run {
                    Log.w(TAG, "No hash returned for the configuration in namespace ${config.namespace()}")
                    return@forEachIndexed
                }
                Log.d(TAG, "Hash ${insertHash.take(4)} returned from store request for new config")

                // confirm pushed seqno
                if (config is ConfigBase) {
                    seqNo?.let {
                        config.confirmPushed(it, insertHash)
                    }
                }

                Log.d(TAG, "Successfully removed the deleted hashes from ${config.javaClass.simpleName}")
                // dump and write config after successful
                if (config is ConfigBase && config.needsDump()) { // usually this will be true?
                    configFactory.persist(config, (message.params["timestamp"] as String).toLong())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing batch request", e)
            return delegate.handleJobFailed(this, dispatcherName, e)
        }
        delegate.handleJobSucceeded(this, dispatcherName)
        if (shouldRunAgain.get() && storage.getConfigSyncJob(destination) == null) {
            // reschedule if something has updated since we started this job
            JobQueue.shared.add(ConfigurationSyncJob(destination))
        }
    }

    private fun getUserSyncInformation(delegate: JobDelegate) {
        val userEdKeyPair = MessagingModuleConfiguration.shared.getUserED25519KeyPair()
    }

    fun Destination.destinationPublicKey(): String = when (this) {
        is Destination.Contact -> publicKey
        is Destination.ClosedGroup -> publicKey
        else -> throw NullPointerException("Not public key for this destination")
    }

    override fun serialize(): Data {
        val (type, address) = when (destination) {
            is Destination.Contact -> CONTACT_TYPE to destination.publicKey
            is Destination.ClosedGroup -> GROUP_TYPE to destination.publicKey
            else -> return Data.EMPTY
        }
        return Data.Builder()
            .putInt(DESTINATION_TYPE_KEY, type)
            .putString(DESTINATION_ADDRESS_KEY, address)
            .build()
    }

    override fun getFactoryKey(): String = KEY

    companion object {
        const val TAG = "ConfigSyncJob"
        const val KEY = "ConfigSyncJob"

        // Keys used for DB storage
        const val DESTINATION_ADDRESS_KEY = "destinationAddress"
        const val DESTINATION_TYPE_KEY = "destinationType"

        // type mappings
        const val CONTACT_TYPE = 1
        const val GROUP_TYPE = 2

    }

    class Factory: Job.Factory<ConfigurationSyncJob> {
        override fun create(data: Data): ConfigurationSyncJob? {
            if (!data.hasInt(DESTINATION_TYPE_KEY) || !data.hasString(DESTINATION_ADDRESS_KEY)) return null

            val address = data.getString(DESTINATION_ADDRESS_KEY)
            val destination = when (data.getInt(DESTINATION_TYPE_KEY)) {
                CONTACT_TYPE -> Destination.Contact(address)
                GROUP_TYPE -> Destination.ClosedGroup(address)
                else -> return null
            }

            return ConfigurationSyncJob(destination)
        }
    }
}