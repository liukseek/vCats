
package main.media;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

final class AuSplitter {

    private static final int MAX_ACCESS_UNIT_SIZE = 32 * 1024 * 1024;
    private static final int MAX_INPUT_CHUNK = 64 * 1024;
    private static final int MAX_BUFFER_SIZE =
            MAX_ACCESS_UNIT_SIZE + MAX_INPUT_CHUNK;

    private static final int MAX_UNSYNCED_SIZE = 4 * 1024 * 1024;

    private byte[] buf = new byte[1 << 20];

    private int len;
    private int auStart = -1;
    private int scan;

    private final Consumer<byte[]> out;
    private boolean finished;

    AuSplitter(Consumer<byte[]> out) {
        this.out = Objects.requireNonNull(out);
    }

    void feed(byte[] src, int n) {
        if (finished) {
            throw new IllegalStateException("Splitter already finished");
        }

        if (src == null) {
            throw new IllegalArgumentException("Input buffer cannot be null");
        }

        if (n < 0 || n > src.length) {
            throw new IllegalArgumentException("Invalid input length: " + n);
        }

        if (n == 0) return;

        int offset = 0;

        while (offset < n) {
            int count = Math.min(MAX_INPUT_CHUNK, n - offset);

            append(src, offset, count);
            processBuffer();

            offset += count;
        }
    }

    private void append(byte[] src, int offset, int count) {
        int required = len + count;

        if (required > MAX_BUFFER_SIZE) {
            fail("Input buffer exceeds maximum capacity");
        }

        if (required > buf.length) {
            int doubled = buf.length * 2;
            int newCapacity = Math.min(
                    MAX_BUFFER_SIZE,
                    Math.max(required, doubled)
            );

            buf = Arrays.copyOf(buf, newCapacity);
        }

        System.arraycopy(src, offset, buf, len, count);
        len += count;
    }

    private void processBuffer() {
        for (int i = scan; i + 3 < len; i++) {
            if (buf[i] == 0
                    && buf[i + 1] == 0
                    && buf[i + 2] == 1
                    && (buf[i + 3] & 0x1F) == H264.NAL_AUD) {

                int boundary = (
                        i > 0
                        && buf[i - 1] == 0
                        && i - 1 >= Math.max(auStart, 0)
                ) ? i - 1 : i;

                if (auStart >= 0 && boundary > auStart) {
                    checkAccessUnitSize(boundary - auStart);

                    out.accept(
                            Arrays.copyOfRange(buf, auStart, boundary)
                    );
                }

                auStart = boundary;
                i += 3;
            }
        }

        scan = Math.max(len - 3, 0);

        if (auStart >= 0) {
            checkAccessUnitSize(len - auStart);

            if (auStart == 0 && len > MAX_ACCESS_UNIT_SIZE) {
                fail("Access unit exceeds maximum size");
            }

        } else if (len > MAX_UNSYNCED_SIZE) {
            fail("No AUD found within maximum synchronization buffer");
        }

        if (auStart > 0) {
            System.arraycopy(
                    buf,
                    auStart,
                    buf,
                    0,
                    len - auStart
            );

            len -= auStart;
            scan -= auStart;
            auStart = 0;
        }
    }

    private void checkAccessUnitSize(int size) {
        if (size > MAX_ACCESS_UNIT_SIZE) {
            fail("Access unit exceeds maximum size of "
                    + (MAX_ACCESS_UNIT_SIZE / (1024 * 1024))
                    + " MiB");
        }
    }

    private void fail(String message) {
        finished = true;
        len = 0;
        scan = 0;
        auStart = -1;

        throw new IllegalStateException(message);
    }

    void finish() {
        if (finished) return;

        finished = true;

        if (auStart >= 0 && len > auStart) {
            out.accept(
                    Arrays.copyOfRange(buf, auStart, len)
            );
        }

        len = 0;
        scan = 0;
        auStart = -1;
    }
}
