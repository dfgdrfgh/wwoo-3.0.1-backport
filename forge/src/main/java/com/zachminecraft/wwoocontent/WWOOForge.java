package com.zachminecraft.wwoocontent;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.resource.PathPackResources;

/** Native Forge entry point, registry lifecycle and bundled-pack selection. */
@Mod("wwoo")
public final class WWOOForge {
    private static final ForgeConfigSpec CONFIG;
    private static final ForgeConfigSpec.BooleanValue REMOVE_PATCHES;
    static {
        var builder = new ForgeConfigSpec.Builder();
        REMOVE_PATCHES = builder.comment("Remove underground andesite, diorite, granite, gravel and dirt patches. Requires restart.")
            .define("removeOres", true);
        CONFIG = builder.build();
    }
    public WWOOForge() {
        var bus = FMLJavaModLoadingContext.get().getModEventBus();
        bus.addListener(ContentPort::register);
        bus.addListener(WWOOForge::packs);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CONFIG, "wwoo/config.toml");
    }
    private static void packs(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        addPack(event, "wwoo_main", "WWOO 3.0.1", true);
        if (ModList.get().isLoaded("vanillabackport"))
            addPack(event, "vanilla_backport_compat", "WWOO Vanilla Backport compatibility", true);
        if (REMOVE_PATCHES.get()) addPack(event, "wwoo_remove_ores", "WWOO Remove Underground Patches", true);
    }
    private static void addPack(AddPackFindersEvent event, String path, String title, boolean required) {
        var root = ModList.get().getModFileById("wwoo").getFile().findResource("resources", path);
        event.addRepositorySource(consumer -> {
            var pack = Pack.readMetaAndCreate("wwoo:" + path, Component.literal(title), required,
                id -> new PathPackResources(id, root, false), PackType.SERVER_DATA, Pack.Position.TOP, PackSource.BUILT_IN);
            if (pack == null) throw new IllegalStateException("Invalid bundled WWOO pack " + path);
            consumer.accept(pack);
        });
    }
}
