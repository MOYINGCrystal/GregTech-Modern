package com.gregtechceu.gtceu.eco;

import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

public class Prices {
    private static final PriceSystem priceSystem = PriceSystem.INSTANCE;

    public static void init() {
        set(Items.GOLD_INGOT, 256);
        set(Items.IRON_INGOT, 10);
        set(GTItems.COIN_DOGE, 1000);
    }

    private static void set(ItemLike item, long price) {
        priceSystem.setBasePrices(item, price);
    }
}
