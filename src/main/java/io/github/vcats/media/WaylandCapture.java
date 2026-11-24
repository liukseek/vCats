package io.github.vcats.media;

import io.github.vcats.util.Log;

import java.io.IOException;
import java.lang.foreign.AddressLayout;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.ServerSocket;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screen capture on Wayland, in pure Java through the system libraries
 * (java.lang.foreign, no extra dependency):
 *
 * <ol>
 *   <li>GDBus talks to xdg-desktop-portal (ScreenCast). The system shows its
 *       "share your screen" dialog, which is the privacy permission.</li>
 *   <li>The portal hands over a PipeWire connection (a file descriptor).</li>
 *   <li>A GStreamer pipeline reads it, scales to the requested size and frame
 *       rate and serves raw I420 frames on a local TCP port that ffmpeg reads.</li>
 * </ol>
 */
public final class WaylandCapture implements AutoCloseable {

    public static final String HINT =
            "Wayland screen capture needs GLib/GIO, GStreamer with the "
                    + "PipeWire plugin and xdg-desktop-portal. On "
                    + "Debian/Ubuntu: sudo apt install libglib2.0-0 "
                    + "libgstreamer1.0-0 gstreamer1.0-pipewire "
                    + "gstreamer1.0-plugins-base gstreamer1.0-plugins-good "
                    + "xdg-desktop-portal xdg-desktop-portal-gnome "
                    + "(or -kde / -wlr for your desktop)";

    private static final String PORTAL = "org.freedesktop.portal.Desktop";
    private static final String PORTAL_PATH = "/org/freedesktop/portal/desktop";
    private static final String SCREENCAST = "org.freedesktop.portal.ScreenCast";

    private static final int GST_STATE_NULL = 1;
    private static final int GST_STATE_PLAYING = 4;
    private static final int GST_STATE_CHANGE_FAILURE = 0;
    private static final long DIALOG_TIMEOUT_MS = 120_000;

    private static final Pattern RESPONSE_CODE = Pattern.compile("^\\((?:uint32 )?(\\d+),");
    private static final Pattern SESSION = Pattern.compile("'session_handle': <'([^']+)'>");
    private static final Pattern NODE = Pattern.compile("'streams': <\\[\\((?:uint32 )?(\\d+),");

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIB_ARENA = Arena.global();
    private static final ValueLayout.OfInt INT = ValueLayout.JAVA_INT;
    private static final AddressLayout PTR = ValueLayout.ADDRESS;

    private static SymbolLookup glib;
    private static SymbolLookup gio;
    private static SymbolLookup gobject;
    private static SymbolLookup gst;

    private final Arena arena = Arena.ofShared();
    private final int port;

    private MemorySegment connection = MemorySegment.NULL;
    private MemorySegment pipeline = MemorySegment.NULL;
    private String session;
    private String sender;
    private int counter;
    private volatile String response;
    private MemorySegment callbackStub;
    private boolean closed;

    private WaylandCapture(int port) {
        this.port = port;
    }

    /** Local TCP port that serves the raw frames. */
    public int port() {
        return port;
    }

    /** True when the native libraries are present; otherwise the reason. */
    public static String unavailableReason() {
        try {
            load();
            return null;

        } catch (Throwable e) {
            return HINT + " (" + e.getMessage() + ")";
        }
    }

    public static WaylandCapture start(int width, int height, int fps)
            throws IOException {

        try {
            load();
        } catch (Throwable e) {
            throw new IOException(HINT + " (" + e.getMessage() + ")", e);
        }

        int port;

        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }

        WaylandCapture capture = new WaylandCapture(port);

        try {
            capture.open(width, height, fps);
            return capture;

        } catch (IOException e) {
            capture.close();
            throw e;

        } catch (Throwable e) {
            capture.close();
            throw new IOException("Wayland capture failed: " + e, e);
        }
    }

    // ------------------------------------------------------------------ flow

    private void open(int width, int height, int fps) throws Throwable {
        connection = (MemorySegment) fn(gio, "g_bus_get_sync",
                FunctionDescriptor.of(PTR, INT, PTR, PTR))
                .invokeExact(2 /* G_BUS_TYPE_SESSION */, MemorySegment.NULL,
                        errorSlot());
        checkError("could not connect to the session bus");

        MemorySegment unique = (MemorySegment) fn(gio,
                "g_dbus_connection_get_unique_name",
                FunctionDescriptor.of(PTR, PTR)).invokeExact(connection);
        sender = string(unique).substring(1).replace('.', '_');

        callbackStub = LINKER.upcallStub(
                MethodHandles.lookup().findVirtual(
                        WaylandCapture.class,
                        "onSignal",
                        MethodType.methodType(void.class,
                                MemorySegment.class, MemorySegment.class,
                                MemorySegment.class, MemorySegment.class,
                                MemorySegment.class, MemorySegment.class,
                                MemorySegment.class)).bindTo(this),
                FunctionDescriptor.ofVoid(PTR, PTR, PTR, PTR, PTR, PTR, PTR),
                LIB_ARENA);

        Log.info("Wayland: asking the desktop portal for a screen to share");

        String created = request("CreateSession",
                "({'handle_token': <'%T'>, 'session_handle_token': <'vcats"
                        + ProcessHandle.current().pid() + "'>},)");
        session = match(SESSION, created, "the portal did not create a session");

        boolean selected = false;

        for (int cursor : new int[]{2, 1, 0}) {
            String extra = cursor == 0 ? "" : ", 'cursor_mode': <uint32 " + cursor + ">";

            try {
                request("SelectSources",
                        "(objectpath '" + session + "', {'handle_token': <'%T'>, "
                                + "'types': <uint32 1>, 'multiple': <false>" + extra + "})");
                selected = true;
                break;

            } catch (IOException e) {
                if (cursor == 0) {
                    throw e;
                }
            }
        }

        if (!selected) {
            throw new IOException("the portal refused the screen selection");
        }

        Log.info("Wayland: waiting for the screen selection dialog...");

        String started = request("Start",
                "(objectpath '" + session + "', '', {'handle_token': <'%T'>})");
        int node = Integer.parseInt(match(NODE, started,
                "no screen was selected"));

        int fd = openPipeWire();

        String description = "pipewiresrc fd=" + fd + " path=" + node
                + " always-copy=true do-timestamp=true"
                + " ! queue max-size-buffers=4 leaky=downstream"
                + " ! videoconvert ! videoscale add-borders=true"
                + " ! video/x-raw,format=I420,width=" + width
                + ",height=" + height + ",pixel-aspect-ratio=1/1"
                + " ! videorate ! video/x-raw,framerate=" + fps + "/1"
                + " ! tcpserversink host=127.0.0.1 port=" + port
                + " sync=false";

        fn(gst, "gst_init", FunctionDescriptor.ofVoid(PTR, PTR))
                .invokeExact(MemorySegment.NULL, MemorySegment.NULL);

        pipeline = (MemorySegment) fn(gst, "gst_parse_launch",
                FunctionDescriptor.of(PTR, PTR, PTR))
                .invokeExact(arena.allocateFrom(description), errorSlot());

        if (pipeline.equals(MemorySegment.NULL)) {
            throw new IOException("could not build the capture pipeline: "
                    + takeError() + ". " + HINT);
        }

        int state = (int) fn(gst, "gst_element_set_state",
                FunctionDescriptor.of(INT, PTR, INT))
                .invokeExact(pipeline, GST_STATE_PLAYING);

        if (state == GST_STATE_CHANGE_FAILURE) {
            throw new IOException("the capture pipeline could not start");
        }

        Log.info("Wayland: capturing " + width + "x" + height + " @ "
                + fps + " fps");
    }

    /** Sends a portal request and waits for its Response signal. */
    private String request(String method, String paramsTemplate)
            throws Throwable {

        String token = "vcats" + ProcessHandle.current().pid() + "_" + (++counter);
        String path = "/org/freedesktop/portal/desktop/request/" + sender
                + "/" + token;

        response = null;

        int subscription = (int) fn(gio, "g_dbus_connection_signal_subscribe",
                FunctionDescriptor.of(INT, PTR, PTR, PTR, PTR, PTR, PTR, INT,
                        PTR, PTR, PTR))
                .invokeExact(connection, MemorySegment.NULL,
                        arena.allocateFrom("org.freedesktop.portal.Request"),
                        arena.allocateFrom("Response"),
                        arena.allocateFrom(path), MemorySegment.NULL, 0,
                        callbackStub, MemorySegment.NULL, MemorySegment.NULL);

        try {
            MemorySegment params = parseVariant(paramsTemplate.replace("%T", token));

            MemorySegment reply = (MemorySegment) fn(gio,
                    "g_dbus_connection_call_sync",
                    FunctionDescriptor.of(PTR, PTR, PTR, PTR, PTR, PTR, PTR,
                            PTR, INT, INT, PTR, PTR))
                    .invokeExact(connection, arena.allocateFrom(PORTAL),
                            arena.allocateFrom(PORTAL_PATH),
                            arena.allocateFrom(SCREENCAST),
                            arena.allocateFrom(method), params,
                            MemorySegment.NULL, 0, -1, MemorySegment.NULL,
                            errorSlot());

            unref(params);

            if (reply.equals(MemorySegment.NULL)) {
                throw new IOException(method + " failed: " + takeError());
            }

            unref(reply);

            long deadline = System.currentTimeMillis() + DIALOG_TIMEOUT_MS;
            MethodHandle iterate = fn(glib, "g_main_context_iteration",
                    FunctionDescriptor.of(INT, PTR, INT));

            while (response == null) {
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException(method + " timed out");
                }

                int ignored = (int) iterate.invokeExact(MemorySegment.NULL, 0);

                if (response == null) {
                    TimeUnit.MILLISECONDS.sleep(10);
                }
            }

        } finally {
            fn(gio, "g_dbus_connection_signal_unsubscribe",
                    FunctionDescriptor.ofVoid(PTR, INT))
                    .invokeExact(connection, subscription);
        }

        String text = response;
        Matcher code = RESPONSE_CODE.matcher(text);
        String status = code.find() ? code.group(1) : "2";

        if (!status.equals("0")) {
            throw new IOException(method + " was "
                    + (status.equals("1") ? "cancelled" : "refused")
                    + " by the user or the system");
        }

        return text;
    }

    /** Called by GDBus (on our thread, from the main context iteration). */
    @SuppressWarnings("unused")
    private void onSignal(MemorySegment conn, MemorySegment senderName,
                          MemorySegment objectPath, MemorySegment iface,
                          MemorySegment signal, MemorySegment parameters,
                          MemorySegment userData) {
        try {
            MemorySegment printed = (MemorySegment) fn(glib, "g_variant_print",
                    FunctionDescriptor.of(PTR, PTR, INT))
                    .invokeExact(parameters, 0);

            response = string(printed);

            fn(glib, "g_free", FunctionDescriptor.ofVoid(PTR))
                    .invokeExact(printed);

        } catch (Throwable e) {
            response = "(2, {})";
        }
    }

    private int openPipeWire() throws Throwable {
        MemorySegment params = parseVariant(
                "(objectpath '" + session + "', @a{sv} {})");
        MemorySegment outList = arena.allocate(PTR);

        MemorySegment reply = (MemorySegment) fn(gio,
                "g_dbus_connection_call_with_unix_fd_list_sync",
                FunctionDescriptor.of(PTR, PTR, PTR, PTR, PTR, PTR, PTR, PTR,
                        INT, INT, PTR, PTR, PTR, PTR))
                .invokeExact(connection, arena.allocateFrom(PORTAL),
                        arena.allocateFrom(PORTAL_PATH),
                        arena.allocateFrom(SCREENCAST),
                        arena.allocateFrom("OpenPipeWireRemote"), params,
                        MemorySegment.NULL, 0, -1, MemorySegment.NULL,
                        outList, MemorySegment.NULL, errorSlot());

        unref(params);

        if (reply.equals(MemorySegment.NULL)) {
            throw new IOException("OpenPipeWireRemote failed: " + takeError());
        }

        unref(reply);

        MemorySegment list = outList.get(PTR, 0);

        int fd = (int) fn(gio, "g_unix_fd_list_get",
                FunctionDescriptor.of(INT, PTR, INT, PTR))
                .invokeExact(list, 0, errorSlot());

        if (fd < 0) {
            throw new IOException("no PipeWire connection: " + takeError());
        }

        return fd;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }

        closed = true;

        try {
            if (!pipeline.equals(MemorySegment.NULL)) {
                int ignored = (int) fn(gst, "gst_element_set_state",
                        FunctionDescriptor.of(INT, PTR, INT))
                        .invokeExact(pipeline, GST_STATE_NULL);

                fn(gst, "gst_object_unref", FunctionDescriptor.ofVoid(PTR))
                        .invokeExact(pipeline);
            }

            if (session != null && !connection.equals(MemorySegment.NULL)) {
                MemorySegment reply = (MemorySegment) fn(gio,
                        "g_dbus_connection_call_sync",
                        FunctionDescriptor.of(PTR, PTR, PTR, PTR, PTR, PTR,
                                PTR, PTR, INT, INT, PTR, PTR))
                        .invokeExact(connection, arena.allocateFrom(PORTAL),
                                arena.allocateFrom(session),
                                arena.allocateFrom("org.freedesktop.portal.Session"),
                                arena.allocateFrom("Close"), MemorySegment.NULL,
                                MemorySegment.NULL, 0, 2000, MemorySegment.NULL,
                                MemorySegment.NULL);

                if (!reply.equals(MemorySegment.NULL)) {
                    unref(reply);
                }
            }

            if (!connection.equals(MemorySegment.NULL)) {
                fn(gobject, "g_object_unref", FunctionDescriptor.ofVoid(PTR))
                        .invokeExact(connection);
            }

        } catch (Throwable e) {
            Log.info("Wayland: cleanup error: " + e);

        } finally {
            arena.close();
            Log.info("Wayland: capture stopped");
        }
    }

    // --------------------------------------------------------------- helpers

    private MemorySegment parseVariant(String text) throws Throwable {
        MemorySegment variant = (MemorySegment) fn(glib, "g_variant_parse",
                FunctionDescriptor.of(PTR, PTR, PTR, PTR, PTR, PTR))
                .invokeExact(MemorySegment.NULL, arena.allocateFrom(text),
                        MemorySegment.NULL, MemorySegment.NULL, errorSlot());

        if (variant.equals(MemorySegment.NULL)) {
            throw new IOException("internal error, bad D-Bus parameters: "
                    + takeError());
        }

        return variant;
    }

    private void unref(MemorySegment variant) throws Throwable {
        fn(glib, "g_variant_unref", FunctionDescriptor.ofVoid(PTR))
                .invokeExact(variant);
    }

    private MemorySegment errorSlot;

    private MemorySegment errorSlot() {
        if (errorSlot == null) {
            errorSlot = arena.allocate(PTR);
        }

        errorSlot.set(PTR, 0, MemorySegment.NULL);
        return errorSlot;
    }

    /** Reads and frees the GError left in the error slot, if any. */
    private String takeError() throws Throwable {
        MemorySegment error = errorSlot.get(PTR, 0);

        if (error.equals(MemorySegment.NULL)) {
            return "unknown error";
        }

        // struct GError { GQuark domain; gint code; gchar *message; }
        MemorySegment message = error.reinterpret(16).get(PTR, 8);
        String text = string(message);

        fn(glib, "g_error_free", FunctionDescriptor.ofVoid(PTR))
                .invokeExact(error);

        errorSlot.set(PTR, 0, MemorySegment.NULL);
        return text;
    }

    private void checkError(String what) throws Throwable {
        if (!errorSlot.get(PTR, 0).equals(MemorySegment.NULL)) {
            throw new IOException(what + ": " + takeError());
        }
    }

    private static String match(Pattern pattern, String text, String error)
            throws IOException {

        Matcher m = pattern.matcher(text);

        if (!m.find()) {
            throw new IOException(error);
        }

        return m.group(1);
    }

    private static String string(MemorySegment cString) {
        return cString.reinterpret(Long.MAX_VALUE).getString(0);
    }

    private static synchronized void load() {
        if (glib != null) {
            return;
        }

        SymbolLookup a = SymbolLookup.libraryLookup("libglib-2.0.so.0", LIB_ARENA);
        SymbolLookup b = SymbolLookup.libraryLookup("libgio-2.0.so.0", LIB_ARENA);
        SymbolLookup c = SymbolLookup.libraryLookup("libgobject-2.0.so.0", LIB_ARENA);
        SymbolLookup d = SymbolLookup.libraryLookup("libgstreamer-1.0.so.0", LIB_ARENA);

        gio = b;
        gobject = c;
        gst = d;
        glib = a;
    }

    private static MethodHandle fn(SymbolLookup lib, String name,
                                   FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(
                lib.find(name).orElseThrow(() ->
                        new IllegalStateException("missing function " + name)),
                descriptor);
    }
}
