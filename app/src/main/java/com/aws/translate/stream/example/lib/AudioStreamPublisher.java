
package com.aws.translate.stream.example.lib;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

import java.io.InputStream;

import software.amazon.awssdk.services.transcribestreaming.model.AudioStream;

/**
 * AudioStreamPublisher implements audio stream publisher.
 * AudioStreamPublisher emits audio stream asynchronously in a separate thread
 */
public class AudioStreamPublisher implements Publisher<AudioStream> {

    final InputStream inputStream;

    public AudioStreamPublisher(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void subscribe(Subscriber<? super AudioStream> s) {
        s.onSubscribe(new ByteToAudioEventSubscription(s, inputStream));
    }
}
