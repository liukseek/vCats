package io.github.vcats.net;

public interface Channel {
    void sendText(String s);

    void sendBinary(byte[] b);

    void close();
}
