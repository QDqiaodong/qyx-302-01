package com.example.reviewsystem.entity;

import java.util.List;

/**
 * 打分维度口径。一件作品必须四维全部有有效分才允许进入综合分计算；
 * 缺任何一维都停在 PENDING_SCORES（待齐分），综合分/等级/统计一律不得按零分凑。
 */
public final class Dimensions {

    public static final String CREATIVITY = "creativity";
    public static final String COMPLETION = "completion";
    public static final String COMMERCIAL_POTENTIAL = "commercial_potential";
    public static final String CRAFTSMANSHIP = "craftsmanship";

    /** 综合分权重也以这套下划线维度名为 key，顺序即综合分加权顺序。 */
    public static final List<String> ALL =
            List.of(CREATIVITY, COMPLETION, COMMERCIAL_POTENTIAL, CRAFTSMANSHIP);

    public static boolean isKnown(String dimension) {
        return ALL.contains(dimension);
    }

    private Dimensions() {
    }
}
