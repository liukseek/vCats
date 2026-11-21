import Foundation
import ScreenCaptureKit
import AVFoundation
import CoreMedia
import AudioToolbox

private func log(_ message: String) {
    let line = "[vCats-audio] \(message)\n"
    FileHandle.standardError.write(Data(line.utf8))
}

final class AudioOutput: NSObject, SCStreamOutput {

    private let outputFormat = AVAudioFormat(
        commonFormat: .pcmFormatInt16,
        sampleRate: 48_000,
        channels: 2,
        interleaved: true
    )!

    private var converter: AVAudioConverter?
    private var inputFormat: AVAudioFormat?

    func stream(
        _ stream: SCStream,
        didOutputSampleBuffer sampleBuffer: CMSampleBuffer,
        of outputType: SCStreamOutputType
    ) {
        guard outputType == .audio, sampleBuffer.isValid else {
            return
        }

        do {
            try sampleBuffer.withAudioBufferList { audioBufferList, _ in
                guard
                    let description = sampleBuffer.formatDescription?.audioStreamBasicDescription,
                    let format = AVAudioFormat(
                        standardFormatWithSampleRate: description.mSampleRate,
                        channels: description.mChannelsPerFrame
                    ),
                    let input = AVAudioPCMBuffer(
                        pcmFormat: format,
                        bufferListNoCopy: audioBufferList.unsafePointer
                    )
                else {
                    return
                }

                convertAndWrite(input)
            }
        } catch {
            log("Errore lettura audio: \(error.localizedDescription)")
        }
    }

    private func convertAndWrite(_ input: AVAudioPCMBuffer) {
        if converter == nil || inputFormat != input.format {
            inputFormat = input.format
            converter = AVAudioConverter(
                from: input.format,
                to: outputFormat
            )
        }

        guard let converter else {
            log("Impossibile creare il convertitore audio")
            return
        }

        let ratio = outputFormat.sampleRate / input.format.sampleRate
        let capacity = AVAudioFrameCount(
            Double(input.frameLength) * ratio + 32
        )

        guard let output = AVAudioPCMBuffer(
            pcmFormat: outputFormat,
            frameCapacity: max(capacity, 32)
        ) else {
            return
        }

        var suppliedInput = false
        var conversionError: NSError?

        let status = converter.convert(
            to: output,
            error: &conversionError
        ) { _, inputStatus in

            if suppliedInput {
                inputStatus.pointee = .noDataNow
                return nil
            }

            suppliedInput = true
            inputStatus.pointee = .haveData
            return input
        }

        if let conversionError {
            log("Errore conversione: \(conversionError.localizedDescription)")
            return
        }

        guard status != .error, output.frameLength > 0 else {
            return
        }

        let audioBuffer = output.audioBufferList.pointee.mBuffers

        guard let data = audioBuffer.mData else {
            return
        }

        let byteCount = Int(audioBuffer.mDataByteSize)

        FileHandle.standardOutput.write(
            Data(bytes: data, count: byteCount)
        )
    }
}

@main
struct SystemAudioCapture {

    static func main() async {
        do {
            log("Inizializzazione ScreenCaptureKit...")

            let content = try await SCShareableContent
                .excludingDesktopWindows(
                    false,
                    onScreenWindowsOnly: true
                )

            guard let display = content.displays.first else {
                throw NSError(
                    domain: "vCats",
                    code: 1,
                    userInfo: [
                        NSLocalizedDescriptionKey:
                            "Nessun display disponibile"
                    ]
                )
            }

            let filter = SCContentFilter(
                display: display,
                excludingApplications: [],
                exceptingWindows: []
            )

            let configuration = SCStreamConfiguration()
            configuration.capturesAudio = true
            configuration.captureMicrophone = false
            configuration.excludesCurrentProcessAudio = true
            configuration.sampleRate = 48_000
            configuration.channelCount = 2

            let output = AudioOutput()

            let stream = SCStream(
                filter: filter,
                configuration: configuration,
                delegate: nil
            )

            let audioQueue = DispatchQueue(
                label: "io.github.vcats.system-audio"
            )

            try stream.addStreamOutput(
                output,
                type: .audio,
                sampleHandlerQueue: audioQueue
            )

            try await stream.startCapture()

            log("Acquisizione avviata: PCM 48000 Hz, stereo, signed 16-bit")
            log("Microfono disabilitato. Premi CTRL+C per terminare.")

            while true {
                try await Task.sleep(for: .seconds(3600))
            }

        } catch {
            log("ERRORE: \(error.localizedDescription)")
            exit(EXIT_FAILURE)
        }
    }
}
