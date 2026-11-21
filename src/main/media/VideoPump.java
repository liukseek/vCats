package main.media;

import main.protocol.EShareClient;
import main.util.Log;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

public final class VideoPump implements Runnable {

    private final Process ffmpeg;
    private final EShareClient eshare;
    private final Runnable onEnd;

    private byte[] sps;
    private byte[] pps;
    private byte[] lastConfig;

    public VideoPump(
            Process ffmpeg,
            EShareClient eshare,
            Runnable onEnd
    ) {
        this.ffmpeg = ffmpeg;
        this.eshare = eshare;
        this.onEnd = onEnd;
    }

    @Override
    public void run() {
        AuSplitter splitter = new AuSplitter(this::sendAccessUnit);

        try (InputStream in = ffmpeg.getInputStream()) {
            byte[] chunk = new byte[64 * 1024];
            int n;

            while ((n = in.read(chunk)) != -1) {
                if (n == 0) {
                    continue;
                }

                splitter.feed(chunk, n);
            }

        } catch (Exception e) {
            Log.info("VideoPump: stream interrupted: " + e);

        } finally {
            try {
                splitter.finish();
            } catch (Exception e) {
                Log.info("VideoPump: finish error: " + e);
            }

            try {
                onEnd.run();
            } catch (RuntimeException e) {
                Log.info("VideoPump: end callback error: " + e);
            }
        }
    }

    private void sendAccessUnit(byte[] au) {
        List<int[]> nals = H264.nals(au);

        int firstVcl = -1;

        for (int[] nal : nals) {
            if (nal[1] >= nal[2]) {
                continue;
            }

            int type = au[nal[1]] & 0x1F;

            if (type == H264.NAL_SPS) {
                sps = Arrays.copyOfRange(
                        au,
                        nal[0],
                        nal[2]
                );

            } else if (type == H264.NAL_PPS) {
                pps = Arrays.copyOfRange(
                        au,
                        nal[0],
                        nal[2]
                );
            }

            if (type >= 1 && type <= 5 && firstVcl < 0) {
                firstVcl = nal[0];
            }
        }

        sendConfiguration();

        if (firstVcl < 0) {
            return;
        }

        if (lastConfig == null) {
            Log.info("VideoPump: waiting for H.264 SPS/PPS");
            return;
        }

        byte[] frame = Arrays.copyOfRange(
                au,
                firstVcl,
                au.length
        );

        eshare.sendVideo(frame, false);
    }

    private void sendConfiguration() {
        if (sps == null || pps == null) {
            return;
        }

        byte[] config = new byte[sps.length + pps.length];

        System.arraycopy(
                sps,
                0,
                config,
                0,
                sps.length
        );

        System.arraycopy(
                pps,
                0,
                config,
                sps.length,
                pps.length
        );

        if (Arrays.equals(config, lastConfig)) {
            return;
        }

        eshare.sendVideo(config, true);

        lastConfig = config;

        Log.info("VideoPump: H.264 configuration sent");
    }
}