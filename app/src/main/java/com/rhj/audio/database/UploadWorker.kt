package com.rhj.audio.database


import android.app.Application
import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.work.Worker
import androidx.work.WorkerParameters

class UploadWorker(var appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        return try {

            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }

    private fun toastAndLog(message: String) {
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        Log.e(TAG, message)
    }

    companion object {
        private const val TAG = "UploadWorker"
    }
}