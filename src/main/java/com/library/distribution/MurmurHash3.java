package com.library.distribution;

import java.nio.charset.StandardCharsets;

/**
 * 32-bit MurmurHash3 implementation for uniform, collision-resistant
 * hashing of keys on the consistent hash ring (Member 3).
 */
public final class MurmurHash3 {

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;
    private static final int SEED = 0x9747b28c;

    private MurmurHash3() {}

    /**
     * Hashes a string key to an unsigned 32-bit integer represented as a positive long [0, 2^32 - 1].
     */
    public static long hash32(String key) {
        if (key == null) return 0L;
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        return hash32(data, 0, data.length, SEED);
    }

    public static long hash32(byte[] data, int offset, int length, int seed) {
        int h1 = seed;
        int nblocks = length / 4;

        for (int i = 0; i < nblocks; i++) {
            int index = offset + (i * 4);
            int k1 = (data[index] & 0xff)
                    | ((data[index + 1] & 0xff) << 8)
                    | ((data[index + 2] & 0xff) << 16)
                    | ((data[index + 3] & 0xff) << 24);

            k1 *= C1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= C2;

            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }

        int index = offset + (nblocks * 4);
        int k1 = 0;
        int tail = length & 3;

        switch (tail) {
            case 3:
                k1 ^= (data[index + 2] & 0xff) << 16;
            case 2:
                k1 ^= (data[index + 1] & 0xff) << 8;
            case 1:
                k1 ^= (data[index] & 0xff);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= C2;
                h1 ^= k1;
        }

        h1 ^= length;
        h1 ^= (h1 >>> 16);
        h1 *= 0x85ebca6b;
        h1 ^= (h1 >>> 13);
        h1 *= 0xc2b2ae35;
        h1 ^= (h1 >>> 16);

        // Convert signed 32-bit int to unsigned long [0, 4294967295]
        return ((long) h1) & 0xFFFFFFFFL;
    }
}
