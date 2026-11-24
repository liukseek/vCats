
package io.github.vcats.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PinResolverTest {

    @Test
    void shouldComputePinCorrectly() {
        assertEquals(0, PinResolver.pinCompute("000000", 32));
        assertEquals(1, PinResolver.pinCompute("000001", 32));
        assertEquals(10, PinResolver.pinCompute("00000A", 32));
    }

    @Test
    void shouldComputePinRegardlessOfLetterCase() {
        assertEquals(
                PinResolver.pinCompute("ABC123", 32),
                PinResolver.pinCompute("abc123", 32)
        );
    }

    @Test
    void shouldConvertPinToAddress() {
        assertEquals(
                "192.255.255.255",
                PinResolver.pinToAddress("000000", 192)
        );

        assertEquals(
                "192.255.255.254",
                PinResolver.pinToAddress("000001", 192)
        );

        assertEquals(
                "192.255.255.245",
                PinResolver.pinToAddress("00000A", 192)
        );
    }

    @Test
    void shouldConvertPinRegardlessOfLetterCase() {
        assertEquals(
                PinResolver.pinToAddress("ABC123", 192),
                PinResolver.pinToAddress("abc123", 192)
        );
    }

    @Test
    void shouldPreserveFirstOctet() {
        assertTrue(
                PinResolver.pinToAddress("000001", 192)
                        .startsWith("192.")
        );

        assertTrue(
                PinResolver.pinToAddress("000001", 10)
                        .startsWith("10.")
        );
    }

    @Test
    void shouldAcceptValidIpv4Addresses() {
        assertTrue(PinResolver.validIp("192.168.1.1"));
        assertTrue(PinResolver.validIp("10.0.0.1"));
        assertTrue(PinResolver.validIp("255.255.255.255"));
        assertTrue(PinResolver.validIp("0.0.0.0"));
    }

    @Test
    void shouldRejectInvalidIpv4Addresses() {
        assertFalse(PinResolver.validIp("256.168.1.1"));
        assertFalse(PinResolver.validIp("192.168.1.256"));
        assertFalse(PinResolver.validIp("192.168.1"));
        assertFalse(PinResolver.validIp("192.168.1.1.5"));
        assertFalse(PinResolver.validIp("192.168.a.1"));
        assertFalse(PinResolver.validIp("localhost"));
        assertFalse(PinResolver.validIp(""));
        assertFalse(PinResolver.validIp(null));
    }
}
