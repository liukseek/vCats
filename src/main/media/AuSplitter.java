package main.media;

import java.util.Arrays;
import java.util.function.Consumer;

final class AuSplitter {
    private byte[] buf = new byte[1 << 20];
    private int len = 0, auStart = -1, scan = 0;
    private final Consumer<byte[]> out;

    AuSplitter(Consumer<byte[]> out) {
        this.out = out;
    }

    void feed(byte[] src, int n) {
        if (len + n > buf.length) buf = Arrays.copyOf(buf, Math.max(buf.length * 2, len + n));
        System.arraycopy(src, 0, buf, len, n);
        len += n;
        for (int i = scan; i + 3 < len; i++) {
            if (buf[i] == 0 && buf[i + 1] == 0 && buf[i + 2] == 1 && (buf[i + 3] & 0x1F) == H264.NAL_AUD) {
                int b = (i > 0 && buf[i - 1] == 0 && i - 1 >= Math.max(auStart, 0)) ? i - 1 : i;
                if (auStart >= 0 && b > auStart) out.accept(Arrays.copyOfRange(buf, auStart, b));
                auStart = b;
                i += 3;
            }
        }
        scan = Math.max(len - 3, 0);
        if (auStart > 0) {
            System.arraycopy(buf, auStart, buf, 0, len - auStart);
            len -= auStart;
            scan -= auStart;
            auStart = 0;
        } else if (auStart < 0 && len > (4 << 20)) {
            len = 0;
            scan = 0;
        }
    }
}
