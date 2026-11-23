package io.github.vcats.audio;

import io.github.vcats.util.Log;

import java.io.InputStream;
import java.util.function.Consumer;

public final class AudioPump implements Runnable {

    private final InputStream input;
    private final Consumer<byte[]> onData;
    private final Runnable onEnd;

    public AudioPump(
            InputStream input,
            Consumer<byte[]> onData,
            Runnable onEnd
    ) {
        this.input = input;
        this.onData = onData;
        this.onEnd = onEnd;
    }

    @Override
    public void run() {
        byte[] frame = new byte[AudioCapture.FRAME_BYTES];

        try (InputStream in = input) {
            while (true) {
                int n = in.readNBytes(frame, 0, frame.length);

                if (n < frame.length) {
                    break;
                }

                onData.accept(frame.clone());
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
}
