
package io.github.vcats.media;

import io.github.vcats.config.Options;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FfmpegTest {

    private List<String> inputArguments(String input) {
        Options options = new Options();
        options.input = input;

        List<String> command = Ffmpeg.command(options);

        int start = 4;
        int end = command.indexOf("-vf");

        return command.subList(start, end);
    }

    @Test
    void shouldParseSimpleArguments() {
        assertEquals(
                List.of("-f", "x11grab", "-i", ":0.0"),
                inputArguments("-f x11grab -i :0.0")
        );
    }

    @Test
    void shouldParseDoubleQuotedPaths() {
        assertEquals(
                List.of("-i", "My Screen/input.mp4"),
                inputArguments("-i \"My Screen/input.mp4\"")
        );
    }

    @Test
    void shouldParseSingleQuotedArguments() {
        assertEquals(
                List.of("-i", "0:none"),
                inputArguments("-i '0:none'")
        );
    }

    @Test
    void shouldParseMixedQuotes() {
        assertEquals(
                List.of("-i", "My Screen/input file.mp4"),
                inputArguments("-i 'My Screen/input file.mp4'")
        );

        assertEquals(
                List.of("-i", "My Screen/input file.mp4"),
                inputArguments("-i \"My Screen/input file.mp4\"")
        );
    }

    @Test
    void shouldPreserveEscapedSpaces() {
        assertEquals(
                List.of("-i", "My Screen/input.mp4"),
                inputArguments("-i My\\ Screen/input.mp4")
        );
    }

    @Test
    void shouldPreserveEmptyQuotedArgument() {
        assertEquals(
                List.of("-i", ""),
                inputArguments("-i \"\"")
        );
    }

    @Test
    void shouldRejectUnclosedQuotes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inputArguments("-i \"input.mp4")
        );
    }

    @Test
    void shouldRejectEmptyInput() {
        assertThrows(
                IllegalArgumentException.class,
                () -> inputArguments("")
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> inputArguments("   ")
        );
    }
}
