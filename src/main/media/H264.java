
package main.media;

import java.util.ArrayList;
import java.util.List;

final class H264 {
    static final int NAL_SPS = 7;
    static final int NAL_PPS = 8;
    static final int NAL_AUD = 9;

    private H264() {
    }

    static List<int[]> nals(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException(
                    "NAL buffer cannot be null"
            );
        }

        List<int[]> result = new ArrayList<>();

        for (int i = 0; i + 3 < data.length; i++) {
            if (data[i] == 0
                    && data[i + 1] == 0
                    && data[i + 2] == 1) {

                int start = (
                        i > 0 && data[i - 1] == 0
                ) ? i - 1 : i;

                if (!result.isEmpty()) {
                    result.getLast()[2] = start;
                }

                result.add(new int[]{
                        start,
                        i + 3,
                        data.length
                });

                i += 2;
            }
        }

        return result;
    }
}
