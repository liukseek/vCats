package io.github.vcats.util;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.List;

/**
 * macOS Screen Recording permission (System Settings, Privacy & Security).
 * Without it avfoundation silently captures only the wallpaper or nothing.
 */
public final class MacPermissions {

    private static final String CORE_GRAPHICS =
            "/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics";

    private static final String SETTINGS_URL =
            "x-apple.systempreferences:com.apple.preference.security"
                    + "?Privacy_ScreenCapture";

    private MacPermissions() {
    }

    public static void ensureScreenCapture() throws IOException {
        if (call("CGPreflightScreenCaptureAccess")) {
            return;
        }

        // Shows the system prompt the first time and registers the app
        // (the terminal or IDE that started Java) in the permission list.
        boolean granted = call("CGRequestScreenCaptureAccess");

        if (granted) {
            return;
        }

        try {
            new ProcessBuilder(List.of("open", SETTINGS_URL)).start();
        } catch (IOException ignored) {
        }

        throw new IOException(
                "Screen Recording permission is missing. Enable it for the "
                        + "app that runs vCats (Terminal, your IDE or Java) in "
                        + "System Settings > Privacy & Security > Screen & "
                        + "System Audio Recording, then quit and reopen "
                        + "that app and start vCats again"
        );
    }

    /** Returns true when the check is unavailable, so it never blocks. */
    private static boolean call(String function) {
        try {
            Linker linker = Linker.nativeLinker();
            SymbolLookup lookup =
                    SymbolLookup.libraryLookup(CORE_GRAPHICS, Arena.global());

            MethodHandle handle = linker.downcallHandle(
                    lookup.find(function).orElseThrow(),
                    FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN)
            );

            return (boolean) handle.invokeExact();

        } catch (Throwable e) {
            Log.info("could not check Screen Recording permission: " + e);
            return true;
        }
    }
}
