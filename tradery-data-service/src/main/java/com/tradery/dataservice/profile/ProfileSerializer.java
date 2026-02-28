package com.tradery.dataservice.profile;

import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Msgpack encoding/decoding for volume profile tick maps.
 * Each profile is stored as Map<Integer, double[]> where key is the price tick
 * (price / tickSize rounded to int) and value is [buyVolume, sellVolume].
 */
public class ProfileSerializer {

    /**
     * Serialize a tick map to msgpack bytes.
     * Format: map header, then for each entry: int key, array of 2 doubles.
     */
    public static byte[] serialize(Map<Integer, double[]> tickMap) throws IOException {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packMapHeader(tickMap.size());
            for (var entry : tickMap.entrySet()) {
                packer.packInt(entry.getKey());
                packer.packArrayHeader(2);
                packer.packDouble(entry.getValue()[0]);
                packer.packDouble(entry.getValue()[1]);
            }
            return packer.toByteArray();
        }
    }

    /**
     * Deserialize msgpack bytes back to a tick map.
     */
    public static Map<Integer, double[]> deserialize(byte[] blob) throws IOException {
        Map<Integer, double[]> tickMap = new TreeMap<>();

        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(blob)) {
            int mapSize = unpacker.unpackMapHeader();
            for (int i = 0; i < mapSize; i++) {
                int tick = unpacker.unpackInt();
                int arrLen = unpacker.unpackArrayHeader();
                double buyVol = unpacker.unpackDouble();
                double sellVol = arrLen >= 2 ? unpacker.unpackDouble() : 0;
                // Skip any extra fields for forward compatibility
                for (int j = 2; j < arrLen; j++) {
                    unpacker.skipValue();
                }
                tickMap.put(tick, new double[]{buyVol, sellVol});
            }
        }

        return tickMap;
    }

    /**
     * Merge multiple profile blobs by summing buy/sell volumes per tick.
     */
    public static Map<Integer, double[]> merge(List<byte[]> blobs) throws IOException {
        Map<Integer, double[]> merged = new TreeMap<>();

        for (byte[] blob : blobs) {
            Map<Integer, double[]> tickMap = deserialize(blob);
            for (var entry : tickMap.entrySet()) {
                merged.merge(entry.getKey(), entry.getValue(), (existing, incoming) -> {
                    existing[0] += incoming[0];
                    existing[1] += incoming[1];
                    return existing;
                });
            }
        }

        return merged;
    }

    /**
     * Merge a tick map into an accumulator in-place.
     */
    public static void mergeInto(Map<Integer, double[]> accumulator, Map<Integer, double[]> source) {
        for (var entry : source.entrySet()) {
            accumulator.merge(entry.getKey(), entry.getValue(), (existing, incoming) -> {
                existing[0] += incoming[0];
                existing[1] += incoming[1];
                return existing;
            });
        }
    }
}
