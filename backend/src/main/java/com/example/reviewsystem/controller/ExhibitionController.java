package com.example.reviewsystem.controller;

import com.example.reviewsystem.dto.response.ExhibitionBoardItem;
import com.example.reviewsystem.entity.Work;
import com.example.reviewsystem.service.ExhibitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exhibition")
@RequiredArgsConstructor
public class ExhibitionController {

    private final ExhibitionService exhibitionService;

    /** 展陈看板：只含点交完成的作品，未点交/点交中的一律不出现。 */
    @GetMapping("/board")
    public ResponseEntity<List<ExhibitionBoardItem>> getBoard() {
        return ResponseEntity.ok(exhibitionService.getBoard());
    }

    /** 发放参展凭证：点交未完成的作品一律拒绝。 */
    @PostMapping("/works/{workId}/certificate")
    public ResponseEntity<Work> issueCertificate(@PathVariable Long workId) {
        return ResponseEntity.ok(exhibitionService.issueCertificate(workId));
    }

}
