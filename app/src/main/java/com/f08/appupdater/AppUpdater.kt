package com.f08.appupdater

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.registerReceiver
import androidx.core.content.FileProvider
import com.google.android.material.progressindicator.CircularProgressIndicator
import java.io.File
import kotlin.math.roundToInt

class AppUpdater(private val context: Context) {
    var mAct = context as Activity
    private lateinit var progressDialog: androidx.appcompat.app.AlertDialog
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var progressText: TextView

    // Permission request codes
    private val STORAGE_PERMISSION_REQUEST_CODE = 100
    private val INSTALL_PERMISSION_REQUEST_CODE = 101


    @RequiresApi(Build.VERSION_CODES.N)
    fun Update(apkUrl: String){
        checkPermissionsAndProceed(apkUrl)
    }

    // Checking and requesting permissions
    @RequiresApi(Build.VERSION_CODES.N)
    fun checkPermissionsAndProceed(apkUrl: String) {
        // Check if the permissions are granted
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED) {
            // Check if the app can write to external storage (for devices below API 29)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {

                // Check install permission for Android 8.0 and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                    requestInstallPermission()
                } else {
                    // Proceed with update if permissions are granted
                    checkUpdate(apkUrl)
                }
            } else {
                // Proceed with update if permission is granted
                checkUpdate(apkUrl)
            }
        } else {
            // Request INTERNET permission
            ActivityCompat.requestPermissions(mAct, arrayOf(Manifest.permission.INTERNET), STORAGE_PERMISSION_REQUEST_CODE)
        }
    }

    // Request INSTALL_PACKAGES permission (for Android 8.0 and above)
    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            intent.data = Uri.parse("package:" + context.packageName)
            mAct.startActivityForResult(intent, INSTALL_PERMISSION_REQUEST_CODE)
        }
    }

    // Handle the result of permission request
    @RequiresApi(Build.VERSION_CODES.N)
    fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Proceed with update if permission is granted
                    checkUpdate("APK_URL_HERE")
                } else {
                    Toast.makeText(context, "Permission denied", Toast.LENGTH_SHORT).show()
                }
            }
            INSTALL_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Proceed with update if install permission is granted
                    checkUpdate("APK_URL_HERE")
                } else {
                    Toast.makeText(context, "Permission to install APKs is denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun checkUpdate(url:String){
        showUpdateDialog(url)
    }


    @RequiresApi(Build.VERSION_CODES.N)
    private fun showUpdateDialog(apkUrl: String) {
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.dialog_update_title))
            .setMessage(context.getString(R.string.dialog_update_body))
            .setCancelable(false)
            .setPositiveButton(context.getString(R.string.dialog_update_button_update)) { _, _ ->
                downloadAndInstall(apkUrl)
            }
            .setNegativeButton(context.getString(R.string.dialog_update_button_cancel)) { _, _ ->
                //if force update
               /* if (false){
                    System.exit(0)
                }*/
            }
            .show()
    }




    @RequiresApi(Build.VERSION_CODES.N)
    @SuppressLint("Range")
    private fun downloadAndInstall(apkUrl: String) {
        showProgressDialog()

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(apkUrl)

        val request = DownloadManager.Request(uri).apply {
            setTitle("Mise à jour de l'application")
            setDescription(context.getString(R.string.dialog_update_downloading))
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "app.apk")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        }

        val downloadId = downloadManager.enqueue(request)

        // Monitor download status
        val onComplete = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    progressDialog.dismiss()
                    installApk()
                    context.unregisterReceiver(this)
                }
            }
        }
        context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))

        // Update progress
        Thread {
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                    when (status) {
                        DownloadManager.STATUS_RUNNING -> {
                            val totalBytes = cursor.getLong(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            if (totalBytes > 0) {
                                val downloadedBytes = cursor.getLong(cursor.getColumnIndex(
                                    DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                                val progress = (downloadedBytes * 100 / totalBytes).toInt()
                                Handler(Looper.getMainLooper()).post {
                                    progressIndicator.setProgress(progress.toDouble().roundToInt(), true)
                                    /*progressIndicator.setProgressTextAdapter { currentProgress ->
                                        "${currentProgress.toInt()}%"
                                    }*/

                                    //progressText.text = "Downloading: $progress%"
                                }
                            }
                        }
                        DownloadManager.STATUS_SUCCESSFUL, DownloadManager.STATUS_FAILED -> {
                            downloading = false
                        }
                    }
                }
                cursor?.close()
            }
        }.start()
    }



    private fun installApk() {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "app.apk")
        val apkUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider", // FileProvider authority
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        mAct.startActivity(intent)
    }




    private fun showProgressDialog() {
        val builder = androidx.appcompat.app.AlertDialog.Builder(context)
        val inflater = mAct.layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_progress, null)
        builder.setView(dialogView)
        builder.setCancelable(false)
        progressDialog = builder.create()
        progressDialog.show()

        progressIndicator = dialogView.findViewById(R.id.circularProgressIndicator)
        progressText = dialogView.findViewById(R.id.progressText)
    }
}