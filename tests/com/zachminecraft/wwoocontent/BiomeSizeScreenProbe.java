package com.zachminecraft.wwoocontent;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import me.shedaniel.clothconfig2.api.*;
import me.shedaniel.clothconfig2.impl.builders.*;
import me.shedaniel.clothconfig2.gui.entries.*;
import net.minecraft.network.chat.Component;

/** Exercise the real screen's field construction and save callbacks without a window. */
public final class BiomeSizeScreenProbe {
    private static final Map<String, NumberField> fields = new LinkedHashMap<>();
    private static final Map<String, Group> groups = new LinkedHashMap<>();
    private static class NumberField extends DoubleFieldBuilder {
        NumberField(Component name, double value) { super(Component.literal("Reset"), name, value); }
        public DoubleListEntry build() { return null; }
        double current() { return value; }
        boolean invalid(double value) { return errorSupplier.apply(value).isPresent(); }
    }
    private static class Group extends SubCategoryBuilder {
        boolean expanded = true;
        Group(Component name) { super(Component.literal("Reset"), name); }
        public SubCategoryBuilder setExpanded(boolean expanded) {
            this.expanded = expanded;
            return super.setExpanded(expanded);
        }
        public SubCategoryListEntry build() { return null; }
    }
    private static class Description extends TextDescriptionBuilder {
        Description(Component text) { super(Component.literal("Reset"), text, text); }
        public TextListEntry build() { return null; }
    }
    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) {
        var categories = new ArrayList<String>();
        var rootEntries = new ArrayList<Object>();
        boolean[] hideTabs = {false};
        ConfigCategory category = proxy(ConfigCategory.class, (p,m,a) -> {
            if (m.getName().equals("addEntry")) rootEntries.add(a[0]);
            return p;
        });
        ConfigBuilder builder = proxy(ConfigBuilder.class, (p,m,a) -> {
            if (m.getName().equals("getOrCreateCategory")) {
                categories.add(((Component)a[0]).getString()); return category;
            }
            if (m.getName().equals("setAlwaysShowTabs")) { hideTabs[0] = !(Boolean)a[0]; return p; }
            throw new AssertionError(m);
        });
        ConfigEntryBuilder entries = proxy(ConfigEntryBuilder.class, (p,m,a) -> {
            var name = (Component) a[0];
            return switch(m.getName()) {
                case "startDoubleField" -> {
                    var field = new NumberField(name, (Double)a[1]); fields.put(name.getString(),field); yield field;
                }
                case "startSubCategory" -> { var group = new Group(name); groups.put(name.getString(),group); yield group; }
                case "startTextDescription" -> new Description(name);
                default -> throw new AssertionError(m);
            };
        });
        var original = new BiomeSizeConfig(1.5, Map.of("minecraft:pale_garden",2.0,"minecraft:forest",0.5,"test:custom_biome",3.0));
        var updates = new HashMap<String,Object>();
        BiomeSizeScreen.add(builder,entries,original,updates::put);
        check(categories.equals(java.util.List.of("WWOO Config")) && hideTabs[0], "One config page without tabs");
        check(groups.keySet().equals(java.util.Set.of("Biome Size")), "Biome Size must be the only expandable section; no biome categories");
        check(!groups.get("Biome Size").expanded, "Biome Size starts collapsed");
        check(rootEntries.size()==1 && groups.get("Biome Size").size()==BiomeScaleOptions.ALL.size()+3, "All scale fields belong to the same section");
        check(fields.size()==BiomeScaleOptions.ALL.size()+2, "Not every biome has a field");
        var names = new ArrayList<>(fields.keySet());
        check(names.removeFirst().equals("Global Biome Scale"), "Global scale must appear first");
        var sorted = new ArrayList<>(names); sorted.sort(String::compareTo);
        check(names.equals(sorted), "Every biome must appear in one alphabetical list");
        // Check real selection weights rather than trusting only the slider limits.
        // No allowed setting may again overpower selection by millions of units.
        check(BiomeScaleIndex.biomeBias(1.0) == 0.0, "1.0 must leave selection unchanged");
        for (double scale : new double[] {0.25, 0.5, 1.0, 2.0, 4.0, 8.0}) {
            check(Math.abs(BiomeScaleIndex.biomeBias(scale)) <= 375000.001,
                "Biome score can overpower unrelated climates: " + scale);
        }
        check(Math.abs(BiomeScaleIndex.biomeBias(2.0) + 125000) < 0.001, "2.0 response");
        check(Math.abs(BiomeScaleIndex.biomeBias(4.0) + 250000) < 0.001, "4.0 response");
        check(Math.abs(BiomeScaleIndex.biomeBias(8.0) + 375000) < 0.001, "8.0 response");
        check(Math.abs(BiomeScaleIndex.biomeBias(0.5) - 125000) < 0.001, "0.5 shrink");
        check(Math.abs(BiomeScaleIndex.biomeBias(0.25) - 250000) < 0.001, "0.25 shrink");
        check(BiomeScaleIndex.biomeBias(8.0) < BiomeScaleIndex.biomeBias(4.0)
            && BiomeScaleIndex.biomeBias(4.0) < BiomeScaleIndex.biomeBias(2.0),
            "Increasing the setting must still expand biome regions");
        check(fields.get("Global Biome Scale").current()==1.5, "Existing global scale");
        check(fields.get("Pale Garden").current()==2 && fields.get("Forest").current()==0.5, "Independent saved values");
        for (var field : fields.values()) {
            check(field.getDefaultValue().get()==1.0, "Reset default");
            for (double bad : new double[]{Double.NaN,Double.POSITIVE_INFINITY,0,8.01}) check(field.invalid(bad), "Screen accepted invalid scale");
            check(!field.invalid(0.25) && !field.invalid(8), "Valid bounds rejected");
        }
        fields.get("Pale Garden").getSaveConsumer().accept(4.0);
        fields.get("Forest").getSaveConsumer().accept(0.75);
        var saved = new BiomeSizeConfig(original.sizeMultiplier(), (Map<String,Double>)updates.get("biomeScales"));
        check(saved.scale("minecraft:pale_garden")==4 && saved.scale("minecraft:forest")==0.75, "Save lost a selected biome edit");
        check(saved.scale("test:custom_biome")==3 && saved.scale("minecraft:plains")==1 && saved.sizeMultiplier()==1.5, "Save changed untouched settings");
        fields.get("Pale Garden").getSaveConsumer().accept(fields.get("Pale Garden").getDefaultValue().get());
        check(((Map<?,?>)updates.get("biomeScales")).get("minecraft:pale_garden").equals(1.0), "Reset not saved");
        System.out.println("BIOME_SIZE_SCREEN_PASS fields="+fields.size()+" groups="+groups.size()+" save_and_reset=pass");
    }
}
