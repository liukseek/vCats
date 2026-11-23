package io.github.vcats.protocol;

import java.util.HexFormat;

public final class Protocol {
    private Protocol() {
    }

    public static final int PORT_CONTROL = 57407;
    public static final int DEVICE_TYPE = 17;
    public static final int T_CONFIG = 256, T_VIDEO = 257, T_AUDIO = 258;

    private static final String STREAM_PLIST_HEX =
            "62706c6973743030d101025773747265616d73a103d50406080a0c0507090b0d5474797065106e5c4368726f6d6553747265616d095c6d616368696e655f6e616d654a4368726f6d65426f6f6ba05a6d616368696e655f6970493132372e302e302e31a05f10136d616368696e655f6d61635f616464726573734f101131313a32323a33333a34343a35353a3636a0080b13152025273435424e59647a0000000000000101000000000000000e0000000000000000000000000000008f";

    public static final byte[] STREAM_PLIST = hex();

    static byte[] hex() {
        return HexFormat.of().parseHex(STREAM_PLIST_HEX);
    }
}
