package com.rhj.audio.service

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.MutableLiveData
import com.letianpai.robot.components.utils.GeeUILogUtils
import java.io.FileOutputStream
import java.util.concurrent.Executors

class RecordManager constructor(val context: Context) {

    val recordFilePathLiveData: MutableLiveData<String> = MutableLiveData()
    var stopListener: StopListener? = null
    private val useFourMic = false
    private val TAG = "RecordManager"
    private val countDownRunable = Runnable {
        logd("时间达到上限说话暂停录音: ")
        stopRecording()
    }
    private var isRecording = false
    private val audioSource = MediaRecorder.AudioSource.MIC
    private val sampleRateInHz = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO

    //    private val sampleRateInHz = 44100
//    private val channelConfig = AudioFormat.CHANNEL_IN_STEREO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize =
        AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat)


    private val recordExecutor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingPermission")
    private val audioRecord =
        AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSize)


    var bufferSizeFour = 16000 * 6 * 2 * 100 / 1000;//这里的100，指的是100ms audio data，可以根据您的实际需求设置合适的值

    //    var readBuff = Byte[]{}
    @SuppressLint("MissingPermission")
    var audioRecordFour = AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        32000,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSizeFour
    );


    private lateinit var fileOutputStream: FileOutputStream
    private var filePath: String? = null
    private val mHandler = Handler(Looper.getMainLooper())
    var dbChangeListener: OnDBChangeListener? = null

    fun startRecording() {
        if (isRecording) {
            cancelRecording()
        }
        isRecording = true
        mHandler.postDelayed(countDownRunable, 18 * 1000L)
        recordExecutor.execute {
            filePath = context.cacheDir.absolutePath + "tmp.pcm"
            fileOutputStream = FileOutputStream(filePath)
            logd("startRecording: ${filePath}  ${Thread.currentThread()}")
            if (useFourMic) {
                audioRecordFour.startRecording()
                val buffer = ByteArray(bufferSizeFour)
                var bytesRead: Int
                while (isRecording) {
                    bytesRead = audioRecordFour.read(buffer, 0, bufferSizeFour)
                    if (bytesRead > 0) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                    }
                    val shortArray = ShortArray(buffer.size / 2) {
                        (buffer[it * 2] + (buffer[(it * 2) + 1].toInt() shl 8)).toShort()
                    }
                    calculateDecibelLevel(buffer = shortArray)
                }
            } else {
                audioRecord.startRecording()
                val buffer = ByteArray(bufferSize)
                var bytesRead: Int
                while (isRecording) {
                    bytesRead = audioRecord.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        fileOutputStream.write(buffer, 0, bytesRead)
                    }
                    val shortArray = ShortArray(buffer.size / 2) {
                        (buffer[it * 2] + (buffer[(it * 2) + 1].toInt() shl 8)).toShort()
                    }
                    calculateDecibelLevel(buffer = shortArray)
                }
            }
        }
    }

    private var count = 0
    private var everSay = false
    private var lastTime = 0L
    private fun calculateDecibelLevel(buffer: ShortArray): Double {
        var current = System.currentTimeMillis()
        if (current - lastTime < 100) {
            return 0.0
        }
        lastTime = current


        var sum = 0.0
        for (sample in buffer) {
            sum += sample * sample
        }
        val rms = Math.sqrt(sum / buffer.size)
        val db = 20 * Math.log10(rms)
        if (dbChangeListener != null) {
            dbChangeListener!!.onChange(db.toInt())
        } else {
            if (db > 65) {
                logd("calculateDecibelLevel: 开始有人说话  ${db.toInt()} $count")
                count++
                if (count > 5) {
                    stopListener?.stop()
                    stopRecording()
                }
            } else {
                count = 0
                logd("calculateDecibelLevel: 开始录音之后还没有开始说话  ${db.toInt()}")
            }
        }
        return db
    }

    fun stopRecording() {
        logd("stopRecording: ")
        mHandler.removeCallbacks(countDownRunable)
//        showWakeGesture("avatar.understanding")
//        changeTips("录音结束")
        isRecording = false
        everSay = false
        if (useFourMic) {
            audioRecordFour.stop()
        } else {
            audioRecord.stop()
        }
        fileOutputStream.close()
        recordFilePathLiveData.postValue(filePath)
    }

    fun cancelRecording() {
        logd("cancelRecording: ")
        if (isRecording) {
            isRecording = false
            mHandler.removeCallbacks(countDownRunable)
//        showWakeGesture("avatar.understanding")
//        changeTips("录音结束")
            isRecording = false
            everSay = false
            if (useFourMic) {
                audioRecordFour.stop()
            } else {
                audioRecord.stop()
            }
            fileOutputStream.close()
        }
    }

    interface StopListener {
        fun stop()
    }

    fun logd(msg: String) {
        GeeUILogUtils.logd("RecordManager", msg)
    }

    interface OnDBChangeListener {
        fun onChange(dbNumber: Int)
    }
}