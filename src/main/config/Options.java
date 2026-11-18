package main.config;

import java.net.InetAddress;

public final class Options {
    public String target;
    public String name = hostName();
    public String type = "manual";
    public String ffmpeg = "ffmpeg";
    public String input;
    public String bitrate = "6M";
    public String transport = "auto";
    public int width = 1920, height = 1080, fps = 30;
    public boolean test;

    public Options() {
    }

    public static Options parse(String[] a) {
        Options o = new Options();
        for (int i = 0; i < a.length; i++) {
            switch (a[i]) {
                case "--name":
                    o.name = a[++i];
                    break;
                case "--type":
                    o.type = a[++i];
                    break;
                case "--ffmpeg":
                    o.ffmpeg = a[++i];
                    break;
                case "--input":
                    o.input = a[++i];
                    break;
                case "--bitrate":
                    o.bitrate = a[++i];
                    break;
                case "--fps":
                    o.fps = Integer.parseInt(a[++i]);
                    break;
                case "--size": {
                    String[] p = a[++i].split("x");
                    o.width = Integer.parseInt(p[0]);
                    o.height = Integer.parseInt(p[1]);
                    break;
                }
                case "--test":
                    o.test = true;
                    break;
                case "--raw":
                    o.transport = "raw";
                    break;
                case "--ws":
                    o.transport = "ws";
                    break;
                default:
                    if (o.target == null) o.target = a[i];
                    else throw new IllegalArgumentException(a[i]);
            }
        }
        if (o.target == null) throw new IllegalArgumentException("usage: java main.Main <PIN|IP> [options]");
        return o;
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "java-client";
        }
    }
}
