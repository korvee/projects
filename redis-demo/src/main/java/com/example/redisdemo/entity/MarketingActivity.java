package com.example.redisdemo.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 模拟你实际工作中的"营销活动"这类需要缓存的业务对象。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketingActivity implements Serializable {
    private Long id;
    private String name;
    /** 例如：PRIVATE_WEALTH（私行）/ RETAIL_LOAN（零售贷款） */
    private String type;
    private String discountDesc;
}
