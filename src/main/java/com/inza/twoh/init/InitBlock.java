package com.inza.twoh.init;

import com.inza.twoh.AddonMain;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public class InitBlock {
    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(ForgeRegistries.BLOCKS, AddonMain.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(ForgeRegistries.ITEMS, AddonMain.MOD_ID);

    public static final RegistryObject<Block> OVER_HEAVEN_FLOOR = BLOCKS.register("over_heaven_floor",
        () -> new Block(AbstractBlock.Properties
            .of(Material.STONE)
            .strength(-1.0F, 3600000.0F)
            .lightLevel(state -> 12)
            .noDrops()));

    public static final RegistryObject<Item> OVER_HEAVEN_FLOOR_ITEM = ITEMS.register("over_heaven_floor",
        () -> new BlockItem(OVER_HEAVEN_FLOOR.get(),
            new Item.Properties().tab(ItemGroup.TAB_BUILDING_BLOCKS)));
}
