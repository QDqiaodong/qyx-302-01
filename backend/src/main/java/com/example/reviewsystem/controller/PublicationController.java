package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.request.PublicationRequest;
import com.example.reviewsystem.entity.PublicationBatch;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class PublicationController {

    private final PublicationService publicationService;

    /**
     * 发起对外公示：整批作品在一个事务里按同一配置快照锁定。
     * 任一件不满足条件（已公示 / 未凑齐分）或中途失败，整批回滚为未公示。
     */
    @PostMapping
    public ResponseEntity<PublicationBatch> publish(@RequestBody(required = false) PublicationRequest request) {
        String batchName = request != null ? request.getBatchName() : null;
        List<Long> workIds = request != null ? request.getWorkIds() : null;
        PublicationBatch batch = publicationService.publish(batchName, workIds);
        return ResponseEntity.ok(batch);
    }

    @GetMapping
    public ResponseEntity<List<PublicationBatch>> listBatches() {
        return ResponseEntity.ok(publicationService.listBatches());
    }

    @GetMapping("/{batchId}/works")
    public ResponseEntity<List<Work>> listBatchWorks(@PathVariable Long batchId) {
        return ResponseEntity.ok(publicationService.listBatchWorks(batchId));
    }

    /**
     * 整批撤回：整批作品回到未公示状态（按当前配置恢复即时分），同样不允许半批残留。
     */
    @PostMapping("/{batchId}/revoke")
    public ResponseEntity<PublicationBatch> revoke(@PathVariable Long batchId) {
        return ResponseEntity.ok(publicationService.revokeBatch(batchId));
    }
}
