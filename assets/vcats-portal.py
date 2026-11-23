#!/usr/bin/env python3
"""vCats Wayland screen capture helper.

Asks the desktop portal (org.freedesktop.portal.ScreenCast) for a screen,
which shows the system "share your screen" dialog, then writes raw I420
frames of the requested size and frame rate to stdout. vCats pipes this
into ffmpeg.

Usage: vcats-portal.py WIDTH HEIGHT FPS
Everything except the video goes to stderr.
"""
import os
import signal
import sys


def log(message):
    print("[vCats-portal] " + message, file=sys.stderr, flush=True)


try:
    import gi

    gi.require_version("Gst", "1.0")
    from gi.repository import Gio, GLib, Gst
except Exception as exc:  # pragma: no cover
    log("missing dependency: %s" % exc)
    sys.exit(3)

PORTAL = "org.freedesktop.portal.Desktop"
PORTAL_PATH = "/org/freedesktop/portal/desktop"
SCREENCAST = "org.freedesktop.portal.ScreenCast"
REQUEST = "org.freedesktop.portal.Request"

bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
sender = bus.get_unique_name()[1:].replace(".", "_")
proxy = Gio.DBusProxy.new_sync(
    bus, Gio.DBusProxyFlags.NONE, None, PORTAL, PORTAL_PATH, SCREENCAST, None
)
counter = 0


def call(method, build):
    """Calls a portal method and waits for its Request.Response signal."""
    global counter
    counter += 1
    token = "vcats%d_%d" % (os.getpid(), counter)
    path = "/org/freedesktop/portal/desktop/request/%s/%s" % (sender, token)
    loop = GLib.MainLoop()
    result = {}

    def on_response(_c, _s, _p, _i, _n, params, _u):
        result["code"], result["data"] = params.unpack()
        loop.quit()

    sub = bus.signal_subscribe(
        PORTAL, REQUEST, "Response", path, None,
        Gio.DBusSignalFlags.NONE, on_response, None,
    )
    try:
        proxy.call_sync(method, build(token), Gio.DBusCallFlags.NONE, -1, None)
        loop.run()
    finally:
        bus.signal_unsubscribe(sub)

    if result.get("code") != 0:
        raise RuntimeError(
            "%s was %s"
            % (method, "cancelled" if result.get("code") == 1 else "refused")
        )
    return result["data"]


def options(token, **extra):
    d = {"handle_token": GLib.Variant("s", token)}
    d.update(extra)
    return d


def main():
    width, height, fps = (int(v) for v in sys.argv[1:4])
    Gst.init(None)

    if Gst.ElementFactory.find("pipewiresrc") is None:
        log("GStreamer PipeWire plugin not found (gstreamer1.0-pipewire)")
        return 3

    session = call(
        "CreateSession",
        lambda t: GLib.Variant(
            "(a{sv})",
            (options(
                t,
                session_handle_token=GLib.Variant("s", "vcats%d" % os.getpid()),
            ),),
        ),
    )["session_handle"]

    cursor_modes = proxy.get_cached_property("AvailableCursorModes")
    cursor = 2 if cursor_modes is not None and cursor_modes.unpack() & 2 else 1

    call(
        "SelectSources",
        lambda t: GLib.Variant(
            "(oa{sv})",
            (session, options(
                t,
                types=GLib.Variant("u", 1),
                multiple=GLib.Variant("b", False),
                cursor_mode=GLib.Variant("u", cursor),
            )),
        ),
    )

    log("waiting for the screen selection dialog...")
    streams = call(
        "Start",
        lambda t: GLib.Variant("(osa{sv})", (session, "", options(t))),
    ).get("streams")
    if not streams:
        raise RuntimeError("no screen selected")
    node = streams[0][0]

    variant, fds = proxy.call_with_unix_fd_list_sync(
        "OpenPipeWireRemote",
        GLib.Variant("(oa{sv})", (session, {})),
        Gio.DBusCallFlags.NONE, -1, None, None,
    )
    fd = fds.get(variant.unpack()[0])

    pipeline = Gst.parse_launch(
        "pipewiresrc fd=%d path=%d always-copy=true do-timestamp=true"
        " ! queue max-size-buffers=4 leaky=downstream"
        " ! videoconvert ! videoscale add-borders=true"
        " ! video/x-raw,format=I420,width=%d,height=%d,pixel-aspect-ratio=1/1"
        " ! videorate"
        " ! video/x-raw,framerate=%d/1"
        " ! fdsink fd=1 sync=false" % (fd, node, width, height, fps)
    )

    loop = GLib.MainLoop()
    status = {"code": 0}

    def on_message(_bus, message):
        if message.type == Gst.MessageType.ERROR:
            err, _dbg = message.parse_error()
            log("pipeline error: %s" % err.message)
            status["code"] = 1
            loop.quit()
        elif message.type == Gst.MessageType.EOS:
            loop.quit()

    pipeline.get_bus().add_signal_watch()
    pipeline.get_bus().connect("message", on_message)

    for sig in (signal.SIGTERM, signal.SIGINT):
        GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, sig, loop.quit)

    pipeline.set_state(Gst.State.PLAYING)
    log("capturing %dx%d @ %d fps" % (width, height, fps))
    try:
        loop.run()
    finally:
        pipeline.set_state(Gst.State.NULL)
        try:
            bus.call_sync(
                PORTAL, session, "org.freedesktop.portal.Session", "Close",
                None, None, Gio.DBusCallFlags.NONE, 1000, None,
            )
        except Exception:
            pass
    return status["code"]


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        log("error: %s" % exc)
        sys.exit(2)
