package io.github.vcats.audio;

import io.github.vcats.util.Log;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

public final class AudioPump implements Runnable {
    private static final long STATS_INTERVAL_MS = 5_000;

    private final InputStream input;
    private final AudioFormat format;
    private final Consumer<byte[]> onData;
    private final Runnable onEnd;

    private long packets;
    private long bytes;
    private int peak;
    private long lastStats = System.currentTimeMillis();

    public AudioPump(
            InputStream input,
            AudioFormat format,
            Consumer<byte[]> onData,
            Runnable onEnd
    ) {
        this.input = format.adts ? new BufferedInputStream(input) : input;
        this.format = format;
        this.onData = onData;
        this.onEnd = onEnd;
    }

    @Override
    public void run() {
        try (InputStream in = input) {
            byte[] packet;

            while ((packet = next(in)) != null) {
                record(packet);
                onData.accept(packet);
            }

        } catch (Exception e) {
            Log.info("AudioPump: stream interrupted: " + e);

        } finally {
            try {
                onEnd.run();
            } catch (RuntimeException e) {
                Log.info("AudioPump: end callback error: " + e);
            }
        }
    }

    private byte[] next(InputStream in) throws IOException {
        if (format.adts) {
            return nextAdtsFrame(in);
        }

        byte[] frame = new byte[format.frameBytes()];
        int n = in.readNBytes(frame, 0, frame.length);

        return n < frame.length ? null : frame;
    }

    /** Reads one ADTS frame, skipping bytes until a frame header is found. */
    static byte[] nextAdtsFrame(InputStream in) throws IOException {
        int b;

        while ((b = in.read()) != -1) {
            if (b != 0xFF) {
                continue;
            }

            byte[] header = new byte[7];
            header[0] = (byte) 0xFF;

            in.mark(8);

            if (in.readNBytes(header, 1, 6) < 6) {
                return null;
            }

            if ((header[1] & 0xF0) != 0xF0) {
                in.reset();
                continue;
            }

            int length = ((header[3] & 0x03) << 11)
                    | ((header[4] & 0xFF) << 3)
                    | ((header[5] & 0xE0) >> 5);

            if (length < 7) {
                in.reset();
                continue;
            }

            byte[] frame = new byte[length];
            System.arraycopy(header, 0, frame, 0, 7);

            if (in.readNBytes(frame, 7, length - 7) < length - 7) {
                return null;
            }

            return frame;
        }

        return null;
    }

    private void record(byte[] packet) {
        packets++;
        bytes += packet.length;

        if (!format.adts) {
            for (int i = 0; i + 1 < packet.length; i += 2) {
                int sample = (short) ((packet[i] & 0xFF) | (packet[i + 1] << 8));
                peak = Math.max(peak, Math.abs(sample));
            }
        }

        long now = System.currentTimeMillis();

        if (now - lastStats >= STATS_INTERVAL_MS) {
            Log.info("audio: sent " + packets + " packets, " + bytes / 1024
                    + " KB in the last " + (now - lastStats) / 1000 + " s"
                    + (format.adts ? "" : ", peak level " + peak * 100 / 32768 + "%"));

            packets = 0;
            bytes = 0;
            peak = 0;
            lastStats = now;
        }
    }
}
