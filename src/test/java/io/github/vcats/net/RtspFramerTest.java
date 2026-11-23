package io.github.vcats.net;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RtspFramerTest {

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void reconstructsHeaderReceivedInFragments() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] first = bytes("RTSP/1.0 200 ");
        byte[] second = bytes("OK\r\nCSeq: 1\r\n\r\n");

        framer.feed(first, first.length, messages::add);

        assertTrue(messages.isEmpty());

        framer.feed(second, second.length, messages::add);

        assertEquals(1, messages.size());
        assertEquals(
                "RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n",
                messages.get(0)
        );

        framer.finish();
    }

    @Test
    void reconstructsFragmentedBinaryBody() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] header = bytes(
                "RTSP/1.0 200 OK\r\n"
                + "Content-Length: 5\r\n\r\n"
        );

        byte[] first = {
                0,
                1
        };

        byte[] second = {
                2,
                (byte) 0xFF,
                4
        };

        framer.feed(header, header.length, messages::add);
        framer.feed(first, first.length, messages::add);

        assertTrue(messages.isEmpty());

        framer.feed(second, second.length, messages::add);

        assertEquals(1, messages.size());

        byte[] received = messages.get(0)
                .getBytes(StandardCharsets.ISO_8859_1);

        assertArrayEquals(
                new byte[]{
                        'R', 'T', 'S', 'P', '/', '1', '.', '0',
                        ' ', '2', '0', '0', ' ', 'O', 'K',
                        '\r', '\n',
                        'C', 'o', 'n', 't', 'e', 'n', 't', '-',
                        'L', 'e', 'n', 'g', 't', 'h', ':', ' ',
                        '5', '\r', '\n', '\r', '\n',
                        0, 1, 2, (byte) 0xFF, 4
                },
                received
        );

        framer.finish();
    }

    @Test
    void separatesMultipleMessagesInOneRead() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\nCSeq: 1\r\n\r\n"
                + "RTSP/1.0 200 OK\r\nCSeq: 2\r\n\r\n"
        );

        framer.feed(input, input.length, messages::add);

        assertEquals(2, messages.size());
        assertTrue(messages.get(0).contains("CSeq: 1"));
        assertTrue(messages.get(1).contains("CSeq: 2"));

        framer.finish();
    }

    @Test
    void handlesContentLengthCaseInsensitively() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\n"
                + "cOnTeNt-LeNgTh: 3\r\n\r\n"
                + "abc"
        );

        framer.feed(input, input.length, messages::add);

        assertEquals(1, messages.size());
        assertTrue(messages.get(0).endsWith("abc"));

        framer.finish();
    }

    @Test
    void rejectsInvalidContentLength() {
        RtspFramer framer = new RtspFramer();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\n"
                + "Content-Length: invalid\r\n\r\n"
        );

        assertThrows(
                IOException.class,
                () -> framer.feed(input, input.length, ignored -> {})
        );
    }

    @Test
    void rejectsOversizedBody() {
        RtspFramer framer = new RtspFramer();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\n"
                + "Content-Length: 4194305\r\n\r\n"
        );

        assertThrows(
                IOException.class,
                () -> framer.feed(input, input.length, ignored -> {})
        );
    }

    @Test
    void detectsIncompleteMessageAtEndOfStream() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\n"
                + "Content-Length: 10\r\n\r\n"
                + "abc"
        );

        framer.feed(input, input.length, messages::add);

        assertTrue(messages.isEmpty());

        assertThrows(IOException.class, framer::finish);
    }

    @Test
    void acceptsMessageWithoutBody() throws Exception {
        RtspFramer framer = new RtspFramer();
        List<String> messages = new ArrayList<>();

        byte[] input = bytes(
                "RTSP/1.0 200 OK\r\nCSeq: 4\r\n\r\n"
        );

        framer.feed(input, input.length, messages::add);

        assertEquals(1, messages.size());
        framer.finish();
    }
}
