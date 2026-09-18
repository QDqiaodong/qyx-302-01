package com.example.reviewsystem.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class PublicationRequest {

    /** 批次名称，可空（后端用默认名称） */
    private String batchName;

    /** 要公示的作品 ID 列表；为空时公示全部未公示的已评分作品 */
    private List<Long> workIds;
}
