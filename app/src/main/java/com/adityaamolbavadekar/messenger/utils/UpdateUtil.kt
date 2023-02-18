package com.adityaamolbavadekar.messenger.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.adityaamolbavadekar.messenger.BuildConfig
import com.adityaamolbavadekar.messenger.managers.PrefsManager
import com.adityaamolbavadekar.messenger.model.UpdateInfo
import com.adityaamolbavadekar.messenger.utils.extensions.simpleDateFormat
import com.adityaamolbavadekar.messenger.utils.logging.InternalLogger
import com.google.firebase.database.ktx.database
import com.google.firebase.database.ktx.getValue
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import java.io.File

/**
 * Checks for Updates by comparing VersionCode.
 * */
class UpdateUtil {

    companion object {

        private val TAG = UpdateUtil::class.java.simpleName
        fun checkForUpdates(context: Context, callback: OnResponseCallback<UpdateInfo, Exception>) {
            val prefs = PrefsManager(context)
            // Don't check for updates if already checked for the day.
            prefs.getLastUpdateChecked()
                ?.let {
                    val info = prefs.getUpdateInfo()
                    if (simpleDateFormat(it) == simpleDateFormat(System.currentTimeMillis())) return
                    else if (info != null && info.versionCode > BuildConfig.VERSION_CODE) return callback.onSuccess(
                        info
                    )
                }
            Firebase.database.getReference(Constants.CloudPaths.CLOUD_PATH_UPDATES)
                .get()
                .addOnSuccessListener {
                    try {
                        val updateInfo = it.getValue<UpdateInfo>()
                        if (updateInfo == null) callback.onFailure(NullPointerException())
                        else {
                            PrefsManager(context).saveUpdateInfo(updateInfo)
                            callback.onSuccess(updateInfo)
                        }
                    } catch (e: Exception) {
                        callback.onFailure(e)
                    }
                }
                .addOnFailureListener { callback.onFailure(it) }
        }

        fun startUpdate(context: Context, updateInfo: UpdateInfo) {
            val localTempFile = File.createTempFile("MessengerUpdateFile", "apk")
            Firebase.storage
                .getReferenceFromUrl(updateInfo.link!!)
                .getFile(localTempFile)
                .addOnSuccessListener {
                    InternalLogger.logD(
                        TAG,
                        "Successfully downloaded update file for $updateInfo to ${localTempFile.absolutePath}"
                    )
                    localTempFile.setReadable(true, false)
                    installApk(context, localTempFile)
                }
                .addOnFailureListener {
                    InternalLogger.logW(
                        TAG,
                        "Unable to get update file for $updateInfo to ${localTempFile.absolutePath}",
                        it
                    )
                }
        }

        private fun installApk(context: Context, localTempFile: File) {
            try {
                Intent(Intent.ACTION_VIEW).apply {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    setDataAndType(
                        Uri.fromFile(localTempFile),
                        "application/vnd.android.package-archive"
                    )
                    putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    context.startActivity(this)
                }
            } catch (e: Exception) {
                InternalLogger.logE(TAG, "Unable to launch install apk intent", e)
            }
        }
    }
}