package com.library.dto;

import java.util.Map;

/**
 * Result of consistent hash rebalancing simulation (Member 3).
 */
public record RebalanceSimulationResponse(
        String nodeName,
        String operation,
        int totalKeys,
        int movedKeys,
        double actualRemapPercent,
        double theoreticalRemapPercent,
        double varianceFromTheory,
        Map<String, Integer> distributionBefore,
        Map<String, Integer> distributionAfter,
        String conclusion
) {}
