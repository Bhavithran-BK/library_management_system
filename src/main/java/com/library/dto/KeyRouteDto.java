package com.library.dto;

public record KeyRouteDto(
        String key,
        long keyHash,
        String assignedNode,
        String virtualNode,
        long virtualNodeHash,
        double ringPositionPercent
) {}
