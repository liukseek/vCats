// vCats: Windows system audio capture (WASAPI loopback of the default output).
// Writes the raw mix-format samples to stdout, with silence filled in while
// nothing is playing so the stream keeps a steady clock.
//   vcats-loopback.exe --info   prints: <rate> <channels> <bits> <float 0|1>
//   vcats-loopback.exe          streams raw audio to stdout
using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Threading;

static class Loopback
{
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    class MMDeviceEnumeratorCom { }

    [ComImport, Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [ComImport, Guid("D666063F-1587-4E43-81F1-B948E807363F"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IMMDevice
    {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams,
                     [MarshalAs(UnmanagedType.IUnknown)] out object iface);
    }

    [ComImport, Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioClient
    {
        int Initialize(int shareMode, int streamFlags, long bufferDuration,
                       long periodicity, IntPtr format, ref Guid sessionGuid);
        int GetBufferSize(out uint frames);
        int GetStreamLatency(out long latency);
        int GetCurrentPadding(out uint padding);
        int IsFormatSupported(int shareMode, IntPtr format, out IntPtr closest);
        int GetMixFormat(out IntPtr format);
        int GetDevicePeriod(out long defaultPeriod, out long minimumPeriod);
        int Start();
        int Stop();
        int Reset();
        int SetEventHandle(IntPtr handle);
        int GetService(ref Guid iid,
                       [MarshalAs(UnmanagedType.IUnknown)] out object service);
    }

    [ComImport, Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317"),
     InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    interface IAudioCaptureClient
    {
        int GetBuffer(out IntPtr data, out int frames, out int flags,
                      out long devicePosition, out long qpcPosition);
        int ReleaseBuffer(int frames);
        int GetNextPacketSize(out int frames);
    }

    [DllImport("kernel32.dll")]
    static extern IntPtr GetStdHandle(int handle);

    const int AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000;
    const int AUDCLNT_BUFFERFLAGS_SILENT = 2;
    const int CHUNK_MS = 20;

    [DllImport("kernel32.dll", SetLastError = true)]
    static extern bool WriteFile(IntPtr handle, byte[] buffer, int count,
                                 out int written, IntPtr overlapped);

    // Raw WriteFile: when vCats closes the pipe this fails and the helper
    // exits instead of lingering in the background.
    static void WriteAll(IntPtr handle, byte[] buffer, int count)
    {
        int offset = 0;

        while (offset < count)
        {
            byte[] part = buffer;
            int n = count - offset;

            if (offset > 0)
            {
                part = new byte[n];
                Buffer.BlockCopy(buffer, offset, part, 0, n);
            }

            int written;
            if (!WriteFile(handle, part, n, out written, IntPtr.Zero))
                Environment.Exit(0);

            offset += written;
        }
    }

    static void Check(int hr, string what)
    {
        if (hr < 0)
            throw new InvalidOperationException(what + " failed (0x" + hr.ToString("X8") + ")");
    }

    static int Main(string[] args)
    {
        try
        {
            return Run(args.Length > 0 && args[0] == "--info");
        }
        catch (IOException)
        {
            return 0; // the reader went away
        }
        catch (Exception e)
        {
            Console.Error.WriteLine("[vCats-loopback] " + e.Message);
            return 1;
        }
    }

    static int Run(bool infoOnly)
    {
        var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorCom();
        IMMDevice device;
        Check(enumerator.GetDefaultAudioEndpoint(0, 0, out device), "GetDefaultAudioEndpoint");

        Guid iidClient = new Guid("1CB9AD4C-DBFA-4c32-B178-C2F568A703B2");
        object obj;
        Check(device.Activate(ref iidClient, 23, IntPtr.Zero, out obj), "Activate");
        var client = (IAudioClient)obj;

        IntPtr format;
        Check(client.GetMixFormat(out format), "GetMixFormat");

        int tag = Marshal.ReadInt16(format, 0) & 0xFFFF;
        int channels = Marshal.ReadInt16(format, 2);
        int rate = Marshal.ReadInt32(format, 4);
        int blockAlign = Marshal.ReadInt16(format, 12);
        int bits = Marshal.ReadInt16(format, 14);
        bool isFloat = tag == 3;

        if (tag == 0xFFFE)
            isFloat = (Marshal.ReadInt16(format, 24) & 0xFFFF) == 3;

        if (infoOnly)
        {
            Console.Out.Write(rate + " " + channels + " " + bits + " " + (isFloat ? 1 : 0));
            Console.Out.Flush();
            return 0;
        }

        Guid session = Guid.Empty;
        Check(client.Initialize(0, AUDCLNT_STREAMFLAGS_LOOPBACK, 10000000, 0, format, ref session),
              "Initialize");

        Guid iidCapture = new Guid("C8ADBD64-E71E-48a0-A4DE-185C395CD317");
        object capObj;
        Check(client.GetService(ref iidCapture, out capObj), "GetService");
        var capture = (IAudioCaptureClient)capObj;

        Check(client.Start(), "Start");
        Console.Error.WriteLine("[vCats-loopback] capturing " + rate + " Hz, " + channels
                                + " ch, " + bits + "-bit" + (isFloat ? " float" : ""));

        IntPtr stdout = GetStdHandle(-11);

        int chunkFrames = rate * CHUNK_MS / 1000;
        byte[] silence = new byte[chunkFrames * blockAlign];
        byte[] buffer = new byte[0];

        Stopwatch clock = Stopwatch.StartNew();
        long lastDataTicks = clock.ElapsedTicks;
        long idleFrames = 0;
        long idleStart = 0;
        bool idle = false;

        while (true)
        {
            int packet;
            Check(capture.GetNextPacketSize(out packet), "GetNextPacketSize");

            if (packet > 0)
            {
                while (packet > 0)
                {
                    IntPtr data;
                    int frames, flags;
                    long devPos, qpcPos;
                    Check(capture.GetBuffer(out data, out frames, out flags, out devPos, out qpcPos),
                          "GetBuffer");

                    int bytes = frames * blockAlign;
                    if (buffer.Length < bytes) buffer = new byte[bytes];

                    if ((flags & AUDCLNT_BUFFERFLAGS_SILENT) != 0)
                        Array.Clear(buffer, 0, bytes);
                    else
                        Marshal.Copy(data, buffer, 0, bytes);

                    WriteAll(stdout, buffer, bytes);

                    Check(capture.ReleaseBuffer(frames), "ReleaseBuffer");
                    Check(capture.GetNextPacketSize(out packet), "GetNextPacketSize");
                }

                lastDataTicks = clock.ElapsedTicks;
                idle = false;
                continue;
            }

            long now = clock.ElapsedTicks;

            // Nothing is playing: after a short gap, keep the stream alive
            // with silence at the real-time rate.
            if (!idle && (now - lastDataTicks) * 1000 / Stopwatch.Frequency >= 30)
            {
                idle = true;
                idleStart = lastDataTicks;
                idleFrames = 0;
            }

            if (idle)
            {
                long target = (now - idleStart) * rate / Stopwatch.Frequency;

                if (target - idleFrames >= chunkFrames)
                {
                    WriteAll(stdout, silence, silence.Length);
                    idleFrames += chunkFrames;
                    continue;
                }
            }

            Thread.Sleep(5);
        }
    }
}
