<p align="center">
  <img src="assets/logo-readme.png" alt="vCats" width="260">
</p>

<p align="center">
  <strong>Screen sharing for ViewBoard / vCast receivers</strong>
</p>

---

vCats is a command-line screen-sharing client written in Java for ViewBoard / vCast (EShare-compatible) receivers. It finds the receiver from its PIN or IP address, asks it to accept a screen share and streams the desktop to it as H.264 video. It can be used from the command line or through a small graphical window.

> **Experimental.** The protocol was reconstructed from the behaviour of the "vCastSender" Chrome extension (v3.2.1) and from observing a receiver on a local network. This project is not affiliated with or endorsed by any vendor.

## Table of Contents

- [Features](#features)
- [Requirements](#requirements)
- [Build](#build)
- [Usage](#usage)
- [Graphical interface](#graphical-interface)
- [How it works](#how-it-works)
- [Project structure](#project-structure)
- [Platform notes](#platform-notes)
- [Troubleshooting](#troubleshooting)
- [Limitations](#limitations)
- [License](#license)

## Features

- Finds the receiver from a 6-character PIN, or connects directly to an IP address.
- Control channel with device presentation, receiver properties and heartbeat.
- Two cast transports, chosen automatically or forced from the command line:
  - **WebSocket**, as used by the Chrome extension;
  - **plain TCP**, as exposed by the native PC receiver.
- Screen capture and H.264 encoding through the system `ffmpeg` on Windows, Linux and macOS.
- Supports both a share started by the user (the receiver must accept it) and a share invited by the receiver.
- Test mode that sends a slowly panning photo of kittens instead of the screen, to check the connection without sharing the desktop.
- Graphical control window (Swing, no extra libraries) with light and dark themes, to configure, start and stop a share and to follow the log.

## Requirements

- **JDK 22** or later
- **Maven 3.9** or later (required to build the project)
- **ffmpeg** on the system `PATH`, or passed with `--ffmpeg`
- The sender and the receiver on the **same local network**

The program checks for ffmpeg at startup and, if it is missing, prints the install command for your system:

| System  | Install command              |
| ------- | ---------------------------- |
| Windows | `winget install Gyan.FFmpeg` |
| macOS   | `brew install ffmpeg`        |
| Linux   | `sudo apt install ffmpeg`    |

## Build

The project uses Maven to compile the Java sources and package them into an executable JAR. From the project root, run:

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

## Usage

Run the application with a receiver PIN or IP address:

```bash
java -jar target/vcats-1.0.0.jar <PIN|IP> [options]
```

Without arguments the program opens the [graphical interface](#graphical-interface).

Examples:

```bash
java -jar target/vcats-1.0.0.jar ABC123
java -jar target/vcats-1.0.0.jar 192.168.1.50 --size 1280x720 --fps 24
java -jar target/vcats-1.0.0.jar 192.168.1.50 --test
```

Press `Ctrl+C` to stop. The program sends a teardown to the receiver and stops ffmpeg before exiting.

### Options

| Option                   | Default         | Description                                                                  |
| ------------------------ | --------------- | ---------------------------------------------------------------------------- |
| `<PIN\|IP>`              | required        | Receiver PIN (6 characters) or IPv4 address                                  |
| `--name NAME`            | local host name | Device name shown on the receiver                                            |
| `--type manual\|invited` | `manual`        | `manual`: the receiver must accept the share. `invited`: the receiver asked for it |
| `--size WxH`             | `1920x1080`     | Output resolution                                                            |
| `--fps N`                | `30`            | Frame rate                                                                   |
| `--bitrate RATE`         | `3M`            | Video bitrate, in ffmpeg notation                                            |
| `--ffmpeg PATH`          | `ffmpeg`        | ffmpeg executable                                                            |
| `--input "ARGS"`         | none            | Custom ffmpeg input arguments, replacing the default screen capture          |
| `--test`                 | off             | Send the kitten test image instead of the screen                             |
| `--raw`                  | automatic       | Force the plain TCP transport                                                |
| `--ws`                   | automatic       | Force the WebSocket transport                                                |

## Graphical interface

Start the program without arguments to open the control window:

```bash
java -jar target/vcats-1.0.0.jar
```

The window is organised in cards (Connection, Video and a collapsible Advanced settings section) and offers the same settings as the command line:

| Field               | Option          | Notes                                                |
| ------------------- | --------------- | ---------------------------------------------------- |
| PIN or IP address   | `<PIN\|IP>`     | Required                                             |
| Device name         | `--name`        | Defaults to the local host name                      |
| Share type          | `--type`        | Manual or invited                                    |
| Transport           | `--raw`, `--ws` | Automatic, native TCP or WebSocket                   |
| Resolution          | `--size`        | Presets, or any `WIDTHxHEIGHT`                       |
| Frame rate          | `--fps`         | Presets, or any value from 1 to 60                   |
| Bitrate             | `--bitrate`     | For example `3M` or `2500k`                          |
| ffmpeg              | `--ffmpeg`      | Advanced settings. Path to the executable, with a file chooser |
| Custom ffmpeg input | `--input`       | Advanced settings. Leave empty to capture the screen |
| Test image          | `--test`        | Sends the kitten image instead of the screen         |

- **Start** validates the settings, locks the form and starts the session. Invalid values and connection errors are shown in red above the log, not in a dialog.
- **Stop** ends the share and releases the receiver. Closing the window during a share stops it first.
- The **status indicator** (bottom left) shows the current stage: looking for the board, connecting, waiting for the board to accept, sharing, stopped or failed.
- The **log area** shows the same messages as the console.
- The **theme** follows the system setting at startup (Windows, macOS and GNOME on Linux; light otherwise). The button in the top right switches between light and dark.
- The **logo** in the header, also used as window and taskbar icon, is cropped automatically from `assets/logo.png`, read from the working directory or the classpath, so start the program from the project root. If the file is missing, the window works without the logo.

## How it works

1. **Discovery.** A PIN encodes the last three octets of the receiver's address. The first octet is guessed from the local network interfaces and each candidate is probed on the control port. An IP address is used as given.
2. **Control channel.** A WebSocket on port 57407 carries line-based text messages: the client introduces itself, requests the receiver properties and sends a heartbeat every 3 seconds. The receiver reports events such as `startMirror`, `stopMirror` and `disconnect`.
3. **Transport selection.** If the WebSocket RTSP port is closed and the native TCP port is open, plain TCP is used; otherwise WebSocket.
4. **Cast channel.** An RTSP-like exchange negotiates the session: `OPTIONS` every second as keep-alive, `SETUP` to start, `TEARDOWN` to end. Over WebSocket the receiver answers with an explicit allowed/refused header; over TCP the reply to `SETUP` carries the data port and counts as acceptance.
5. **Video.** ffmpeg captures the screen and encodes H.264 with access unit delimiters. The program splits the stream into access units, sends the SPS/PPS configuration first, then each frame preceded by a 128-byte header with payload length, type and timestamp.

### Ports

| Purpose        | WebSocket transport | Plain TCP transport |
| -------------- | ------------------- | ------------------- |
| Control        | 57407               | 57407               |
| RTSP           | 51042               | 51040               |
| Video          | 51032               | 51030               |
| Audio (opened) | 51033               | 51020               |

The control channel always uses WebSocket, whatever the cast transport.

## Project structure

The Java sources are under `src/main/`. The logo files and the test image are in `assets/`.

```text
assets/
├── logo.png                   Original logo, read by the graphical interface
├── logo-readme.png            Same logo cropped to its content, with rounded corners and a thin border, used in this README
└── test-kittens.jpg           Image sent in test mode (--test)

src/main/
├── Main.java                  Entry point
├── config/
│   └── Options.java           Command-line options
├── util/
│   └── Log.java               Console logging, with listeners for the GUI
├── ui/
│   ├── ControlWindow.java     Main window: header, Start, Stop and status
│   ├── OptionsForm.java       Settings cards, converted to Options
│   ├── LogPanel.java          Log display
│   ├── theme/
│   │   ├── Theme.java         Light and dark palettes, fonts, system theme detection
│   │   └── Logo.java          Loads assets/logo.png and crops the symbol
│   └── widget/                Custom Swing components
│       ├── Card.java, ThemedPanel.java, ThemedLabel.java
│       ├── FlatButton.java, FlatTextField.java, FlatComboBox.java
│       ├── ToggleSwitch.java, StatusPill.java, LogoMark.java
│       └── FlatScrollBarUI.java, ChevronIcon.java
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
    ├── CastRunner.java        Full flow for one share: check, resolve, connect, run, stop
    ├── CastSession.java       Ties control, cast and ffmpeg together
    └── Phase.java             Session stages reported to the interface
```

Files in `assets/` are read from the working directory, so start the program from the project root.

## Platform notes

| System  | Capture backend | Notes                                                                                    |
| ------- | --------------- | ---------------------------------------------------------------------------------------- |
| Windows | `gdigrab`       | Restart the IDE or terminal after installing ffmpeg so the new `PATH` is picked up       |
| macOS   | `avfoundation`  | The terminal or IDE needs the Screen Recording permission. The screen device index is detected automatically |
| Linux   | `x11grab`       | X11 and XWayland only. On Wayland use `--input` or switch to an X11 session              |

## Troubleshooting

**`Connection refused` when the cast starts**
The control port answered but the cast ports did not. The receiver may use the other transport: try `--raw` or `--ws`. If neither works, check that screen sharing is enabled on the receiver.

**`ffmpeg is not executable`**
Install ffmpeg or pass its location with `--ffmpeg`. On Windows, restart the IDE after installing so it sees the new `PATH`.

**The share is never accepted**
With `--type manual` someone has to accept the request on the receiver. The program waits up to 60 seconds.

**Black or empty image on Linux**
The session is probably Wayland. Use `--input` with a Wayland-capable capture command, or log in to an X11 session.

**Checking the connection**
Run with `--test`. The receiver should show a slowly panning photo of kittens: this separates connection problems from screen-capture problems, and the movement shows the stream is live and not frozen. The image is read from `assets/test-kittens.jpg` in the working directory, so start the program from the project root; if the file is missing, a generated test pattern is sent instead.

## Limitations

- Sender only: there is no receiver implementation.
- No audio. The audio channel is opened but not used.
- The control channel uses plain `ws://`, with no encryption or authentication beyond the receiver accepting the share. Use it only on trusted networks and with receivers you are authorised to use.
- Tested only on Windows against a native PC receiver. The macOS and Linux code paths have not been run.
- The graphical interface compiles and its layout has been checked in both themes, but it has not been used for a live share yet. It does not remember settings or the theme between runs.
- The protocol is reconstructed, not documented: other receiver versions may behave differently.

## License

Released under the MIT License. See [LICENSE](LICENSE) for the full text.
