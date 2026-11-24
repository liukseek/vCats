
package io.github.vcats.media;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class H264Test {

    @Test
    void shouldReturnEmptyListForEmptyBuffer() {
        assertTrue(H264.nals(new byte[0]).isEmpty());
    }

    @Test
    void shouldReturnEmptyListWithoutStartCodes() {
        byte[] data = {0x11, 0x22, 0x33, 0x44};

        assertTrue(H264.nals(data).isEmpty());
    }

    @Test
    void shouldExtractSingleNal() {
        byte[] data = {0, 0, 1, 0x67, 0x11, 0x22};

        List<int[]> nals = H264.nals(data);

        assertEquals(1, nals.size());
        assertArrayEquals(new int[]{0, 3, 6}, nals.getFirst());
    }

    @Test
    void shouldExtractMultipleNals() {
        byte[] data = {
                0, 0, 1, 0x67,
                0, 0, 1, 0x68
        };

        List<int[]> nals = H264.nals(data);

        assertEquals(2, nals.size());
        assertArrayEquals(new int[]{0, 3, 4}, nals.get(0));
        assertArrayEquals(new int[]{4, 7, 8}, nals.get(1));
    }

    @Test
    void shouldRecognizeFourByteStartCode() {
        byte[] data = {0, 0, 0, 1, 0x67, 0x11};

        List<int[]> nals = H264.nals(data);

        assertEquals(1, nals.size());
        assertArrayEquals(new int[]{0, 4, 6}, nals.getFirst());
    }

    @Test
    void shouldRecognizeThreeAndFourByteStartCodesTogether() {
        byte[] data = {
                0, 0, 1, 0x67,
                0, 0, 0, 1, 0x68
        };

        List<int[]> nals = H264.nals(data);

        assertEquals(2, nals.size());
        assertArrayEquals(new int[]{0, 3, 4}, nals.get(0));
        assertArrayEquals(new int[]{4, 8, 9}, nals.get(1));
    }

    @Test
    void shouldIgnoreDataBeforeFirstStartCode() {
        byte[] data = {
                0x55, 0x66,
                0, 0, 1, 0x67
        };

        List<int[]> nals = H264.nals(data);

        assertEquals(1, nals.size());
        assertArrayEquals(new int[]{2, 5, 6}, nals.getFirst());
    }

    @Test
    void shouldIgnoreIncompleteStartCodeAtEnd() {
        byte[] data = {0x11, 0, 0, 1};

        assertTrue(H264.nals(data).isEmpty());
    }

    @Test
    void shouldIgnoreStartCodeWithoutNalHeader() {
        byte[] data = {0, 0, 1};

        assertTrue(H264.nals(data).isEmpty());
    }

    @Test
    void shouldRejectNullBuffer() {
        assertThrows(
                IllegalArgumentException.class,
                () -> H264.nals(null)
        );
    }
}
