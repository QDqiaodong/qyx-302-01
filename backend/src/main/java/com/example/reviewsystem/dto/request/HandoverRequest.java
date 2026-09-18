package com.example.reviewsystem.dto.request;

import lombok.Data;

/**
 * 点交登记内容。件数不在此处——件数以作品档案为准（作者改动件数会作废点交），
 * 点交单只登记现场核对的两样：完好情况、接收人。
 */
@Data
public class HandoverRequest {

    private String conditionStatus;

    private String receiver;

}
