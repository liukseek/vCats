# VCats

A command-line screen-sharing client written in Java for ViewBoard / vCast (EShare-compatible) receivers. It finds the receiver from its PIN or IP address, asks it to accept a screen share, and streams the desktop to it as H.264 video.

The project is experimental. The protocol was reconstructed from the behaviour of the "vCastSender" Chrome extension (v3.2.1) and from observing a receiver on a local network. It is not affiliated with or endorsed by any vendor.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [Usage](#usage)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Platform notes](#platform-notes)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [License](#license)

## Features

- Resolves the receiver from a 6-character PIN or connects directly to an IP address.
- Control channel with device presentation, receiver properties and heartbeat.
- Two cast transports, chosen automatically or forced from the command line:
  - WebSocket, as used by the Chrome extension.
  - Plain TCP, as exposed by the native PC receiver.
- Screen capture and H.264 encoding through the system `ffmpeg` on Windows, Linux and macOS.
- Built-in test pattern source, so the connection can be verified without sharing the screen.
- Handles both a user-initiated share, which the receiver must accept, and a share the receiver invites.

## Requirements

- JDK 21 or later.
- `ffmpeg` available on the system `PATH` (or passed with `--ffmpeg`). The program checks for it at startup and prints the install command for the current operating system if it is missing:

| System  | Install command                |
| ------- | ------------------------------ |
| Windows | `winget install Gyan.FFmpeg`   |
| macOS   | `brew install ffmpeg`          |
| Linux   | `sudo apt install ffmpeg`      |

- The sender and the receiver must be on the same local network.

## Build

From the project root:

```bash
javac -d out -sourcepath src src/main/Main.java
```

The project can also be opened and built directly in IntelliJ IDEA. The entry point is `main.Main`.

## Usage

```bash
java -cp out main.Main <PIN|IP> [options]
```

Examples:

```bash
java -cp out main.Main ABC123
java -cp out main.Main 192.168.1.50 --size 1280x720 --fps 24
java -cp out main.Main 192.168.1.50 --test
```

### Options

| Option                  | Default            | Description                                                                 |
| ----------------------- | ------------------ | --------------------------------------------------------------------------- |
| `<PIN\|IP>`             | required           | Receiver PIN (6 characters) or IPv4 address                                 |
| `--name NAME`           | local host name    | Device name shown on the receiver                                           |
| `--type manual\|invited`| `manual`           | `manual`: the receiver must accept the share. `invited`: the receiver asked |
| `--size WxH`            | `1920x1080`        | Output resolution                                                           |
| `--fps N`               | `30`               | Frame rate                                                                  |
| `--bitrate RATE`        | `3M`               | Video bitrate, in ffmpeg notation                                           |
| `--ffmpeg PATH`         | `ffmpeg`           | ffmpeg executable                                                           |
| `--input "ARGS"`        | none               | Custom ffmpeg input arguments, replacing the default screen capture         |
| `--test`                | off                | Send a generated test pattern instead of the screen                         |
| `--raw`                 | automatic          | Force the plain TCP transport                                               |
| `--ws`                  | automatic          | Force the WebSocket transport                                               |

Press `Ctrl+C` to stop. The program sends a teardown to the receiver and stops ffmpeg before exiting.

## How it works

1. **Discovery.** A PIN encodes the last three octets of the receiver's address. The first octet is guessed from the local network interfaces, and each candidate is probed on the control port. An IP address is used as given.
2. **Control channel.** A WebSocket on port 57407 carries line-based text messages: the client introduces itself, requests the receiver properties, and sends a heartbeat every 3 seconds. The receiver reports events such as `startMirror`, `stopMirror` and `disconnect`.
3. **Transport selection.** If the WebSocket RTSP port is closed and the native TCP port is open, the plain TCP transport is used. Otherwise the WebSocket transport is used.
4. **Cast channel.** An RTSP-like exchange (`OPTIONS` every second as keep-alive, `SETUP` to start, `TEARDOWN` to end) negotiates the session. With the WebSocket transport the receiver answers with an explicit allowed or refused header. With the native TCP transport the reply to `SETUP` carries the data port and counts as acceptance.
5. **Video.** ffmpeg captures the screen and encodes H.264 with access unit delimiters. The program splits the stream into access units, sends the SPS/PPS configuration first, then each frame preceded by a 128-byte header carrying the payload length, type and timestamp.

### Ports

| Purpose        | WebSocket transport | Plain TCP transport |
| -------------- | ------------------- | ------------------- |
| Control        | 57407               | 57407               |
| RTSP           | 51042               | 51040               |
| Video          | 51032               | 51030               |
| Audio (opened) | 51033               | 51020               |

The control channel always uses WebSocket.

## Project structure

```text
src/main/
├── Main.java                  Entry point
├── config/
│   └── Options.java           Command-line options
├── util/
│   └── Log.java               Console logging
├── net/
│   ├── Channel.java           Bidirectional channel to the receiver
│   ├── WebSocketChannel.java  WebSocket implementation
│   ├── TcpChannel.java        Plain TCP implementation
│   └── Transport.java         Transport kinds, ports and automatic selection
├── protocol/
│   ├── Protocol.java          Protocol constants and stream descriptor
│   ├── PinResolver.java       PIN or IP to a reachable receiver address
│   ├── ControlClient.java     Control channel and heartbeat
│   └── EShareClient.java      RTSP-like cast channel and video framing
├── media/
│   ├── Ffmpeg.java            ffmpeg check and command line per operating system
│   ├── VideoPump.java         Reads ffmpeg output and sends frames to the receiver
│   ├── AuSplitter.java        Splits the H.264 stream into access units
│   └── H264.java              NAL unit parsing
└── session/
    └── CastSession.java       Ties control, cast and ffmpeg together
```

## Platform notes

| System  | Capture backend | Notes                                                                                           |
| ------- | --------------- | ----------------------------------------------------------------------------------------------- |
| Windows | `gdigrab`       | Restart the IDE or terminal after installing ffmpeg so the updated `PATH` is picked up          |
| macOS   | `avfoundation`  | The terminal or IDE needs the Screen Recording permission. The screen device index is detected |
| Linux   | `x11grab`       | Works on X11 and XWayland only. On a Wayland session use `--input` or switch to an X11 session |

## Troubleshooting

**`Connection refused` when the cast starts.** The control port answered but the cast ports did not. The receiver may use the other transport, so try `--raw` or `--ws`. If neither works, check that screen sharing is enabled on the receiver.

**`ffmpeg is not executable`.** Install ffmpeg or pass its location with `--ffmpeg`. On Windows, restart the IDE after installing so it sees the new `PATH`.

**The share is never accepted.** With `--type manual` the receiver has to accept the request, and the program waits up to 60 seconds for the answer.

**Black or empty image on Linux.** The session is probably Wayland. Use `--input` with a Wayland-capable capture command, or log in to an X11 session.

**Verifying the connection.** Run with `--test`. The receiver should display a generated test pattern, which separates connection problems from screen-capture problems.

## Limitations

- Only the sender side is implemented. There is no receiver.
- Audio is not transmitted. The audio channel is opened but unused.
- The control channel uses plain `ws://`. There is no encryption or authentication beyond the receiver accepting the share, so use it only on networks you trust and with receivers you are authorised to use.
- Tested on Windows against a native PC receiver. The macOS and Linux code paths have not been run.
- The protocol is reconstructed, not documented. Other receiver versions may behave differently.

## License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for details.
