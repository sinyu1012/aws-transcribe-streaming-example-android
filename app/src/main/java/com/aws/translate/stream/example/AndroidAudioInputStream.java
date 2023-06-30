package com.aws.translate.stream.example;

import android.annotation.SuppressLint;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.io.IOException;
import java.io.InputStream;

public class AndroidAudioInputStream extends InputStream {

    private AudioRecord audioRecord;
    private int sampleRate;
    private int channelConfig;
    private int audioFormat;
    private int bufferSize;
    private byte[] buffer;

    @SuppressLint("MissingPermission")
    public AndroidAudioInputStream(int sampleRate, int channelConfig, int audioFormat) {
        this.sampleRate = sampleRate;
        this.channelConfig = channelConfig;
        this.audioFormat = audioFormat;

        this.bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
        this.audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize);
        this.buffer = new byte[bufferSize];
    }

    public void startRecording() {
        audioRecord.startRecording();
    }

    public void stopRecording() {
        audioRecord.stop();
    }

    @Override
    public int read() throws IOException {
        int bytesRead = audioRecord.read(buffer, 0, 1);
        return (bytesRead > 0) ? buffer[0] : -1;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return audioRecord.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        audioRecord.release();
    }

}
