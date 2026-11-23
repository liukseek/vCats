package io.github.vcats.net;

import java.net.http.HttpClient;
import java.util.function.Consumer;

@FunctionalInterface
public interface ChannelFactory {

    Channel open(
            HttpClient http,
            String host,
            int port,
            Consumer<String> onMessage,
            Runnable onClose
    ) throws Exception;
}
