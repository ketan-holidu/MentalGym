package com.mentalgym.config

import org.springframework.boot.context.properties.ConfigurationProperties
import java.time.ZoneId

@ConfigurationProperties(prefix = "mental-gym")
data class MentalGymProperties(
    val passThreshold: Double = 0.8,
    val srsLadderDays: List<Int> = listOf(3, 7, 14, 30, 60, 120),
    val allowedOrigins: List<String> = listOf("http://localhost:8080", "http://127.0.0.1:8080"),
    val zone: String = "Europe/Berlin",
    val mcpEndpoint: String = "/mcp",
    val docTokenSoftCap: Int = 15000,
    val docChunkChars: Int = 20000,
    val inlineDocChars: Int = 30000,
) {
    val zoneId: ZoneId get() = ZoneId.of(zone)
}
