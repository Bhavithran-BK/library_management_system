package com.library.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.CreateShortLinkRequest;
import com.library.dto.ShortLinkAnalyticsDto;
import com.library.dto.ShortLinkResponseDto;
import com.library.model.ShortLink;
import com.library.repository.ShortLinkRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Automated test suite for Base62 URL Shortener and Dual-Tier Cache Persistence (Member 4).
 */
@SpringBootTest
@AutoConfigureMockMvc
public class Base62ShortLinkTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private Base62Service base62Service;

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    @DisplayName("Base62 Encoding and Decoding roundtrip validation")
    public void testBase62EncodingAndDecoding() {
        assertEquals("0", base62Service.encode(0));
        assertEquals("1", base62Service.encode(1));
        assertEquals("Z", base62Service.encode(61));
        assertEquals("10", base62Service.encode(62));

        long[] testValues = {0L, 1L, 42L, 61L, 62L, 100_000L, 999_999L, 123456789L};
        for (long val : testValues) {
            String encoded = base62Service.encode(val);
            assertNotNull(encoded);
            assertFalse(encoded.isEmpty());
            long decoded = base62Service.decode(encoded);
            assertEquals(val, decoded, "Decoded value must match original encoded long: " + val);
        }
    }

    @Test
    @DisplayName("Create ShortLink creates MongoDB entity and warms Redis Tier 1 cache")
    public void testCreateShortLinkDualTierPersistence() {
        String testBookId = "BK-TEST-B62-" + System.currentTimeMillis();
        String targetUrl = "/#catalogue?id=" + testBookId;

        CreateShortLinkRequest req = new CreateShortLinkRequest();
        req.setTargetUrl(targetUrl);
        req.setBookId(testBookId);
        req.setTitle("Distributed Systems Architecture");

        ShortLinkResponseDto dto = shortLinkService.createShortLink(req);
        assertNotNull(dto);
        assertNotNull(dto.getCode());
        assertEquals(targetUrl, dto.getTargetUrl());

        // Verify MongoDB entity exists
        ShortLink mongoEntity = shortLinkRepository.findByCode(dto.getCode()).orElse(null);
        assertNotNull(mongoEntity);
        assertEquals(testBookId, mongoEntity.getBookId());

        // Verify Redis Tier 1 cache exists
        String cachedUrl = stringRedisTemplate.opsForValue().get("shortlink:" + dto.getCode());
        assertNotNull(cachedUrl);
        assertEquals(targetUrl, cachedUrl);

        // Resolve link
        String resolved = shortLinkService.resolveShortLink(dto.getCode());
        assertEquals(targetUrl, resolved);
    }

    @Test
    @DisplayName("REST Endpoints: POST /api/short-links, GET /s/{code} HTTP 302, and GET analytics")
    public void testShortLinkRestEndpoints() throws Exception {
        String bookId = "BK-REST-" + System.currentTimeMillis();
        String targetUrl = "/#catalogue?id=" + bookId;

        CreateShortLinkRequest req = new CreateShortLinkRequest();
        req.setTargetUrl(targetUrl);
        req.setBookId(bookId);
        req.setTitle("Clean Architecture Testing");

        // 1. POST /api/short-links
        String postResponse = mockMvc.perform(post("/api/short-links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").isNotEmpty())
                .andExpect(jsonPath("$.data.shortUrl").value(startsWith("/s/")))
                .andExpect(jsonPath("$.data.targetUrl").value(targetUrl))
                .andReturn().getResponse().getContentAsString();

        String code = objectMapper.readTree(postResponse).path("data").path("code").asText();
        assertNotNull(code);

        // 2. GET /s/{code} -> HTTP 302 Redirect
        mockMvc.perform(get("/s/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", targetUrl));

        // 3. GET /api/short-links/{code}/analytics
        mockMvc.perform(get("/api/short-links/" + code + "/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.targetUrl").value(targetUrl))
                .andExpect(jsonPath("$.data.cachedInRedis").value(true));
    }
}
