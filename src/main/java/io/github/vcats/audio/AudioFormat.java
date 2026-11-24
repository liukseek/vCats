package io.github.vcats.audio;

import io.github.vcats.protocol.Protocol;

/**
 * Audio formats vCats can send. The receiver's expected format is not
 * documented, so several are available to try with --audio-format.
 */
public enum AudioFormat {
    /**
     * Raw 24 kHz mono 16-bit little-endian, sent as plain TCP to the board's
     * audio port 25123, which plays it (the default).
     */
    PCM24("pcm24", 24_000, 1, false, Protocol.PORT_AUDIO_RAW),
    /** Raw 48 kHz stereo 16-bit little-endian, on the EShare audio channel. */
    PCM("pcm", 48_000, 2, false),
    /** Raw 44.1 kHz stereo 16-bit little-endian. */
    PCM44("pcm44", 44_100, 2, false),
    /** Raw 48 kHz mono 16-bit little-endian. */
    MONO("mono", 48_000, 1, false),
    /** AAC-LC, 48 kHz stereo, one ADTS frame per packet. */
    AAC("aac", 48_000, 2, true);

    public static final int FRAME_MS = 20;
    private static final int BYTES_PER_SAMPLE = 2;

    public final String id;
    public final int sampleRate;
    public final int channels;
    public final boolean adts;

    /** Raw TCP port on the board, or 0 to use the EShare audio channel. */
    public final int rawPort;

    AudioFormat(String id, int sampleRate, int channels, boolean adts) {
        this(id, sampleRate, channels, adts, 0);
    }

    AudioFormat(String id, int sampleRate, int channels, boolean adts, int rawPort) {
        this.id = id;
        this.sampleRate = sampleRate;
        this.channels = channels;
        this.adts = adts;
        this.rawPort = rawPort;
    }

    /** Bytes in one 20 ms packet of raw PCM. */
    public int frameBytes() {
        return sampleRate * FRAME_MS / 1000 * channels * BYTES_PER_SAMPLE;
    }

    public static AudioFormat parse(String value) {
        for (AudioFormat format : values()) {
            if (format.id.equalsIgnoreCase(value)) {
                return format;
            }
        }

        throw new IllegalArgumentException(
                "Unknown audio format: " + value
                        + ". Use pcm24, pcm, pcm44, mono or aac"
        );
    }
}
