package com.linc.amplituda;

import android.content.Context;
import android.webkit.URLUtil;

import com.linc.amplituda.exceptions.AmplitudaException;
import com.linc.amplituda.exceptions.io.FileNotFoundException;
import com.linc.amplituda.exceptions.io.InvalidAudioUrlException;
import com.linc.amplituda.exceptions.processing.ProcessCancelledException;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Main entry point for the Amplituda library.
 * <p>
 * Processes an audio file at a local path or remote URL and extracts per-frame amplitude data.
 * Only one processing task runs at a time: calling
 * {@link #processAudio(String, Compress, Cache)} automatically cancels any task that is
 * currently running before submitting the new one. A task can also be cancelled explicitly
 * via {@link #cancel()}.
 * <p>
 * Call {@link #release()} when the instance is no longer needed to free the internal
 * background thread.
 *
 * @author Hamza417
 */
public final class Amplituda {

    private final FileManager fileManager;
    private final ExecutorService executor;
    private final AtomicReference<Future<?>> currentTask;

    /**
     * Creates a new {@code Amplituda} instance.
     *
     * @param context Android context used to access app resources and the cache directory
     */
    public Amplituda(final Context context) {
        fileManager = new FileManager(context);
        executor = Executors.newSingleThreadExecutor();
        currentTask = new AtomicReference<>();
    }

    /**
     * Enables or disables Amplituda logs for more detailed processing information.
     *
     * @param priority Android Log constant, for example {@code Log.DEBUG}
     * @param enable   {@code true} to enable logs, {@code false} to disable
     * @return this instance for chaining
     */
    public Amplituda setLogConfig(final int priority, final boolean enable) {
        AmplitudaLogger.enable(enable);
        AmplitudaLogger.priority(priority);
        return this;
    }

    /**
     * Clears all amplitude data that has been stored in the cache.
     *
     * @return this instance for chaining
     */
    public Amplituda clearCache() {
        fileManager.clearAllCacheFiles();
        return this;
    }

    /**
     * Clears any cached amplitude data associated with the given audio path or URL.
     *
     * @param audio the local file path or URL of the audio whose cache should be cleared
     * @return this instance for chaining
     */
    public Amplituda clearCache(final String audio) {
        fileManager.clearCache(String.valueOf(audio.hashCode()));
        return this;
    }

    /**
     * Cancels the currently running or queued processing task, if any.
     * <p>
     * The associated {@link AmplitudaProcessingTask} will silently discard the result.
     * If an error listener is attached it will receive a {@link ProcessCancelledException}.
     */
    public void cancel() {
        Future<?> task = currentTask.getAndSet(null);
        if (task != null) {
            task.cancel(true);
        }
    }

    /**
     * Shuts down the internal background executor.
     * <p>
     * Call this when the {@code Amplituda} instance is no longer needed, for example in
     * {@code Activity.onDestroy()}, to avoid leaking the background thread. Any running
     * task is cancelled before the executor is stopped.
     */
    public void release() {
        cancel();
        executor.shutdownNow();
    }

    /**
     * Starts processing the audio file at the given local path or remote URL.
     * <p>
     * If a previous processing task is still running or waiting it is cancelled immediately
     * before this new task is submitted. Processing runs on a dedicated background thread;
     * the returned {@link AmplitudaProcessingTask} can be used to block for the result or
     * attach success and error callbacks.
     *
     * @param path     absolute local file path or remote URL of the audio file to process
     * @param compress compression parameters that control how amplitude samples are reduced
     * @param cache    caching parameters that control whether results are stored or reused
     * @return an {@link AmplitudaProcessingTask} representing the pending result
     */
    public AmplitudaProcessingTask<String> processAudio(
            final String path,
            final Compress compress,
            final Cache cache
    ) {
        cancel();
        Future<AmplitudaProcessingOutput<String>> future = executor.submit(
                () -> executeProcessing(path, compress, cache)
        );
        currentTask.set(future);
        return new AmplitudaProcessingTask<>(future);
    }

    /**
     * Internal processing entry point that runs on the background thread.
     * Handles both local file paths and remote URLs.
     *
     * @param path     local path or URL of the audio file
     * @param compress compression parameters
     * @param cache    caching parameters
     * @return the processing output containing amplitude data or an error
     */
    private AmplitudaProcessingOutput<String> executeProcessing(
            final String path,
            final Compress compress,
            final Cache cache
    ) {
        InputAudio<String> inputAudio = new InputAudio<>(path);
        try {
            checkCancelled();

            if (!URLUtil.isValidUrl(path)) {
                // Local file path
                inputAudio.setType(InputAudio.Type.PATH);
                return new AmplitudaProcessingOutput<>(
                        processFileJNI(new File(path), inputAudio, compress, cache),
                        inputAudio
                );
            }

            // Remote URL: download to a temporary file first
            inputAudio.setType(InputAudio.Type.URL);
            long startTime = System.currentTimeMillis();
            AmplitudaLogger.log("Downloading audio from URL: " + path);

            File tempAudio = fileManager.getUrlFile(path, null);
            if (tempAudio == null) {
                return errorOutput(new InvalidAudioUrlException(), inputAudio);
            }

            AmplitudaLogger.logOperationTime(AmplitudaLogger.OPERATION_PREPARING, startTime);
            checkCancelled();

            AmplitudaResultJNI result = processFileJNI(tempAudio, inputAudio, compress, cache);
            fileManager.deleteFile(tempAudio);
            return new AmplitudaProcessingOutput<>(result, inputAudio);

        } catch (AmplitudaException exception) {
            return errorOutput(exception, inputAudio);
        }
    }

    /**
     * Runs the native amplitude extraction on a local {@link File}.
     * <p>
     * Attempts to reuse a cached result when the cache policy allows it. If no valid
     * cache entry is found the JNI decoder is invoked and the result may be persisted
     * according to the cache policy.
     *
     * @param audioFile  the audio file to decode
     * @param inputAudio metadata container for the audio source
     * @param compress   compression parameters
     * @param cache      caching parameters
     * @param <T>        the audio source type held by {@code inputAudio}
     * @return raw JNI result containing the amplitude string and duration
     * @throws AmplitudaException if the file does not exist or a native error occurs
     */
    private synchronized <T> AmplitudaResultJNI processFileJNI(
            final File audioFile,
            final InputAudio<T> inputAudio,
            final Compress compress,
            final Cache cache
    ) throws AmplitudaException {
        if (!audioFile.exists()) {
            throw new FileNotFoundException();
        }

        long startTime = System.currentTimeMillis();
        File cacheFile = fileManager.getCacheFile(
                String.valueOf(audioFile.hashCode()),
                cache.getKey()
        );

        AmplitudaResultJNI result = null;
        if (cache.getState() == Cache.REUSE) {
            result = amplitudesFromCache(cacheFile);
        }

        if (result == null) {
            AmplitudaLogger.log("Process audio " + audioFile.getPath());
            result = amplitudesFromAudioJNI(
                    audioFile.getPath(),
                    compress.getType(),
                    compress.getPreferredSamplesPerSecond(),
                    cacheFile.getPath(),
                    cache.isEnabled(),
                    null
            );
        } else {
            AmplitudaLogger.log(
                    String.format(
                            Locale.US,
                            "Found cache data \"%s\" for audio \"%s\"",
                            cacheFile.getName(),
                            audioFile.getName()
                    )
            );
        }

        inputAudio.setDuration(result.getDurationMillis());
        AmplitudaLogger.logOperationTime(AmplitudaLogger.OPERATION_PROCESSING, startTime);
        return result;
    }

    /**
     * Wraps an {@link AmplitudaException} in an error-only {@link AmplitudaProcessingOutput}.
     *
     * @param exception  the exception that caused processing to fail
     * @param inputAudio metadata container for the audio source
     * @param <T>        the audio source type
     * @return a processing output that carries the exception and no amplitude data
     */
    private <T> AmplitudaProcessingOutput<T> errorOutput(
            final AmplitudaException exception,
            final InputAudio<T> inputAudio
    ) {
        return new AmplitudaProcessingOutput<>(exception, inputAudio);
    }

    /**
     * Checks whether the current thread has been interrupted and throws
     * {@link ProcessCancelledException} if so.
     *
     * @throws ProcessCancelledException when the executing thread carries an interrupt signal
     */
    private void checkCancelled() throws ProcessCancelledException {
        if (Thread.currentThread().isInterrupted()) {
            throw new ProcessCancelledException();
        }
    }

    /**
     * Attempts to read previously computed amplitude data from a cache file.
     *
     * @param audioCache the cache file to read
     * @return a populated {@link AmplitudaResultJNI} on success, or {@code null} if the
     *         cache file is empty, missing, or malformed
     */
    private AmplitudaResultJNI amplitudesFromCache(final File audioCache) {
        try {
            String cacheData = fileManager.readFile(audioCache);
            if (cacheData == null || cacheData.isEmpty()) {
                return null;
            }
            int durationStartIdx = cacheData.indexOf("=");
            int durationEndIdx = cacheData.indexOf(System.lineSeparator());
            String duration = cacheData.substring(0, durationEndIdx)
                    .substring(durationStartIdx + 1, durationEndIdx);
            String amplitudes = cacheData.substring(durationEndIdx + 1);
            AmplitudaResultJNI resultJNI = new AmplitudaResultJNI();
            resultJNI.setDuration(Double.parseDouble(duration));
            resultJNI.setAmplitudes(amplitudes);
            return resultJNI;
        } catch (Exception ignored) {
            return null;
        }
    }

    // NDK library initializer.
    static {
        System.loadLibrary("amplituda-native-lib");
    }

    native AmplitudaResultJNI amplitudesFromAudioJNI(
            String pathToAudio,
            int compressType,
            int framesPerSecond,
            String pathToCache,
            boolean cacheEnabled,
            AmplitudaProgressListener listener
    );

}