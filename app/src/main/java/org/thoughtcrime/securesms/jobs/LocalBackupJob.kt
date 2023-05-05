package org.thoughtcrime.securesms.jobs

import android.content.Context
import org.session.libsession.messaging.jobs.Job
import org.session.libsession.messaging.jobs.JobDelegate
import org.session.libsession.messaging.utilities.Data
import org.session.libsignal.utilities.Log
import org.thoughtcrime.securesms.notifications.NotificationChannels
import org.thoughtcrime.securesms.service.GenericForegroundService
import org.thoughtcrime.securesms.util.BackupUtil.createBackupFile
import org.thoughtcrime.securesms.util.BackupUtil.deleteAllBackupFiles

import network.loki.messenger.R


class LocalBackupJob:Job {
    override var delegate: JobDelegate? = null
    override var id: String? = null
    override var failureCount: Int = 0
    override val maxFailureCount: Int = 0

    lateinit var context: Context

    companion object {
        val TAG = LocalBackupJob::class.simpleName
        val KEY: String = "LocalBackupJob"
    }

    override fun execute(dispatcherName: String) {
        Log.i(TAG, "Executing backup job...")

        GenericForegroundService.startForegroundTask(
            context,
            context.getString(R.string.LocalBackupJob_creating_backup),
            NotificationChannels.BACKUPS,
            R.drawable.ic_launcher_foreground
        )

        // TODO: Maybe create a new backup icon like ic_signal_backup?
        try {
            val record = createBackupFile(context)
            deleteAllBackupFiles(context, listOf(record))
        } finally {
            GenericForegroundService.stopForegroundTask(context)
        }
    }

    override fun serialize(): Data {
        return  Data.EMPTY
    }

    override fun getFactoryKey(): String {
        return KEY
    }

    class Factory: Job.Factory<LocalBackupJob> {
        override fun create(data: Data): LocalBackupJob {
            return LocalBackupJob()
        }
    }
}