package com.library.controller;

import com.library.dto.*;
import com.library.service.DistributionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for Member 3: Distributed Storage, MongoDB Sharding & Consistent Hashing.
 */
@RestController
@RequestMapping({"/api/admin/distribution", "/api/distribution"})
public class DistributionController {

    private final DistributionService distributionService;

    public DistributionController(DistributionService distributionService) {
        this.distributionService = distributionService;
    }

    /**
     * Agreed API Contract:
     * GET /api/admin/distribution
     * Returns full Consistent Hash Ring status, MongoDB sharding state, and key allocation.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<DistributionOverviewDto>> getDistributionOverview() {
        DistributionOverviewDto overview = distributionService.getDistributionOverview();
        return ResponseEntity.ok(ApiResponse.success("Distributed storage and hash ring status retrieved", overview));
    }

    /**
     * Adds a logical storage node to the consistent hash ring.
     */
    @PostMapping("/nodes")
    public ResponseEntity<ApiResponse<Map<String, Object>>> addNode(@RequestBody NodeOperationRequest request) {
        distributionService.addNode(request.getNodeName());
        DistributionOverviewDto updated = distributionService.getDistributionOverview();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Logical storage node '" + request.getNodeName() + "' added to consistent hash ring",
                        Map.of("nodeName", request.getNodeName(), "overview", updated)));
    }

    /**
     * Removes a logical storage node from the consistent hash ring.
     */
    @DeleteMapping("/nodes/{nodeName}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> removeNode(@PathVariable String nodeName) {
        distributionService.removeNode(nodeName);
        DistributionOverviewDto updated = distributionService.getDistributionOverview();
        return ResponseEntity.ok(ApiResponse.success("Logical storage node '" + nodeName + "' removed from consistent hash ring",
                Map.of("nodeName", nodeName, "overview", updated)));
    }

    /**
     * Resolves the target storage node and ring position for an arbitrary key.
     */
    @GetMapping("/route")
    public ResponseEntity<ApiResponse<KeyRouteDto>> routeKey(@RequestParam String key) {
        KeyRouteDto route = distributionService.routeKey(key);
        return ResponseEntity.ok(ApiResponse.success("Key routed successfully on consistent hash ring", route));
    }

    /**
     * Simulates dynamic node addition/removal and measures keys migrated against theoretical expectation.
     */
    @PostMapping("/rebalance-simulation")
    public ResponseEntity<ApiResponse<RebalanceSimulationResponse>> simulateRebalance(@RequestBody(required = false) RebalanceSimulationRequest request) {
        if (request == null) {
            request = new RebalanceSimulationRequest();
        }
        RebalanceSimulationResponse response = distributionService.simulateRebalance(request);
        return ResponseEntity.ok(ApiResponse.success("Consistent hash rebalance simulation completed", response));
    }

    /**
     * Executes a large-sample uniformity benchmark (e.g. 10,000 keys) to evaluate virtual node balance.
     */
    @PostMapping("/benchmark")
    public ResponseEntity<ApiResponse<Map<String, Object>>> runBenchmark(@RequestParam(defaultValue = "10000") int sampleSize) {
        Map<String, Object> results = distributionService.benchmarkUniformity(sampleSize);
        return ResponseEntity.ok(ApiResponse.success("Consistent hash ring uniformity benchmark completed", results));
    }
}
