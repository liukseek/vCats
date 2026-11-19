
package main.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptionsTest {

    @Test
    void shouldHaveCorrectDefaultValues() {
        Options options = new Options();

        assertNull(options.target);
        assertNotNull(options.name);
        assertEquals("manual", options.type);
        assertEquals("ffmpeg", options.ffmpeg);
        assertNull(options.input);
        assertEquals("6M", options.bitrate);
        assertEquals("auto", options.transport);
        assertEquals(1920, options.width);
        assertEquals(1080, options.height);
        assertEquals(30, options.fps);
        assertFalse(options.test);
    }

    @Test
    void shouldParseTarget() {
        Options options = Options.parse(
                new String[]{"192.168.1.100"}
        );

        assertEquals("192.168.1.100", options.target);
    }

    @Test
    void shouldParseAllOptions() {
        Options options = Options.parse(new String[]{
                "192.168.1.100",
                "--name", "Test Client",
                "--type", "manual",
                "--ffmpeg", "/usr/bin/ffmpeg",
                "--input", "test-input",
                "--bitrate", "6M",
                "--fps", "60",
                "--size", "1280x720",
                "--test",
                "--raw"
        });

        assertEquals("192.168.1.100", options.target);
        assertEquals("Test Client", options.name);
        assertEquals("manual", options.type);
        assertEquals("/usr/bin/ffmpeg", options.ffmpeg);
        assertEquals("test-input", options.input);
        assertEquals("6M", options.bitrate);
        assertEquals(60, options.fps);
        assertEquals(1280, options.width);
        assertEquals(720, options.height);
        assertTrue(options.test);
        assertEquals("raw", options.transport);
    }

    @Test
    void shouldParseWebSocketTransport() {
        Options options = Options.parse(new String[]{
                "192.168.1.100",
                "--ws"
        });

        assertEquals("ws", options.transport);
    }

    @Test
    void shouldRejectMissingTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Options.parse(new String[]{})
        );
    }

    @Test
    void shouldRejectMultipleTargets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> Options.parse(new String[]{
                        "192.168.1.100",
                        "192.168.1.101"
                })
        );
    }

     @Test
    void shouldRejectMissingOptionValue() {
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--name"}));
    }

    @Test
    void shouldRejectInvalidResolution() {
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--size", "1280"}));

        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--size", "abc"}));

        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--size", "0x720"}));
    }

    @Test
    void shouldAcceptUppercaseResolutionSeparator() {
        Options options = Options.parse(
            new String[]{"192.168.1.100", "--size", "1280X720"}
        );

        assertEquals(1280, options.width);
        assertEquals(720, options.height);
    }

    @Test
    void shouldRejectInvalidFps() {
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--fps", "0"}));

        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--fps", "61"}));
    }

    @Test
    void shouldRejectUnknownOption() {
        assertThrows(IllegalArgumentException.class,
                () -> Options.parse(new String[]{"192.168.1.100", "--unknown"}));
    }
}
