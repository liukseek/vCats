
package io.github.vcats.media;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AuSplitterTest {

    private static final int MAX_ACCESS_UNIT_SIZE =
            32 * 1024 * 1024;

    @Test
    void shouldEmitLastAccessUnitOnFinish() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] data = {0, 0, 1, 9, 0x11, 0x22};

        splitter.feed(data, data.length);
        assertTrue(output.isEmpty());

        splitter.finish();

        assertEquals(1, output.size());
        assertArrayEquals(data, output.getFirst());
    }

    @Test
    void shouldNotEmitLastAccessUnitTwice() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] data = {0, 0, 1, 9, 0x11};

        splitter.feed(data, data.length);
        splitter.finish();
        splitter.finish();

        assertEquals(1, output.size());
    }

    @Test
    void shouldRejectFeedAfterFinish() {
        AuSplitter splitter = new AuSplitter(data -> {});

        splitter.finish();

        assertThrows(
                IllegalStateException.class,
                () -> splitter.feed(new byte[]{1}, 1)
        );
    }

    @Test
    void shouldRejectInvalidInputLength() {
        AuSplitter splitter = new AuSplitter(data -> {});

        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.feed(new byte[]{1, 2}, 3)
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> splitter.feed(new byte[]{1, 2}, -1)
        );
    }

    @Test
    void shouldIgnoreEmptyInput() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        splitter.feed(new byte[0], 0);
        splitter.finish();

        assertTrue(output.isEmpty());
    }

    @Test
    void shouldNotEmitDataWithoutAudOnFinish() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] data = {0x11, 0x22, 0x33};

        splitter.feed(data, data.length);
        splitter.finish();

        assertTrue(output.isEmpty());
    }

    @Test
    void shouldAcceptAccessUnitBelowMaximumSize() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] data = new byte[MAX_ACCESS_UNIT_SIZE - 1];
        data[0] = 0;
        data[1] = 0;
        data[2] = 1;
        data[3] = 9;

        splitter.feed(data, data.length);
        splitter.finish();

        assertEquals(1, output.size());
        assertEquals(data.length, output.getFirst().length);
    }

    @Test
    void shouldRejectAccessUnitAboveMaximumSize() {
        AuSplitter splitter = new AuSplitter(data -> {});

        byte[] first = new byte[MAX_ACCESS_UNIT_SIZE];
        first[0] = 0;
        first[1] = 0;
        first[2] = 1;
        first[3] = 9;

        splitter.feed(first, first.length);

        assertThrows(
                IllegalStateException.class,
                () -> splitter.feed(new byte[]{1}, 1)
        );
    }

    @Test
    void shouldSplitMultipleAccessUnitsInSameChunk() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] first = {0, 0, 1, 9, 0x11, 0x22};
        byte[] second = {0, 0, 1, 9, 0x33, 0x44};

        byte[] data = new byte[first.length + second.length];

        System.arraycopy(first, 0, data, 0, first.length);
        System.arraycopy(second, 0, data, first.length, second.length);

        splitter.feed(data, data.length);
        splitter.finish();

        assertEquals(2, output.size());
        assertArrayEquals(first, output.get(0));
        assertArrayEquals(second, output.get(1));
    }

    @Test
    void shouldRecognizeAudSplitAcrossChunks() {
        List<byte[]> output = new ArrayList<>();
        AuSplitter splitter = new AuSplitter(output::add);

        byte[] first = {0, 0, 1, 9, 0x11, 0x22};
        byte[] partialAud = {0, 0};
        byte[] remainingAud = {1, 9, 0x33, 0x44};

        splitter.feed(first, first.length);
        splitter.feed(partialAud, partialAud.length);

        assertTrue(output.isEmpty());

        splitter.feed(remainingAud, remainingAud.length);

        assertEquals(1, output.size());
        assertArrayEquals(first, output.getFirst());

        splitter.finish();

        assertEquals(2, output.size());
    }

    @Test
    void shouldRejectDataWithoutAudAboveFourMiB() {
        AuSplitter splitter = new AuSplitter(data -> {});

        byte[] data = new byte[(4 << 20) + 1];

        assertThrows(
                IllegalStateException.class,
                () -> splitter.feed(data, data.length)
        );
    }
}
