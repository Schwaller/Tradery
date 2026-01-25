package com.tradery.forge.data;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.LongConsumer;

/**
 * InputStream wrapper that reports read progress.
 * Used for tracking download progress of large files.
 */
public class ProgressInputStream extends FilterInputStream {

    private final long contentLength;
    private final LongConsumer onProgress;
    private long bytesRead = 0;
    private long lastReportedBytes = 0;
    private static final long REPORT_INTERVAL = 32768; // Report every 32KB

    /**
     * Create a progress-tracking input stream.
     *
     * @param in            The underlying input stream
     * @param contentLength Total expected content length (-1 if unknown)
     * @param onProgress    Callback receiving bytes read so far
     */
    public ProgressInputStream(InputStream in, long contentLength, LongConsumer onProgress) {
        super(in);
        this.contentLength = contentLength;
        this.onProgress = onProgress;
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) {
            bytesRead++;
            maybeReportProgress();
        }
        return b;
    }

    @Override
    public int read(byte[] b) throws IOException {
        int n = super.read(b);
        if (n > 0) {
            bytesRead += n;
            maybeReportProgress();
        }
        return n;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) {
            bytesRead += n;
            maybeReportProgress();
        }
        return n;
    }

    private void maybeReportProgress() {
        if (onProgress != null && bytesRead - lastReportedBytes >= REPORT_INTERVAL) {
            lastReportedBytes = bytesRead;
            onProgress.accept(bytesRead);
        }
    }

    /**
     * Get total bytes read so far.
     */
    public long getBytesRead() {
        return bytesRead;
    }

    /**
     * Get progress as percentage (0-100), or -1 if content length unknown.
     */
    public int getProgressPercent() {
        if (contentLength <= 0) return -1;
        return (int) Math.min(100, (bytesRead * 100) / contentLength);
    }

    /**
     * Get content length, or -1 if unknown.
     */
    public long getContentLength() {
        return contentLength;
    }
}
