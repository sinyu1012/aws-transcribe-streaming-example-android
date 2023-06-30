package com.aws.translate.stream.example.lib;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.transcribestreaming.TranscribeStreamingAsyncClient;
import software.amazon.awssdk.services.transcribestreaming.model.MediaEncoding;
import software.amazon.awssdk.services.transcribestreaming.model.Result;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionRequest;
import software.amazon.awssdk.services.transcribestreaming.model.StartStreamTranscriptionResponse;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptEvent;
import software.amazon.awssdk.services.transcribestreaming.model.TranscriptResultStream;
import software.amazon.awssdk.services.translate.TranslateClient;
import software.amazon.awssdk.services.translate.model.TranslateTextRequest;
import software.amazon.awssdk.services.translate.model.TranslateTextResponse;

public class AwsTranscribeAndTranslate {

    private static final String TAG = "AwsTranscribeAndTranslate";
    private TranscribeStreamingAsyncClient transcribeClient;
    private TranslateClient translateClient;
    private String sourceLanguage;
    private String translateSourceLanguage;

    private String targetLanguage;
    private TranslationListener listener;
    private Region region;
    private AtomicBoolean transcribeInProgress = new AtomicBoolean(false);
    private TranscribeStreamingRetryClient streamingRetryClient;
    private AudioStreamPublisher requestStream;


    public AwsTranscribeAndTranslate(String awsAccessKey, String awsSecretKey, String sourceLanguage, String targetLanguage, TranslationListener listener) {

        AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
        AwsCredentialsProvider awsCredentialsProvider = StaticCredentialsProvider.create(awsBasicCredentials);

        this.region = Region.US_EAST_1;
        String endpoint = "https://transcribestreaming." + region.toString().toLowerCase().replace('_', '-') + ".amazonaws.com";

        this.transcribeClient = TranscribeStreamingAsyncClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(region)
                .endpointOverride(URI.create(endpoint))
                .build();

        streamingRetryClient = new TranscribeStreamingRetryClient(transcribeClient);

        this.translateClient = TranslateClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .credentialsProvider(awsCredentialsProvider)
                .region(region)
                .build();


        this.sourceLanguage = sourceLanguage;
        this.targetLanguage = targetLanguage;
        this.listener = listener;
    }


    public AwsTranscribeAndTranslate(String awsAccessKey, String awsSecretKey, String regionId, TranslationListener listener) {

        AwsBasicCredentials awsBasicCredentials = AwsBasicCredentials.create(awsAccessKey, awsSecretKey);
        AwsCredentialsProvider awsCredentialsProvider = StaticCredentialsProvider.create(awsBasicCredentials);

        this.region = getRegionById(regionId);
        Log.d(TAG, "AwsTranscribeAndTranslate: " + region.toString().toLowerCase().replace('_', '-'));
        String endpoint = "https://transcribestreaming." + region.toString().toLowerCase().replace('_', '-') + ".amazonaws.com";

        this.transcribeClient = TranscribeStreamingAsyncClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(region)
                .endpointOverride(URI.create(endpoint))
                .build();

        streamingRetryClient = new TranscribeStreamingRetryClient(transcribeClient);

        this.translateClient = TranslateClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .credentialsProvider(awsCredentialsProvider)
                .region(region)
                .build();

        this.listener = listener;
    }

    public void setTranscribeLanguage(String sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    /**
     * Close clients and streams
     */
    public void close() {
        try {
            if (requestStream != null) {
                requestStream.inputStream.close();
            }
        } catch (IOException ex) {
            System.out.println("error closing in-progress microphone stream: " + ex);
        } finally {
            streamingRetryClient.close();
        }
    }

    public void stopTranscription() {
        if (requestStream != null) {
            try {
                requestStream.inputStream.close();
            } catch (IOException ex) {
                System.out.println("Error stopping input stream: " + ex);
            } finally {
                requestStream = null;
            }
        }
    }

    public void processAudioFrame(InputStream audioFrame) {
        if (transcribeInProgress.compareAndSet(false, true)) {
            requestStream = new AudioStreamPublisher(audioFrame);
            CompletableFuture<Void> result = streamingRetryClient.startStreamTranscription(
                    getTranscribeRequest(16000),
                    requestStream,
                    getResponseHandler());

            result.whenComplete((r, ex) -> {
                if (ex != null) {
                    listener.onError(ex);
                } else {
                    listener.onComplete();
                }
                transcribeInProgress.set(false);
            });
        }
    }

    private StartStreamTranscriptionRequest getTranscribeRequest(Integer mediaSampleRateHertz) {
        return StartStreamTranscriptionRequest.builder()
                .languageCode(sourceLanguage)
                .mediaEncoding(MediaEncoding.PCM)
                .mediaSampleRateHertz(mediaSampleRateHertz)
                .build();
    }


    private StreamTranscriptionBehavior getResponseHandler() {
        return new StreamTranscriptionBehavior() {

            @Override
            public void onError(Throwable e) {
                listener.onError(e);
                System.out.println("Error Occurred: " + e);
            }


            @Override
            public void onStream(TranscriptResultStream event) {
                List<Result> results = ((TranscriptEvent) event).transcript().results();
                System.out.println(String.format("=== onStream " + results.size() + "==="));

                if (results.size() > 0) {
                    if (!results.get(0).alternatives().get(0).transcript().isEmpty()) {
                        String transcript = results.get(0).alternatives().get(0).transcript();
                        System.out.println(String.format("=== onStream " + transcript + "==="));
                        JSONObject jsonObjectResult = new JSONObject();
                        try {
                            jsonObjectResult.put("transcript", transcript);

                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        listener.onTranscribe(jsonObjectResult.toString());
                        String translation = translateText(transcript, sourceLanguage, targetLanguage);
                        listener.onTranslation(translation);
                    }
                }
            }

            @Override
            public void onResponse(StartStreamTranscriptionResponse r) {
                System.out.println(String.format("=== Received Initial response. Request Id: %s ===", r.requestId()));
            }

            @Override
            public void onComplete() {
                System.out.println("=== All records streamed successfully ===");
                listener.onComplete();
            }
        };
    }


    public String translateText(String text, String sourceLanguage, String targetLanguage) {
        TranslateTextRequest translateTextRequest = TranslateTextRequest.builder()
                .sourceLanguageCode(sourceLanguage)
                .targetLanguageCode(targetLanguage)
                .text(text)
                .build();
        TranslateTextResponse translateTextResponse = translateClient.translateText(translateTextRequest);
        return translateTextResponse.translatedText();
    }


    public interface TranslationListener {

        void onTranscribe(String text);

        void onTranslation(String translatedText);

        void onError(Throwable exception);

        void onComplete();
    }

    public static Region getRegionById(String regionId) {
        if (regionId == null) {
            throw new IllegalArgumentException("Region ID cannot be null");
        }

        for (Region region : Region.regions()) {
            if (regionId.equals(region.id())) {
                return region;
            }
        }

        throw new IllegalArgumentException("Invalid Region ID: " + regionId);
    }

}
