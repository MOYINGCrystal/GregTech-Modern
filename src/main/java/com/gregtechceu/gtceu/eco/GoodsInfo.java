package com.gregtechceu.gtceu.eco;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 商品价格
 */
@Data
@NoArgsConstructor
public class GoodsInfo {
    public GoodsInfo(int goodsId, long price) {
        this.goodsId = goodsId;
        this.price = price;
    }

    private int goodsId;

    /**
     * 价格
     * 商品价格在被商品出售时改变
     */
    private long price;

    private long priceLastChangeTime = 0;

    public void setupPrice(long price) {
        this.price = price;
        priceLastChangeTime = System.currentTimeMillis();
    }
}
