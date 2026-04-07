package com.linc.amplituda;

import com.linc.amplituda.callback.AmplitudaErrorListener;
import com.linc.amplituda.callback.AmplitudaSuccessListener;
import com.linc.amplituda.exceptions.processing.ProcessCancelledException;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * Represents an asynchronous audio processing task returned by
 * {@link Amplituda#processAudio(String, Compress, Cache)}.
 * <p>
 * Wraps the underlying {@link Future} and mirrors the same result-retrieval API
 * provided by {@link AmplitudaProcessingOutput} so that callers can use familiar
 * callback or blocking patterns. When the task has been cancelled, all {@code get}
 * variants silently suppress the result and, where an error listener is present,
 * deliver a {@link ProcessCancelledException}.
 *
 * @param <T> the type of the audio source that was processed
 * @author Hamza417
 */
public final class AmplitudaProcessingTask<T> {

    private final Future<AmplitudaProcessingOutput<T>> future;

    /**
     * Package-private constructor. Instances are created only by {@link Amplituda}.
     *
     * @param future the underlying future holding the processing output
     */
    AmplitudaProcessingTask(final Future<AmplitudaProcessingOutput<T>> future) {
        this.future = future;
    }

    /**
     * Blocks until the result is available, then delivers it via the provided callbacks.
     *
     * @param successListener callback invoked with the result on success
     * @param errorListener   callback invoked with the exception on failure or cancellation
     */
    public void get(
            final AmplitudaSuccessListener<T> successListener,
            final AmplitudaErrorListener errorListener
    ) {
        try {
            future.get().get(successListener, errorListener);
        } catch (CancellationException | InterruptedException ignored) {
            // Task was cancelled; no result to deliver.
        } catch (ExecutionException e) {
            if (errorListener != null) {
                errorListener.onError(new ProcessCancelledException());
            }
        }
    }

    /**
     * Blocks until the result is available, then delivers it via the success callback.
     * Errors are silently ignored.
     *
     * @param successListener callback invoked with the result on success
     */
    public void get(final AmplitudaSuccessListener<T> successListener) {
        get(successListener, null);
    }

    /**
     * Blocks until the result is available and returns it.
     *
     * @param errorListener callback invoked with the exception on failure or cancellation
     * @return the {@link AmplitudaResult}, or {@code null} if the task was cancelled
     */
    public AmplitudaResult<T> get(final AmplitudaErrorListener errorListener) {
        try {
            return future.get().get(errorListener);
        } catch (CancellationException | InterruptedException ignored) {
            return null;
        } catch (ExecutionException e) {
            if (errorListener != null) {
                errorListener.onError(new ProcessCancelledException());
            }
            return null;
        }
    }

    /**
     * Blocks until the result is available and returns it.
     * Errors are silently ignored.
     *
     * @return the {@link AmplitudaResult}, or {@code null} if the task was cancelled
     */
    public AmplitudaResult<T> get() {
        try {
            return future.get().get();
        } catch (CancellationException | InterruptedException | ExecutionException ignored) {
            return null;
        }
    }

    /**
     * Returns {@code true} if this task has been cancelled before it completed.
     *
     * @return {@code true} if the task is cancelled
     */
    public boolean isCancelled() {
        return future.isCancelled();
    }

    /**
     * Returns {@code true} if this task has completed, either normally, by cancellation,
     * or due to an exception.
     *
     * @return {@code true} if the task is done
     */
    public boolean isDone() {
        return future.isDone();
    }
}

