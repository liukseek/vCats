package main.util;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AppPaths {
    private static final int MAX_PARENTS = 5;

    private AppPaths() {
    }

    public static List<Path> roots() {
        List<Path> roots = new ArrayList<>();
        roots.add(Path.of("").toAbsolutePath());

        try {
            URL location = AppPaths.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation();

            Path source = Path.of(location.toURI());
            Path base = Files.isDirectory(source) ? source : source.getParent();

            for (int i = 0; i < MAX_PARENTS && base != null; i++) {
                if (!roots.contains(base)) {
                    roots.add(base);
                }

                base = base.getParent();
            }
        } catch (Exception ignored) {
        }

        return roots;
    }

    public static Path find(String relativePath) {
        for (Path root : roots()) {
            Path candidate = root.resolve(relativePath);

            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        return null;
    }
}
