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
    private boolean configSent;

    public VideoPump(Process ffmpeg, EShareClient eshare, Runnable onEnd) {
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
            while ((n = in.read(chunk)) > 0) splitter.feed(chunk, n);
        } catch (Exception e) {
            Log.info("pump interrupted: " + e);
        }
        onEnd.run();
    }

    private void sendAccessUnit(byte[] au) {
        List<int[]> ns = H264.nals(au);
        byte[] sps = null, pps = null;
        int firstVcl = -1;
        for (int[] n : ns) {
            int t = au[n[1]] & 0x1F;
            if (t == H264.NAL_SPS) sps = Arrays.copyOfRange(au, n[0], n[2]);
            else if (t == H264.NAL_PPS) pps = Arrays.copyOfRange(au, n[0], n[2]);
            if (t != H264.NAL_AUD && firstVcl < 0) firstVcl = n[0];
        }
        if (firstVcl < 0) return;
        if (!configSent) {
            if (sps == null || pps == null) return;
            byte[] cfg = Arrays.copyOf(sps, sps.length + pps.length);
            System.arraycopy(pps, 0, cfg, sps.length, pps.length);
            eshare.sendVideo(cfg, true);
            configSent = true;
        }
        eshare.sendVideo(Arrays.copyOfRange(au, firstVcl, au.length), false);
    }
}
