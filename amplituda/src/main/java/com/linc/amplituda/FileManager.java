package com.linc.amplituda;

import android.content.Context;
import android.content.res.Resources;
import android.os.ParcelFileDescriptor;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;


final class FileManager {

    private final Resources resources;
    private final String cache;

    FileManager(final Context context) {
        resources = context.getResources();
        cache = context.getCacheDir().getPath() + File.separator;
    }

    /**
     * Delete local storage file
     */
    synchronized void deleteFile(final File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /**
     * Copy res/raw file to local storage
     *
     * @param resource - res/raw file id
     * @return raw file from local storage
     */
    synchronized File getRawFile(final int resource, final AmplitudaProgressListener listener) {
        File temp = new File(cache, String.valueOf(resource));
        try {
            InputStream inputStream = resources.openRawResource(resource);
            streamToFile(inputStream, temp, 1024 * 4, inputStream.available(), listener);
            return temp;
        } catch (Resources.NotFoundException | IOException ignored) {
            return null;
        }
    }

    /**
     * Copy audio from URL to local storage
     *
     * @param audioUrl - audio file url
     * @return audio file from local storage
     */
    synchronized File getUrlFile(final String audioUrl, final AmplitudaProgressListener listener) {
        File temp = new File(cache, String.valueOf(audioUrl.hashCode()));
        try {
            URL url = new URL(audioUrl);
            URLConnection connection = url.openConnection();
            connection.connect();
            streamToFile(
                    new BufferedInputStream(url.openStream()),
                    temp,
                    1024,
                    getUrlContentLength(connection),
                    listener
            );
        } catch (IOException e) {
            return null;
        }
        return temp;
    }

    /**
     * Copies the audio data from a {@link ParcelFileDescriptor} into a temporary file
     * so the native decoder can read it from a plain file path.
     * <p>
     * This is the bridge between SAF (Storage Access Framework) and the rest of the library.
     * SAF gives you a {@link ParcelFileDescriptor} - think of it as a handle to a file that
     * lives somewhere the app might not have a direct path to (like cloud storage or another
     * app's files). We just need to drain that handle into a temp file, and we're good to go.
     *
     * @param pfd      the file descriptor obtained from a SAF {@code ContentResolver} call
     * @param listener optional progress listener; may be {@code null}
     * @return a temporary {@link File} containing the audio data, or {@code null} on failure
     */
    synchronized File getFileDescriptorFile(
            final ParcelFileDescriptor pfd,
            final AmplitudaProgressListener listener
    ) {
        try {
            // Wrap the raw FD in an InputStream - AutoCloseInputStream closes the FD for us
            // when we're done reading, so no leaks here.
            ParcelFileDescriptor.AutoCloseInputStream stream =
                    new ParcelFileDescriptor.AutoCloseInputStream(pfd);
            return getInputStreamFile(stream, listener);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Copy audio from Uri to local storage
     *
     * @param audioStream - audio file stream
     * @return audio file from local storage
     */
    synchronized File getInputStreamFile(final InputStream audioStream, final AmplitudaProgressListener listener) {
        try {
            return getByteArrayFile(getByteArrayFromInputStream(audioStream), listener);
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * Copy audio from Uri to local storage
     *
     * @param audioByteArray - audio file stream
     * @return audio file from local storage
     */
    synchronized File getByteArrayFile(final byte[] audioByteArray, final AmplitudaProgressListener listener) {
        File temp = new File(cache, "temp_" + System.nanoTime());
        try (FileOutputStream outputStream = new FileOutputStream(temp)) {
            if (listener != null) listener.onProgressInternal(0);
            outputStream.write(audioByteArray);
            if (listener != null) listener.onProgressInternal(100);
            return temp;
        } catch (IOException ignored) {
            return null;
        }
    }

    /**
     * Copy audio from URL to local storage
     *
     * @param inputStream - audio file input stream
     * @param temp        - cache file to which the stream will be written
     * @param bufferSize  - copy operation buffer size
     */
    private synchronized void streamToFile(
            final InputStream inputStream,
            final File temp,
            final int bufferSize,
            final long contentLength,
            final AmplitudaProgressListener listener
    ) {
        try {
            OutputStream fos = new FileOutputStream(temp);
            byte[] buffer = new byte[bufferSize];
            int read;
            int bytesWritten = 0, progress = 0;

            while ((read = inputStream.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
                bytesWritten += read;

                if (listener != null && contentLength > 0) {
                    int current_progress = (int) (((float) bytesWritten / (float) contentLength) * 100.0);
                    if (current_progress != progress) {
                        listener.onProgressInternal(current_progress);
                        progress = current_progress;
                    }
                }
            }

            fos.flush();
            fos.close();
        } catch (IOException ignored) {
        } finally {
            try {
                inputStream.close();
            } catch (IOException | NullPointerException ignored) {
            }
        }
    }

    private synchronized long getUrlContentLength(URLConnection url) {
        try {
            final HttpURLConnection urlConnection = (HttpURLConnection) url;
            urlConnection.setRequestMethod("HEAD");
            final String lengthHeaderField = urlConnection.getHeaderField("content-length");
            Long result = lengthHeaderField == null ? null : Long.parseLong(lengthHeaderField);
            return result == null || result < 0L ? -1L : result;
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private synchronized byte[] getByteArrayFromInputStream(InputStream is) throws IOException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        byte[] buffer = new byte[0xFFFF];
        for (int len = is.read(buffer); len != -1; len = is.read(buffer)) {
            os.write(buffer, 0, len);
        }
        return os.toByteArray();
    }
}