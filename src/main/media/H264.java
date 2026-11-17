package main.media;

import java.util.ArrayList;
import java.util.List;

final class H264 {
    static final int NAL_SPS = 7, NAL_PPS = 8, NAL_AUD = 9;

    private H264() {
    }

    static List<int[]> nals(byte[] b) {
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i + 3 < b.length; i++) {
            if (b[i] == 0 && b[i + 1] == 0 && b[i + 2] == 1) {
                int sc = (i > 0 && b[i - 1] == 0) ? i - 1 : i;
                if (!out.isEmpty()) out.getLast()[2] = sc;
                out.add(new int[]{sc, i + 3, b.length});
                i += 2;
            }
        }
        return out;
    }
}
