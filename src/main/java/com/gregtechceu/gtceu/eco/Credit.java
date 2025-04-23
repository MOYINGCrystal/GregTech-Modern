package com.gregtechceu.gtceu.eco;

import com.gregtechceu.gtceu.common.data.GTItems;
import lombok.Getter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.ArrayList;
import java.util.List;

public enum Credit {
    NEUTRONIUM_CREDIT(10_000_000, GTItems.CREDIT_NEUTRONIUM),
    NAQUADAH_CREDIT(1_000_000, GTItems.CREDIT_NAQUADAH),
    OSMIUM_CREDIT(100_000, GTItems.CREDIT_OSMIUM),
    PLATINUM_CREDIT(10_000, GTItems.CREDIT_PLATINUM),
    GOLD_CREDIT(1_000, GTItems.CREDIT_GOLD),
    SILVER_CREDIT(100, GTItems.CREDIT_SILVER),
    CUPRONICKEL_CREDIT(10, GTItems.CREDIT_CUPRONICKEL),
    COPPER_CREDIT(1, GTItems.CREDIT_COPPER),
    ;

    @Getter
    private final long price;

    @Getter
    private final Item item;

    Credit(long price, ItemLike item) {
        this.price = price;
        this.item = item.asItem();
    }

    public static List<ItemStack> toCredits(long price) {
        List<ItemStack> list = new ArrayList<>();
        for (Credit credit : values()) {
            long creditPrice = credit.getPrice();
            long num = price / creditPrice;
            if (num > 0) {
                list.add(new ItemStack(credit.getItem(), (int)num));
            }
            price -= num * creditPrice;
        }
        return list;
    }
}
