package com.example.reviewsystem.config;

import com.example.reviewsystem.service.ScoreShardingService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Configuration
@RequiredArgsConstructor
public class ScoreShardingConfig {

    private final JdbcTemplate jdbcTemplate;
    private final ScoreShardingService scoreShardingService;

    @PostConstruct
    public void init() {
        String currentMonth = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        scoreShardingService.ensureTableExists(currentMonth);
    }
}
