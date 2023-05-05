package org.thoughtcrime.securesms.jobs

import androidx.annotation.Nullable

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import com.fasterxml.jackson.annotation.JsonProperty
import network.loki.messenger.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobDelegate
import org.session.libsession.messaging.utilities.Data
import org.session.libsession.utilities.FileUtils
import org.session.libsession.utilities.TextSecurePreferences.Companion.getUpdateApkDigest
import org.session.libsession.utilities.TextSecurePreferences.Companion.getUpdateApkDownloadId
import org.session.libsession.utilities.TextSecurePreferences.Companion.setUpdateApkDigest
import org.session.libsession.utilities.TextSecurePreferences.Companion.setUpdateApkDownloadId
import org.session.libsignal.utilities.Hex
import org.session.libsignal.utilities.JsonUtil
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.service.UpdateApkReadyListener
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest

class UpdateApkJob: Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 0

    lateinit var context: Context

    companion object {
        val TAG = UpdateApkJob::class.simpleName
        val KEY: String = "UpdateApkJob"
    }

    override fun execute(dispatcherName: String) {
        if (!BuildConfig.PLAY_STORE_DISABLED) return

        Log.i(TAG, "Checking for APK update...")

        val client = OkHttpClient()
        val request = Request.Builder().url(String.format("%s/latest.json", BuildConfig.NOPLAY_UPDATE_URL)).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException("Bad response: " + response.message())
        }

        val updateDescriptor: UpdateDescriptor = JsonUtil.fromJson(
            response.body()!!.string(),
            UpdateDescriptor::class.java
        )
        val digest = Hex.fromStringCondensed(updateDescriptor.digest)

        Log.i(
            TAG,
            "Got descriptor: $updateDescriptor"
        )

        if (updateDescriptor.versionCode > getVersionCode()) {
            val downloadStatus: DownloadStatus = getDownloadStatus(updateDescriptor.url, digest)
            Log.i(TAG, "Download status: " + downloadStatus.status)
            if (downloadStatus.status == DownloadStatus.Status.COMPLETE) {
                Log.i(TAG, "Download status complete, notifying...")
                handleDownloadNotify(downloadStatus.downloadId)
            } else if (downloadStatus.status == DownloadStatus.Status.MISSING) {
                Log.i(TAG, "Download status missing, starting download...")
                handleDownloadStart(
                    updateDescriptor.url,
                    updateDescriptor.versionName,
                    digest
                )
            }
        }
    }

    @Throws(PackageManager.NameNotFoundException::class)
    private fun getVersionCode(): Int {
        val packageManager: PackageManager = context.getPackageManager()
        val packageInfo: PackageInfo = packageManager.getPackageInfo(context.getPackageName(), 0)
        return packageInfo.versionCode
    }

    private fun getDownloadStatus(uri: String, theirDigest: ByteArray): DownloadStatus {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query()
        query.setFilterByStatus(DownloadManager.STATUS_PAUSED or DownloadManager.STATUS_PENDING or DownloadManager.STATUS_RUNNING or DownloadManager.STATUS_SUCCESSFUL)
        val pendingDownloadId = getUpdateApkDownloadId(context)
        val pendingDigest = getPendingDigest(context)
        val cursor = downloadManager.query(query)
        return try {
            var status = DownloadStatus(DownloadStatus.Status.MISSING, -1)
            while (cursor != null && cursor.moveToNext()) {
                val jobStatus =
                    cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val jobRemoteUri =
                    cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_URI))
                val downloadId =
                    cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                val digest = getDigestForDownloadId(downloadId)
                if (jobRemoteUri != null && jobRemoteUri == uri && downloadId == pendingDownloadId) {
                    if (jobStatus == DownloadManager.STATUS_SUCCESSFUL && digest != null && pendingDigest != null &&
                        MessageDigest.isEqual(pendingDigest, theirDigest) &&
                        MessageDigest.isEqual(digest, theirDigest)
                    ) {
                        return DownloadStatus(DownloadStatus.Status.COMPLETE, downloadId)
                    } else if (jobStatus != DownloadManager.STATUS_SUCCESSFUL) {
                        status = DownloadStatus(DownloadStatus.Status.PENDING, downloadId)
                    }
                }
            }
            status
        } finally {
            cursor?.close()
        }
    }

    private fun handleDownloadStart(uri: String, versionName: String, digest: ByteArray) {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadRequest = DownloadManager.Request(Uri.parse(uri))
        downloadRequest.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        downloadRequest.setTitle("Downloading Signal update")
        downloadRequest.setDescription("Downloading Signal $versionName")
        downloadRequest.setVisibleInDownloadsUi(false)
        downloadRequest.setDestinationInExternalFilesDir(context, null, "signal-update.apk")
        downloadRequest.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
        val downloadId = downloadManager.enqueue(downloadRequest)
        setUpdateApkDownloadId(context, downloadId)
        setUpdateApkDigest(context, Hex.toStringCondensed(digest))
    }

    private fun handleDownloadNotify(downloadId: Long) {
        val intent = Intent(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        intent.putExtra(DownloadManager.EXTRA_DOWNLOAD_ID, downloadId)
        UpdateApkReadyListener().onReceive(context, intent)
    }

    private fun getDigestForDownloadId(downloadId: Long): ByteArray? {
        return try {
            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fin = FileInputStream(downloadManager.openDownloadedFile(downloadId).fileDescriptor)
            val digest = FileUtils.getFileDigest(fin)
            fin.close()
            digest
        } catch (e: IOException) {
            Log.w(TAG, e)
            null
        }
    }

    private fun getPendingDigest(context: Context): ByteArray? {
        return try {
            val encodedDigest = getUpdateApkDigest(context) ?: return null
            Hex.fromStringCondensed(encodedDigest)
        } catch (e: IOException) {
            Log.w(TAG, e)
            null
        }
    }

    override fun serialize(): Data {
        return Data.EMPTY
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    private class UpdateDescriptor(
        @JsonProperty("versionCode") @Nullable val versionCode: Int,
        @JsonProperty("versionName") @Nullable val versionName: String,
        @JsonProperty("url") @Nullable val url: String,
        @JsonProperty("sha256sum") @Nullable val digest: String)
    {
        override fun toString(): String {
            return "[$versionCode, $versionName, $url]"
        }
    }

    private class DownloadStatus(val status: Status, val downloadId: Long) {
        enum class Status {
            PENDING, COMPLETE, MISSING
        }
    }

    class Factory: Job.Factory<UpdateApkJob> {
        override fun create(data: Data): UpdateApkJob {
            return UpdateApkJob()
        }
    }
}