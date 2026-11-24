package io.github.vcats.audio;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AudioPumpTest {

    private static byte[] adts(int payload) {
        int length = 7 + payload;
        byte[] frame = new byte[length];
        frame[0] = (byte) 0xFF;
        frame[1] = (byte) 0xF1;
        frame[2] = 0x4C;
        frame[3] = (byte) (0x80 | ((length >> 11) & 0x03));
        frame[4] = (byte) ((length >> 3) & 0xFF);
        frame[5] = (byte) (((length & 0x07) << 5) | 0x1F);
        frame[6] = (byte) 0xFC;
        for (int i = 7; i < length; i++) {
            frame[i] = (byte) i;
        }
        return frame;
    }

    @Test
    void splitsPcmIntoTwentyMillisecondPackets() {
        int size = AudioFormat.PCM.frameBytes();
        List<byte[]> out = new ArrayList<>();

        new AudioPump(
                new ByteArrayInputStream(new byte[size * 3 + 10]),
                AudioFormat.PCM,
                out::add,
                () -> {}
        ).run();

        assertEquals(3, out.size());
        assertEquals(3840, out.get(0).length);
    }

    @Test
    void frameSizesFollowTheFormat() {
        assertEquals(3840, AudioFormat.PCM.frameBytes());
        assertEquals(3528, AudioFormat.PCM44.frameBytes());
        assertEquals(1920, AudioFormat.MONO.frameBytes());
    }

    @Test
    void readsAdtsFramesAndSkipsGarbage() throws IOException {
        byte[] first = adts(30);
        byte[] second = adts(100);

        byte[] stream = new byte[3 + first.length + second.length];
        stream[0] = 1;
        stream[1] = (byte) 0xFF;
        stream[2] = 5;
        System.arraycopy(first, 0, stream, 3, first.length);
        System.arraycopy(second, 0, stream, 3 + first.length, second.length);

        ByteArrayInputStream in = new ByteArrayInputStream(stream);

        assertArrayEquals(first, AudioPump.nextAdtsFrame(in));
        assertArrayEquals(second, AudioPump.nextAdtsFrame(in));
        assertNull(AudioPump.nextAdtsFrame(in));
    }

    @Test
    void parsesAndRejectsFormats() {
        assertEquals(AudioFormat.AAC, AudioFormat.parse("AAC"));
        assertThrows(IllegalArgumentException.class, () -> AudioFormat.parse("mp3"));
    }
}
