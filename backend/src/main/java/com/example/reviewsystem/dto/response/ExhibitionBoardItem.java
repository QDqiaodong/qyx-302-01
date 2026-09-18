package com.example.reviewsystem.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 展陈看板条目：只有点交完成（件数、完好情况、接收人三样齐全）的作品
 * 才会出现在这里——看板数据由后端按点交状态过滤，未到场作品连行都不会有。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExhibitionBoardItem {

    private Long workId;

    private String workName;

    private String category;

    private String creatorName;

    /** 点交完成时钉下的件数快照 */
    private Integer pieceCount;

    private String conditionStatus;

    private String receiver;

    private LocalDateTime handoverCompletedAt;

    /** NOT_ISSUED / ISSUED / REVOKED */
    private String certificateStatus;

    private String certificateNo;

}
