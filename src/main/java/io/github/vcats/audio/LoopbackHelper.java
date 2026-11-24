package io.github.vcats.audio;

import io.github.vcats.util.Log;
import io.github.vcats.util.Processes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Windows system audio through WASAPI loopback, which needs no Stereo Mix or
 * virtual cable. The small C# helper is compiled once with the csc.exe that
 * ships with Windows (.NET Framework 4) and cached in the temp folder.
 */
final class LoopbackHelper {
    private static final String RESOURCE = "/assets/vcats-loopback.cs";
    private static final String VERSION = "1";

    record Format(int rate, int channels, int bits, boolean isFloat) {
        String ffmpegFormat() {
            if (isFloat) {
                return "f32le";
            }

            return switch (bits) {
                case 24 -> "s24le";
                case 32 -> "s32le";
                default -> "s16le";
            };
        }
    }

    private static Path exe;

    private LoopbackHelper() {
    }

    static synchronized Path executable() throws IOException {
        if (exe != null && Files.isRegularFile(exe)) {
            return exe;
        }

        Path dir = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("vcats-" + VERSION);
        Files.createDirectories(dir);

        Path target = dir.resolve("vcats-loopback.exe");

        if (!Files.isRegularFile(target)) {
            Path csc = findCompiler();
            Path source = dir.resolve("vcats-loopback.cs");

            try (InputStream in =
                         LoopbackHelper.class.getResourceAsStream(RESOURCE)) {
                if (in == null) {
                    throw new IOException("loopback helper source not found");
                }

                Files.copy(in, source, StandardCopyOption.REPLACE_EXISTING);
            }

            Path partial = dir.resolve("vcats-loopback.tmp.exe");

            try {
                Processes.Result result = Processes.run(
                        List.of(
                                csc.toString(),
                                "/nologo",
                                "/optimize",
                                "/out:" + partial,
                                source.toString()
                        ),
                        60
                );

                if (result.exitCode() != 0) {
                    throw new IOException(
                            "could not build the audio helper: "
                                    + result.output().trim()
                    );
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted while building the helper");
            }

            Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
            Log.info("audio helper built: " + target);
        }

        exe = target;
        return target;
    }

    static Format query(Path helper) throws IOException {
        try {
            Processes.Result result = Processes.run(
                    List.of(helper.toString(), "--info"),
                    10
            );

            String[] p = result.output().trim().split("\\s+");

            if (result.exitCode() != 0 || p.length != 4) {
                throw new IOException(
                        "no audio output device available ("
                                + result.output().trim() + ")"
                );
            }

            return new Format(
                    Integer.parseInt(p[0]),
                    Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]),
                    p[3].equals("1")
            );

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted");

        } catch (NumberFormatException e) {
            throw new IOException("unexpected audio helper output", e);
        }
    }

    private static Path findCompiler() throws IOException {
        String windir = System.getenv("WINDIR");

        if (windir != null) {
            for (String framework : List.of("Framework64", "Framework")) {
                Path csc = Path.of(
                        windir,
                        "Microsoft.NET",
                        framework,
                        "v4.0.30319",
                        "csc.exe"
                );

                if (Files.isRegularFile(csc)) {
                    return csc;
                }
            }
        }

        throw new IOException("the .NET Framework compiler (csc.exe) was not found");
    }
}
