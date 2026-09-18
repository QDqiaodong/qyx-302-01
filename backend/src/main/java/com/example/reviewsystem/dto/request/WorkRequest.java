package com.example.reviewsystem.dto.request;

import lombok.Data;

@Data
public class WorkRequest {

    private String category;
    private String theme;
    private String creatorName;
    private String creatorPhone;
    private String creatorEmail;
    private String workName;
    private String description;
    private String imageUrl;
    private String status;
    /** 件数（作者申报）。已点交后改动件数会作废点交、撤下展墙与参展凭证。 */
    private Integer pieceCount;

}