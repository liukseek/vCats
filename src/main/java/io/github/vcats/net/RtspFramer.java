package io.github.vcats.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

final class RtspFramer {

    private static final int MAX_HEADER_SIZE = 64 * 1024;
    private static final int MAX_BODY_SIZE = 4 * 1024 * 1024;

    private static final byte[] HEADER_END = {
            '\r', '\n', '\r', '\n'
    };

    private byte[] pending = new byte[0];

    void feed(
            byte[] data,
            int length,
            Consumer<String> onMessage
    ) throws IOException {

        if (length < 0 || length > data.length) {
            throw new IOException("Invalid RTSP input length");
        }

        if (length == 0) {
            return;
        }

        append(data, length);

        while (true) {
            int headerEnd = findHeaderEnd();

            if (headerEnd < 0) {
                if (pending.length > MAX_HEADER_SIZE) {
                    throw new IOException("RTSP header exceeds size limit");
                }
                return;
            }

            if (headerEnd + HEADER_END.length > MAX_HEADER_SIZE) {
                throw new IOException("RTSP header exceeds size limit");
            }

            int bodyLength = contentLength(headerEnd);

            long totalLength = (long) headerEnd
                    + HEADER_END.length
                    + bodyLength;

            if (totalLength > Integer.MAX_VALUE) {
                throw new IOException("RTSP message is too large");
            }

            if (pending.length < totalLength) {
                return;
            }

            int messageLength = (int) totalLength;

            byte[] message = new byte[messageLength];
            System.arraycopy(
                    pending,
                    0,
                    message,
                    0,
                    messageLength
            );

            consume(messageLength);

            // ISO-8859-1 conserva esattamente la corrispondenza byte-carattere.
            onMessage.accept(
                    new String(message, StandardCharsets.ISO_8859_1)
            );
        }
    }

    void finish() throws IOException {
        if (pending.length != 0) {
            throw new IOException(
                    "Connection closed with an incomplete RTSP message"
            );
        }
    }

    private void append(byte[] data, int length) throws IOException {
        long newLength = (long) pending.length + length;

        if (newLength > MAX_HEADER_SIZE + MAX_BODY_SIZE) {
            throw new IOException("RTSP buffer exceeds size limit");
        }

        byte[] combined = new byte[(int) newLength];

        System.arraycopy(
                pending,
                0,
                combined,
                0,
                pending.length
        );

        System.arraycopy(
                data,
                0,
                combined,
                pending.length,
                length
        );

        pending = combined;
    }

    private int findHeaderEnd() {
        for (int i = 0; i <= pending.length - HEADER_END.length; i++) {
            if (pending[i] == HEADER_END[0]
                    && pending[i + 1] == HEADER_END[1]
                    && pending[i + 2] == HEADER_END[2]
                    && pending[i + 3] == HEADER_END[3]) {
                return i;
            }
        }

        return -1;
    }

    private int contentLength(int headerEnd) throws IOException {
        String header = new String(
                pending,
                0,
                headerEnd,
                StandardCharsets.ISO_8859_1
        );

        String[] lines = header.split("\r\n");
        Integer found = null;

        for (int i = 1; i < lines.length; i++) {
            int colon = lines[i].indexOf(':');

            if (colon < 0) {
                continue;
            }

            String name = lines[i]
                    .substring(0, colon)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            if (!name.equals("content-length")) {
                continue;
            }

            String value = lines[i].substring(colon + 1).trim();

            final int parsed;

            try {
                parsed = Integer.parseInt(value);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid RTSP Content-Length", e);
            }

            if (parsed < 0 || parsed > MAX_BODY_SIZE) {
                throw new IOException("RTSP body exceeds size limit");
            }

            if (found != null && found != parsed) {
                throw new IOException("Conflicting RTSP Content-Length headers");
            }

            found = parsed;
        }

        return found == null ? 0 : found;
    }

    private void consume(int length) {
        int remaining = pending.length - length;

        byte[] next = new byte[remaining];

        if (remaining > 0) {
            System.arraycopy(
                    pending,
                    length,
                    next,
                    0,
                    remaining
            );
        }

        pending = next;
    }
}
