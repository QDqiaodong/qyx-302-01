package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.request.ScoreRequest;
import com.example.reviewsystem.entity.Score;
import com.example.reviewsystem.service.ScoreService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/scores")
@RequiredArgsConstructor
public class ScoreController {

    private final ScoreService scoreService;

    @GetMapping("/work/{workId}")
    public ResponseEntity<List<Score>> getScoresByWorkId(@PathVariable Long workId) {
        List<Score> scores = scoreService.getScoresByWorkId(workId);
        return ResponseEntity.ok(scores);
    }

    /**
     * 提交维度分：body.dimensions 可只带一个或几个维度（例如只交创意分）。
     * 同维已有有效分时整体 409 拒绝；单事务提交，失败不留半截维度分。
     */
    @PostMapping
    public ResponseEntity<List<Score>> submitScore(@RequestBody ScoreRequest request) {
        List<Score> submitted = scoreService.submitScores(
                request.getWorkId(),
                request.getJudgeId(),
                request.getJudgeName(),
                request.getDimensions());
        return ResponseEntity.ok(submitted);
    }

    /** 修改某条维度分的数值（维度本身不可改，同一维只有这一条有效分）。 */
    @PutMapping("/{id}")
    public ResponseEntity<Score> updateScore(@PathVariable Long id, @RequestBody ScoreRequest request) {
        java.math.BigDecimal value = request.getDimensions() == null
                ? null
                : request.getDimensions().values().stream().findFirst().orElse(null);
        Score updated = scoreService.updateScore(id, value);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScore(@PathVariable Long id) {
        scoreService.deleteScore(id);
        return ResponseEntity.noContent().build();
    }

}
