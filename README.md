
<p align="center">
  <img src="assets/logo-readme.png" alt="vCats" width="260">
</p>

<p align="center">
  <strong>Screen sharing for ViewBoard / vCast receivers</strong>
</p>

---

vCats is a command-line screen-sharing client written in Java for ViewBoard / vCast (EShare-compatible) receivers. It discovers receivers using a PIN or IP address, requests permission to share the screen and streams the desktop as H.264 video.

It can be used from the command line or through a graphical interface.

> **Experimental.** The protocol was reconstructed from the behaviour of the "vCastSender" Chrome extension (v3.2.1) and observations of a receiver on a local network. This project is not affiliated with or endorsed by any vendor.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [Testing](#testing)
- [Usage](#usage)
- [Graphical interface](#graphical-interface)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Platform notes](#platform-notes)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [License](#license)

## Features

- Discovers receivers using a 6-character PIN or connects directly to an IPv4 address.
- Control channel with device identification, receiver properties and heartbeat.
- Two cast transports, selected automatically or forced from the command line:
  - **WebSocket**, as used by the Chrome extension.
  - **Plain TCP**, as exposed by the native PC receiver.
- Screen capture and H.264 encoding through the system `ffmpeg` on Windows, Linux and macOS.
- Supports both user-initiated sharing and receiver-initiated invitations.
- Test mode that sends a slowly panning photo of kittens instead of the desktop.
- Graphical control window built with Swing, featuring light and dark themes.
- Session lifecycle management, connection monitoring and structured logging.

## Requirements

- **JDK 22** or later.
- **Maven 3.9** or later (required to build the project).
- **ffmpeg** available on the system `PATH`, or specified using `--ffmpeg`.
- Sender and receiver connected to the **same local network**.

The program checks for ffmpeg at startup and, if it is missing, prints the installation command for the current system:

| System | Install command |
| --- | --- |
| Windows | `winget install Gyan.FFmpeg` |
| macOS | `brew install ffmpeg` |
| Linux | `sudo apt install ffmpeg` |

## Build

The project uses Maven to compile the Java sources and package them into an executable JAR.

From the project root, run:

```bash
mvn clean package
```

The resulting JAR is generated in the `target/` directory:

```text
target/vcats-1.0.0.jar
```

To launch the application:

```bash
java -jar target/vcats-1.0.0.jar
```

The JAR does not bundle ffmpeg: it must be installed separately and available on your system `PATH`.

The project can also be imported and built directly in IntelliJ IDEA. The entry point is `main.Main`.

## Testing

vCats includes automated unit tests written with **JUnit 5**.

The tests cover:

- Command-line options and default configuration.
- FFmpeg argument parsing.
- H.264 NAL unit parsing.
- H.264 access unit splitting, including chunk boundaries and size limits.
- PIN computation, address conversion and IPv4 validation.

Run the tests with:

```bash
mvn test
```

To run a specific test class:

```bash
mvn -Dtest=PinResolverTest test
```

To perform a clean build and run the complete verification lifecycle:

```bash
mvn clean verify
```

## Usage

Run the application with a receiver PIN or IP address:

```bash
java -jar target/vcats-1.0.0.jar <PIN|IP> [options]
```

Without arguments, the program opens the graphical interface.

Examples:

```bash
java -jar target/vcats-1.0.0.jar ABC123
java -jar target/vcats-1.0.0.jar 192.168.1.50 --size 1280x720 --fps 24
java -jar target/vcats-1.0.0.jar 192.168.1.50 --test
```

Press `Ctrl+C` to stop. The program attempts to terminate the sharing session, release the receiver and stop ffmpeg before exiting.

### Options

| Option | Default | Description |
| --- | --- | --- |
| `<PIN\|IP>` | Required | Receiver PIN (6 characters) or IPv4 address |
| `--name NAME` | Local host name | Device name shown on the receiver |
| `--type manual\|invited` | `manual` | `manual`: receiver must accept the share. `invited`: receiver requested it |
| `--size WxH` | `1920x1080` | Output resolution |
| `--fps N` | `30` | Frame rate |
| `--bitrate RATE` | `3M` | Video bitrate, in ffmpeg notation |
| `--ffmpeg PATH` | `ffmpeg` | ffmpeg executable |
| `--input "ARGS"` | None | Custom ffmpeg input arguments, replacing the default screen capture |
| `--test` | Off | Send the kitten test image instead of the screen |
| `--raw` | Automatic | Force the plain TCP transport |
| `--ws` | Automatic | Force the WebSocket transport |

## Graphical interface

Start the program without arguments to open the control window:

```bash
java -jar target/vcats-1.0.0.jar
```

The window is organised into cards (Connection, Video and a collapsible Advanced settings section) and offers the same settings as the command line.

| Field | Option | Notes |
| --- | --- | --- |
| PIN or IP address | `<PIN\|IP>` | Required |
| Device name | `--name` | Defaults to the local host name |
| Share type | `--type` | Manual or invited |
| Transport | `--raw`, `--ws` | Automatic, native TCP or WebSocket |
| Resolution | `--size` | Presets or any `WIDTHxHEIGHT` |
| Frame rate | `--fps` | Presets or values from 1 to 60 |
| Bitrate | `--bitrate` | For example `3M` or `2500k` |
| ffmpeg | `--ffmpeg` | Path to the executable |
| Custom ffmpeg input | `--input` | Leave empty to capture the screen |
| Test image | `--test` | Sends the kitten image instead of the screen |

- **Start** validates the settings, locks the form and starts the session. Invalid values and connection errors are displayed above the log.
- **Stop** ends the share and releases the receiver.
- Closing the window during a share stops the session first.
- The **status indicator** shows the current stage: resolving, connecting, waiting, casting or stopped.
- The **log area** displays the same messages as the console.
- The **theme** follows the system setting at startup where supported. The button in the top right switches between light and dark.
- The **logo** is loaded from `assets/logo.png`, either from the working directory or the classpath.

## How it works

1. **Discovery.** A PIN encodes the receiver's address. The first octet is derived from the local network interfaces and each candidate is probed on the control port. An IP address is used directly.
2. **Control channel.** A WebSocket on port 57407 carries line-based text messages. The client introduces itself, requests receiver properties and sends a heartbeat every 3 seconds. The receiver reports events such as `startMirror`, `stopMirror` and `disconnect`.
3. **Transport selection.** The client checks the available cast transports and selects WebSocket or plain TCP. The transport can also be forced using `--raw` or `--ws`.
4. **Cast channel.** An RTSP-like exchange negotiates the session: `OPTIONS` for keep-alive, `SETUP` to start and `TEARDOWN` to end. The receiver's response is used to determine whether sharing is accepted.
5. **Video.** ffmpeg captures the screen and encodes H.264 with access unit delimiters. The client splits the stream into access units, sends the SPS/PPS configuration first and then the video frames, each preceded by a 128-byte header containing payload length, type and timestamp.

### Ports

| Purpose | WebSocket transport | Plain TCP transport |
| --- | --- | --- |
| Control | 57407 | 57407 |
| RTSP | 51042 | 51040 |
| Video | 51032 | 51030 |
| Audio (opened) | 51033 | 51020 |

The control channel always uses WebSocket, regardless of the selected cast transport.

## Project structure

Java source files are under `src/main/`, while automated tests are under `src/test/`. Assets are stored in `assets/`.

```text
assets/
├── logo.png
├── logo-readme.png
└── test-kittens.jpg

src/
├── main/
│   ├── Main.java
│   ├── config/
│   │   └── Options.java
│   ├── util/
│   │   └── Log.java
│   ├── ui/
│   │   ├── ControlWindow.java
│   │   ├── OptionsForm.java
│   │   ├── LogPanel.java
│   │   ├── theme/
│   │   │   ├── Theme.java
│   │   │   └── Logo.java
│   │   └── widget/
│   ├── net/
│   │   ├── Channel.java
│   │   ├── WebSocketChannel.java
│   │   ├── TcpChannel.java
│   │   └── Transport.java
│   ├── protocol/
│   │   ├── Protocol.java
│   │   ├── PinResolver.java
│   │   ├── ControlClient.java
│   │   └── EShareClient.java
│   ├── media/
│   │   ├── Ffmpeg.java
│   │   ├── VideoPump.java
│   │   ├── AuSplitter.java
│   │   └── H264.java
│   └── session/
│       ├── CastRunner.java
│       ├── CastSession.java
│       └── Phase.java
└── test/
    └── java/main/
        ├── config/
        │   └── OptionsTest.java
        ├── media/
        │   ├── AuSplitterTest.java
        │   ├── FfmpegTest.java
        │   └── H264Test.java
        └── protocol/
            └── PinResolverTest.java
```

Files in `assets/` are read from the working directory, so start the program from the project root.

## Platform notes

| System | Capture backend | Notes |
| --- | --- | --- |
| Windows | `gdigrab` | Restart the IDE or terminal after installing ffmpeg so the new `PATH` is picked up |
| macOS | `avfoundation` | The terminal or IDE needs Screen Recording permission. The screen device index is detected automatically |
| Linux | `x11grab` | X11 and XWayland only. On Wayland use `--input` or switch to an X11 session |

## Troubleshooting

**`Connection refused` when the cast starts**

The control port answered but the cast ports did not. The receiver may use the other transport: try `--raw` or `--ws`. If neither works, check that screen sharing is enabled on the receiver.

**`ffmpeg is not executable`**

Install ffmpeg or pass its location with `--ffmpeg`. On Windows, restart the IDE after installing so it sees the new `PATH`.

**The share is never accepted**

With `--type manual`, someone has to accept the request on the receiver. The program waits up to 60 seconds.

**Black or empty image on Linux**

The session is probably running under Wayland. Use `--input` with a Wayland-capable capture command, or log in to an X11 session.

**Checking the connection**

Run with `--test`. The receiver should show a slowly panning photo of kittens. This separates connection problems from screen-capture problems and shows whether the stream is live.

The image is read from `assets/test-kittens.jpg` in the working directory. If the file is missing, a generated test pattern is sent instead.

## Limitations

- Sender only: there is no receiver implementation.
- No audio. The audio channel is opened but not used.
- The control channel uses plain `ws://`, with no encryption or authentication beyond the receiver accepting the share. Use it only on trusted networks and with receivers you are authorised to use.
- Automated unit tests cover selected components; they do not replace integration testing with a real receiver.
- Hardware testing with a real ViewBoard is still pending.
- Linux code paths have not been fully tested.
- The protocol is reconstructed, not officially documented. Other receiver versions may behave differently.

## License

Released under the MIT License. See [LICENSE](LICENSE) for the full text.
