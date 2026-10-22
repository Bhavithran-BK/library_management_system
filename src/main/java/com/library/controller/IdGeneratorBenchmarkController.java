package com.library.controller;

import com.library.dto.ApiResponse;
import com.library.dto.BenchmarkRequest;
import com.library.dto.BenchmarkResultDto;
import com.library.idgenerator.IdGeneratorService;
import com.library.idgenerator.IdPrefix;
import com.library.service.BenchmarkService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/id-generator")
public class IdGeneratorBenchmarkController {

    private final BenchmarkService benchmarkService;
    private final IdGeneratorService idGeneratorService;

    public IdGeneratorBenchmarkController(BenchmarkService benchmarkService, IdGeneratorService idGeneratorService) {
        this.benchmarkService = benchmarkService;
        this.idGeneratorService = idGeneratorService;
    }

    @PostMapping("/benchmark")
    public ResponseEntity<ApiResponse<BenchmarkResultDto>> runBenchmark(@RequestBody(required = false) BenchmarkRequest request) {
        if (request == null) {
            request = new BenchmarkRequest();
        }
        BenchmarkResultDto result = benchmarkService.runBenchmark(request);
        return ResponseEntity.ok(ApiResponse.success("Benchmark completed with 0 collisions", result));
    }

    @GetMapping("/sample")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSampleIds() {
        Map<String, Object> sample = new HashMap<>();
        sample.put("strategy", idGeneratorService.getActiveStrategyName());
        sample.put("bookId", idGeneratorService.generateBookId());
        sample.put("memberId", idGeneratorService.generateMemberId());
        sample.put("loanId", idGeneratorService.generateLoanId());
        sample.put("reservationId", idGeneratorService.generateReservationId());
        sample.put("transactionId", idGeneratorService.generateTransactionId());

        return ResponseEntity.ok(ApiResponse.success(sample));
    }
}
