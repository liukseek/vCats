package main.audio;

import main.util.Log;

import java.io.InputStream;
import java.util.Arrays;
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
        byte[] buffer = new byte[16 * 1024];

        try (InputStream in = input) {
            int n;

            while ((n = in.read(buffer)) != -1) {
                if (n == 0) {
                    continue;
                }

                onData.accept(Arrays.copyOf(buffer, n));
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
