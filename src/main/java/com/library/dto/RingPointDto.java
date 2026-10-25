package com.library.dto;

/**
 * Coordinate of a virtual node on the 32-bit consistent hash ring (Member 3).
 * Used for high-fidelity ring canvas and sharding visualization.
 */
public record RingPointDto(
        String physicalNode,
        String virtualNode,
        long hash,
        double ringPositionPercent
) {}
