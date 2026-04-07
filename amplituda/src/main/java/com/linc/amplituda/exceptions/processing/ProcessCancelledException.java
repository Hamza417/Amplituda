package com.linc.amplituda.exceptions.processing;

import com.linc.amplituda.ErrorCode;
import com.linc.amplituda.exceptions.AmplitudaException;

/**
 * Exception thrown when an audio processing task is cancelled before it completes.
 * This can happen either because {@link com.linc.amplituda.Amplituda#cancel()} was called
 * explicitly, or because a new {@code processAudio} call was made while a previous one was
 * still running.
 *
 * @author Hamza417
 */
public final class ProcessCancelledException extends AmplitudaException {

    /**
     * Constructs a new {@code ProcessCancelledException} with a default message.
     */
    public ProcessCancelledException() {
        super("Audio processing was cancelled.", ErrorCode.AMPLITUDA_EXCEPTION);
    }
}

