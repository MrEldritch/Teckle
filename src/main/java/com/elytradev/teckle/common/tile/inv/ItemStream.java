package com.elytradev.teckle.common.tile.inv;

import net.minecraft.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Created by darkevilmac on 5/4/17.
 */
public class ItemStream {

    public static Stream<ItemStack> createItemStream(IItemHandler handler) {
        AtomicInteger i = new AtomicInteger(0);
        return Stream.generate(() -> handler.getStackInSlot(i.getAndIncrement())).limit(handler.getSlots());
    }

}
