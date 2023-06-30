package com.aws.translate.stream.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aws.translate.stream.example.lib.AwsTranscribeAndTranslate
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private var isRecording = false
    private var startStopButton: Button? = null
    private var transcriptionText: TextView? = null
    private var transcribeText: TextView? = null

    private var transcriberAndTranslator: AwsTranscribeAndTranslate? = null
    var audioInputStream: AndroidAudioInputStream =
        AndroidAudioInputStream(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        startStopButton = findViewById<Button>(R.id.startStopButton)
        transcriptionText = findViewById<TextView>(R.id.transcriptionText)
        transcribeText = findViewById<TextView>(R.id.transcribeText)

        transcriberAndTranslator = AwsTranscribeAndTranslate(
            "xxxxx", // todo
            "xxxxx",
            "zh-CN",
            "en-US",
            object : AwsTranscribeAndTranslate.TranslationListener {

                override fun onTranscribe(text: String) {
                    runOnUiThread {
                        transcribeText?.text = text;
                    }
                }


                override fun onTranslation(translatedText: String) {
                    runOnUiThread {
                        transcriptionText?.text = translatedText;
                    }
                }

                override fun onError(exception: Throwable) {
                    exception.printStackTrace()
                    Log.e("", "onError: ${exception.stackTraceToString()}")
                }

                override fun onComplete() {
                    // Do something when complete
                }
            })

    }


    fun onStartStopClick(view: View?) {
        if (!isRecording) {
            startRecording()
            transcriberAndTranslator?.processAudioFrame(audioInputStream)
        } else {
            stopRecording()
        }
    }


    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf<String>(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO_PERMISSION
            )
        } else {
            try {
                audioInputStream.startRecording()
                isRecording = true
                startStopButton!!.text = "Stop Transcription"
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun stopRecording() {
        audioInputStream.stopRecording()
        transcriberAndTranslator?.stopTranscription()
        startStopButton!!.text = "Start Microphone "
    }


    companion object {
        private const val REQUEST_RECORD_AUDIO_PERMISSION = 200
    }

}