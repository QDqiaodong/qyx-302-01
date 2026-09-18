package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.request.HandoverRequest;
import com.example.reviewsystem.entity.Handover;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.service.HandoverService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/works/{workId}/handover")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;

    /** 当前点交单；未点交（还没有单）返回 404。 */
    @GetMapping
    public ResponseEntity<Handover> getCurrent(@PathVariable Long workId) {
        return handoverService.getCurrentHandover(workId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** 开始点交：未点交 → 点交中。 */
    @PostMapping("/start")
    public ResponseEntity<Handover> start(@PathVariable Long workId) {
        return ResponseEntity.ok(handoverService.startHandover(workId));
    }

    /** 点交中登记内容（完好情况、接收人可先记一半）。 */
    @PutMapping
    public ResponseEntity<Handover> updateDraft(@PathVariable Long workId,
                                                @RequestBody HandoverRequest request) {
        return ResponseEntity.ok(
                handoverService.updateDraft(workId, request.getConditionStatus(), request.getReceiver()));
    }

    /** 完成点交：三要素（件数、完好情况、接收人）缺一样整单不成立。 */
    @PostMapping("/complete")
    public ResponseEntity<Handover> complete(@PathVariable Long workId,
                                             @RequestBody(required = false) HandoverRequest request) {
        String conditionStatus = request != null ? request.getConditionStatus() : null;
        String receiver = request != null ? request.getReceiver() : null;
        return ResponseEntity.ok(handoverService.completeHandover(workId, conditionStatus, receiver));
    }

    /** 点交失败：整单作废退回未点交，不留一半件数一半空着的半成品。 */
    @PostMapping("/fail")
    public ResponseEntity<Work> fail(@PathVariable Long workId) {
        return ResponseEntity.ok(handoverService.failHandover(workId));
    }

}
