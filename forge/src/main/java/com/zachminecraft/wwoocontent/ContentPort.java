package com.zachminecraft.wwoocontent;

import com.google.gson.*;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraftforge.registries.RegisterEvent;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.nbt.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.providers.number.*;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.blockpredicates.*;
import net.minecraft.world.level.levelgen.feature.*;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.*;
import net.minecraft.world.level.levelgen.feature.treedecorators.*;
import net.minecraft.world.level.levelgen.placement.*;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.templatesystem.*;
import java.util.*;
import java.util.stream.Stream;

/** Only codecs needed by the selected WWOO 3.0.1 content; no vanilla registry overrides. */
public final class ContentPort  {
    private static final String NS = "wwoocontent";
    private static final ThreadLocal<WorldGenLevel> WORLD = new ThreadLocal<>();
    private static BlockStateProviderType<RuleProvider> RULE_TYPE;
    private static TreeDecoratorType<GroundDecorator> GROUND_TYPE;
    private static BlockPredicateType<BiomePredicate> BIOME_TYPE;
    private static BlockPredicateType<HeightPredicate> HEIGHT_TYPE;
    private static BlockPredicateType<VolumePredicate> VOLUME_TYPE;
    private static BlockPredicateType<ChancePredicate> CHANCE_TYPE;
    private static PlacementModifierType<OffsetPlacement> OFFSET_TYPE;
    private static PlacementModifierType<CountPlacement> COUNT_TYPE;
    private static PlacementModifierType<SelectedPlacement> SELECTED_TYPE;
    private static LootNumberProviderType WEIGHTED_NUMBER_TYPE;
    private static PlacementModifierType<CuboidPlacement> CUBOID_TYPE;
    private static BlockPredicateType<UnobstructedPredicate> UNOBSTRUCTED_TYPE;
    private static final Codec<JsonObject> JSON = Codec.PASSTHROUGH.xmap(
        d -> d.convert(JsonOps.INSTANCE).getValue().getAsJsonObject(), j -> new Dynamic<>(JsonOps.INSTANCE, j));

    public static void register(RegisterEvent event) {
        event.register(Registries.FEATURE, id("context"), () -> new ContextFeature());
        event.register(Registries.FEATURE, id("overlay"), () -> new MultiFeature(false));
        event.register(Registries.FEATURE, id("sequence"), () -> new MultiFeature(true));
        event.register(Registries.FEATURE, id("template"), () -> new TemplateFeature());
        event.register(Registries.FEATURE, id("spike"), () -> new SpikeFeature());
        event.register(Registries.FEATURE, id("simple_block"), () -> new ModernSimpleBlockFeature());
        if (event.getRegistryKey().equals(Registries.BLOCK_STATE_PROVIDER_TYPE)) { ModernNoiseProvider.TYPE = makeType(BlockStateProviderType.class, ModernNoiseProvider.MAP.codec()); event.register(Registries.BLOCK_STATE_PROVIDER_TYPE, id("modern_noise"), () -> ModernNoiseProvider.TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_STATE_PROVIDER_TYPE)) { RULE_TYPE = makeType(BlockStateProviderType.class, RuleProvider.MAP.codec()); event.register(Registries.BLOCK_STATE_PROVIDER_TYPE, id("rule_based"), () -> RULE_TYPE); }
        if (event.getRegistryKey().equals(Registries.TREE_DECORATOR_TYPE)) { GROUND_TYPE = makeType(TreeDecoratorType.class, GroundDecorator.MAP.codec()); event.register(Registries.TREE_DECORATOR_TYPE, id("place_on_ground"), () -> GROUND_TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_PREDICATE_TYPE)) { BIOME_TYPE = () -> BiomePredicate.MAP.codec(); event.register(Registries.BLOCK_PREDICATE_TYPE, id("matching_biomes"), () -> BIOME_TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_PREDICATE_TYPE)) { HEIGHT_TYPE = () -> HeightPredicate.MAP.codec(); event.register(Registries.BLOCK_PREDICATE_TYPE, id("height_range"), () -> HEIGHT_TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_PREDICATE_TYPE)) { VOLUME_TYPE = () -> VolumePredicate.MAP.codec(); event.register(Registries.BLOCK_PREDICATE_TYPE, id("volume_match"), () -> VOLUME_TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_PREDICATE_TYPE)) { CHANCE_TYPE = () -> ChancePredicate.MAP.codec(); event.register(Registries.BLOCK_PREDICATE_TYPE, id("random_chance"), () -> CHANCE_TYPE); }
        if (event.getRegistryKey().equals(Registries.PLACEMENT_MODIFIER_TYPE)) { OFFSET_TYPE = () -> OffsetPlacement.MAP.codec(); event.register(Registries.PLACEMENT_MODIFIER_TYPE, id("offset"), () -> OFFSET_TYPE); }
        if (event.getRegistryKey().equals(Registries.PLACEMENT_MODIFIER_TYPE)) { CUBOID_TYPE = () -> CuboidPlacement.MAP.codec(); event.register(Registries.PLACEMENT_MODIFIER_TYPE, id("cuboid"), () -> CUBOID_TYPE); }
        if (event.getRegistryKey().equals(Registries.BLOCK_PREDICATE_TYPE)) { UNOBSTRUCTED_TYPE = () -> UnobstructedPredicate.MAP.codec(); event.register(Registries.BLOCK_PREDICATE_TYPE, id("unobstructed"), () -> UNOBSTRUCTED_TYPE); }
        if (event.getRegistryKey().equals(Registries.PLACEMENT_MODIFIER_TYPE)) { COUNT_TYPE = () -> CountPlacement.MAP.codec(); event.register(Registries.PLACEMENT_MODIFIER_TYPE, id("count"), () -> COUNT_TYPE); }
        if (event.getRegistryKey().equals(Registries.PLACEMENT_MODIFIER_TYPE)) { SELECTED_TYPE = () -> SelectedPlacement.MAP.codec(); event.register(Registries.PLACEMENT_MODIFIER_TYPE, id("randomly_selected"), () -> SELECTED_TYPE); }
        if (event.getRegistryKey().equals(Registries.LOOT_NUMBER_PROVIDER_TYPE)) { WEIGHTED_NUMBER_TYPE = new LootNumberProviderType(new WeightedNumber.Serializer()); event.register(Registries.LOOT_NUMBER_PROVIDER_TYPE, id("weighted_list"), () -> WEIGHTED_NUMBER_TYPE); }
    }
    private static ResourceLocation id(String name) { return new ResourceLocation(NS, name); }
    @SuppressWarnings("unchecked") private static <T> T makeType(Class<?> type, Codec<?> codec) {
        try { var c = type.getDeclaredConstructor(Codec.class); c.setAccessible(true); return (T)c.newInstance(codec); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Cannot register WWOO content codec", e); }
    }

    public record ContextConfig(Holder<ConfiguredFeature<?, ?>> feature) implements FeatureConfiguration {
        static final Codec<ContextConfig> CODEC = ConfiguredFeature.CODEC.fieldOf("feature").xmap(ContextConfig::new, ContextConfig::feature).codec();
        @Override public Stream<ConfiguredFeature<?, ?>> getFeatures() { return feature.value().getFeatures(); }
    }
    private static final class ContextFeature extends Feature<ContextConfig> {
        ContextFeature() { super(ContextConfig.CODEC); }
        @Override public boolean place(FeaturePlaceContext<ContextConfig> c) {
            var previous = WORLD.get(); WORLD.set(c.level());
            try { return c.config().feature.value().place(c.level(), c.chunkGenerator(), c.random(), c.origin()); }
            finally { if (previous == null) WORLD.remove(); else WORLD.set(previous); }
        }
    }
    public record MultiConfig(List<Holder<PlacedFeature>> features) implements FeatureConfiguration {
        static final Codec<MultiConfig> CODEC = PlacedFeature.CODEC.listOf().fieldOf("features").xmap(MultiConfig::new, MultiConfig::features).codec();
        @Override public Stream<ConfiguredFeature<?, ?>> getFeatures() { return features.stream().flatMap(f -> f.value().getFeatures()); }
    }
    private static final class MultiFeature extends Feature<MultiConfig> {
        private final boolean sequence;
        MultiFeature(boolean sequence) { super(MultiConfig.CODEC); this.sequence = sequence; }
        @Override public boolean place(FeaturePlaceContext<MultiConfig> c) {
            boolean any = false;
            for (var f : c.config().features) {
                boolean placed = f.value().place(c.level(), c.chunkGenerator(), c.random(), c.origin());
                if (sequence && !placed) return false;
                any |= placed;
            }
            return sequence || any;
        }
    }
    public record TemplateConfig(JsonObject data) implements FeatureConfiguration {
        static final Codec<TemplateConfig> CODEC = JSON.xmap(TemplateConfig::new, TemplateConfig::data);
    }
    private static final class TemplateFeature extends Feature<TemplateConfig> {
        TemplateFeature() { super(TemplateConfig.CODEC); }
        @Override public boolean place(FeaturePlaceContext<TemplateConfig> c) {
            var templates = c.config().data.getAsJsonArray("templates");
            int total = 0;
            for (var t : templates) total += t.getAsJsonObject().get("weight").getAsInt();
            int choice = c.random().nextInt(total); JsonObject selected = null;
            for (var t : templates) { var o = t.getAsJsonObject(); choice -= o.get("weight").getAsInt(); if (choice < 0) { selected = o.getAsJsonObject("data"); break; } }
            var template = c.level().getLevel().getStructureManager().get(new ResourceLocation(selected.get("id").getAsString()));
            if (template.isEmpty()) throw new IllegalStateException("Missing WWOO template: " + selected.get("id"));
            Rotation rotation;
            if (selected.has("rotations")) {
                var rs = selected.getAsJsonArray("rotations"); String r = rs.get(c.random().nextInt(rs.size())).getAsString();
                rotation = r.equals("180") ? Rotation.CLOCKWISE_180 : Rotation.valueOf(r.toUpperCase(Locale.ROOT));
            } else rotation = Rotation.values()[c.random().nextInt(Rotation.values().length)];
            var size=template.get().getSize();
            var pos=c.origin().relative(rotation.rotate(Direction.WEST),size.getX()/2).relative(rotation.rotate(Direction.NORTH),size.getZ()/2);
            var settings = new StructurePlaceSettings().setRotation(rotation).setRandom(c.random());
            if(c.config().data.has("processors")) {
                var processors=StructureProcessorType.LIST_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE,c.level().registryAccess()),c.config().data.get("processors")).getOrThrow(false, message -> { throw new IllegalStateException(message); });
                for(var processor:processors.value().list()) settings.addProcessor(processor);
            }
            settings.setIgnoreEntities(true);
            boolean placed=template.get().placeInWorld(c.level(), pos, pos, settings, c.random(), 3);
            var saved=template.get().save(new CompoundTag());
            for(var tag:saved.getList("entities",Tag.TAG_COMPOUND)) {
                var row=(CompoundTag)tag;var nbt=row.getCompound("nbt").copy();var coords=row.getList("pos",Tag.TAG_DOUBLE);
                var relative=new Vec3(coords.getDouble(0),coords.getDouble(1),coords.getDouble(2));
                var transformed=StructureTemplate.transform(relative,Mirror.NONE,rotation,BlockPos.ZERO).add(pos.getX(),pos.getY(),pos.getZ());
                var xyz=new ListTag();xyz.add(DoubleTag.valueOf(transformed.x));xyz.add(DoubleTag.valueOf(transformed.y));xyz.add(DoubleTag.valueOf(transformed.z));nbt.put("Pos",xyz);nbt.remove("UUID");
                if(nbt.getString("id").equals("minecraft:cushion")) {
                    nbt.putInt("TileX",Mth.floor(transformed.x));nbt.putInt("TileY",Mth.floor(transformed.y));nbt.putInt("TileZ",Mth.floor(transformed.z));
                }
                var entity=EntityType.create(nbt,c.level().getLevel());
                entity.ifPresent(e -> { e.moveTo(transformed.x,transformed.y,transformed.z,e.rotate(rotation),e.getXRot());if (!(e instanceof Mob) || !PaleGardenSpawning.blocked(c.level(),e.blockPosition(),e.getType())) c.level().addFreshEntityWithPassengers(e); });
            }
            return placed || !saved.getList("entities",Tag.TAG_COMPOUND).isEmpty();
        }
    }

    public record SimpleConfig(BlockStateProvider provider, boolean scheduleTick) implements FeatureConfiguration {
        static final Codec<SimpleConfig> CODEC=RecordCodecBuilder.create(i -> i.group(
            BlockStateProvider.CODEC.fieldOf("to_place").forGetter(SimpleConfig::provider),
            Codec.BOOL.optionalFieldOf("schedule_tick",false).forGetter(SimpleConfig::scheduleTick)).apply(i,SimpleConfig::new));
    }
    private static final class ModernSimpleBlockFeature extends Feature<SimpleConfig> {
        ModernSimpleBlockFeature() { super(SimpleConfig.CODEC); }
        @Override public boolean place(FeaturePlaceContext<SimpleConfig> c) {
            var level=c.level();var pos=c.origin();
            var state=RuleProvider.optional(c.config().provider,c.random(),pos,level);
            if(state==null || !state.canSurvive(level,pos)) return false;
            if(state.getBlock() instanceof DoublePlantBlock) {
                var above=level.getBlockState(pos.above());
                if(!above.isAir() && (!Objects.equals(state.getFluidState(),above.getFluidState()) || !above.canBeReplaced())) return false;
                DoublePlantBlock.placeAt(level,state,pos,2);
            } else if(state.getBlock().getClass().getName().equals("com.blackgear.vanillabackport.common.level.blocks.MossyCarpetBlock")) {
                try {
                    state.getBlock().getClass().getMethod("placeAt",net.minecraft.world.level.LevelAccessor.class,BlockPos.class,RandomSource.class,int.class)
                        .invoke(null,level,pos,level.getRandom(),2);
                } catch(ReflectiveOperationException e) { throw new IllegalStateException("Cannot place VanillaBackport moss carpet",e); }
            } else level.setBlock(pos,state,2);
            if(c.config().scheduleTick) level.scheduleTick(pos,level.getBlockState(pos).getBlock(),1);
            return true;
        }
    }

    public record Rule(BlockPredicate predicate, BlockStateProvider provider) {
        static final Codec<Rule> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPredicate.CODEC.fieldOf("if_true").forGetter(Rule::predicate),
            BlockStateProvider.CODEC.fieldOf("then").forGetter(Rule::provider)).apply(i, Rule::new));
    }
    public static final class RuleProvider extends BlockStateProvider {
        static final MapCodec<RuleProvider> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            Rule.CODEC.listOf().fieldOf("rules").forGetter(p -> p.rules),
            BlockStateProvider.CODEC.optionalFieldOf("fallback").forGetter(p -> p.fallback)).apply(i, RuleProvider::new));
        private final List<Rule> rules; private final Optional<BlockStateProvider> fallback;
        RuleProvider(List<Rule> rules, Optional<BlockStateProvider> fallback) { this.rules = rules; this.fallback = fallback; }
        @Override protected BlockStateProviderType<?> type() { return RULE_TYPE; }
        @Override public BlockState getState(RandomSource random, BlockPos pos) {
            var world = WORLD.get();
            if (world == null) throw new IllegalStateException("WWOO rule provider requires content context");
            BlockState result = optionalState(random, pos, world);
            return result == null ? world.getBlockState(pos) : result;
        }
        private BlockState optionalState(RandomSource random, BlockPos pos, WorldGenLevel world) {
            for (var r : rules) {
                if (!r.predicate.test(world, pos)) continue;
                BlockState result = optional(r.provider, random, pos, world);
                if (result != null) return result;
            }
            return fallback.map(p -> optional(p, random, pos, world)).orElse(null);
        }
        private static BlockState optional(BlockStateProvider provider, RandomSource random, BlockPos pos, WorldGenLevel world) {
            return provider instanceof RuleProvider rules ? rules.optionalState(random, pos, world) : provider.getState(random, pos);
        }
    }
    public record BiomePredicate(List<String> biomes) implements BlockPredicate {
        static final MapCodec<BiomePredicate> MAP = Codec.STRING.listOf().fieldOf("biomes").xmap(BiomePredicate::new, BiomePredicate::biomes);
        @Override public BlockPredicateType<?> type() { return BIOME_TYPE; }
        @Override public boolean test(WorldGenLevel level, BlockPos pos) {
            var biome=level.getBiome(pos);
            for(String id:biomes) { if(id.startsWith("#") ? biome.is(TagKey.create(Registries.BIOME,new ResourceLocation(id.substring(1)))) : biome.is(new ResourceLocation(id))) return true; }
            return false;
        }
    }
    public record HeightPredicate(JsonObject min, JsonObject max) implements BlockPredicate {
        static final MapCodec<HeightPredicate> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            JSON.fieldOf("min_inclusive").forGetter(HeightPredicate::min), JSON.fieldOf("max_inclusive").forGetter(HeightPredicate::max)).apply(i, HeightPredicate::new));
        @Override public BlockPredicateType<?> type() { return HEIGHT_TYPE; }
        private static int resolve(JsonObject a, WorldGenLevel w) {
            if (a.has("absolute")) return a.get("absolute").getAsInt();
            if (a.has("above_bottom")) return w.getMinBuildHeight() + a.get("above_bottom").getAsInt();
            return w.getMaxBuildHeight() - 1 - a.get("below_top").getAsInt();
        }
        @Override public boolean test(WorldGenLevel level, BlockPos pos) { return pos.getY() >= resolve(min, level) && pos.getY() <= resolve(max, level); }
    }
    public record VolumePredicate(BlockPos min, BlockPos max, BlockPredicate match) implements BlockPredicate {
        static final MapCodec<VolumePredicate> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockPos.CODEC.fieldOf("min").forGetter(VolumePredicate::min), BlockPos.CODEC.fieldOf("max").forGetter(VolumePredicate::max),
            BlockPredicate.CODEC.fieldOf("match").forGetter(VolumePredicate::match)).apply(i, VolumePredicate::new));
        @Override public BlockPredicateType<?> type() { return VOLUME_TYPE; }
        @Override public boolean test(WorldGenLevel level, BlockPos pos) {
            for (int x=min.getX();x<=max.getX();x++) for (int z=min.getZ();z<=max.getZ();z++) for (int y=min.getY();y<=max.getY();y++) if (!match.test(level,pos.offset(x,y,z))) return false;
            return true;
        }
    }
    public record ChancePredicate(float chance) implements BlockPredicate {
        static final MapCodec<ChancePredicate> MAP = Codec.floatRange(0,1).fieldOf("chance").xmap(ChancePredicate::new, ChancePredicate::chance);
        @Override public BlockPredicateType<?> type() { return CHANCE_TYPE; }
        @Override public boolean test(WorldGenLevel level, BlockPos pos) { return level.getRandom().nextFloat() < chance; }
    }
    public static final class OffsetPlacement extends PlacementModifier {
        static final MapCodec<OffsetPlacement> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProvider.CODEC.fieldOf("x").forGetter(p -> p.x), IntProvider.CODEC.fieldOf("y").forGetter(p -> p.y),
            IntProvider.CODEC.fieldOf("z").forGetter(p -> p.z)).apply(i, OffsetPlacement::new));
        private final IntProvider x,y,z;
        OffsetPlacement(IntProvider x, IntProvider y, IntProvider z) { this.x=x; this.y=y; this.z=z; }
        @Override public PlacementModifierType<?> type() { return OFFSET_TYPE; }
        @Override public Stream<BlockPos> getPositions(PlacementContext c, RandomSource r, BlockPos p) { return Stream.of(p.offset(x.sample(r), y.sample(r), z.sample(r))); }
    }
    public record UnobstructedPredicate(BlockPos offset) implements BlockPredicate {
        static final MapCodec<UnobstructedPredicate> MAP = BlockPos.CODEC.optionalFieldOf("offset",BlockPos.ZERO).xmap(UnobstructedPredicate::new,UnobstructedPredicate::offset);
        @Override public BlockPredicateType<?> type() { return UNOBSTRUCTED_TYPE; }
        @Override public boolean test(WorldGenLevel level,BlockPos pos) { return level.isUnobstructed(null,Shapes.block().move(pos.getX(),pos.getY(),pos.getZ())); }
    }
    public static final class CountPlacement extends PlacementModifier {
        static final MapCodec<CountPlacement> MAP=IntProvider.CODEC.fieldOf("count").xmap(CountPlacement::new,p -> p.count);
        private final IntProvider count;
        CountPlacement(IntProvider count) { this.count=count; }
        @Override public PlacementModifierType<?> type() { return COUNT_TYPE; }
        @Override public Stream<BlockPos> getPositions(PlacementContext c,RandomSource r,BlockPos pos) { return java.util.stream.IntStream.range(0,count.sample(r)).mapToObj(i -> pos); }
    }
    public static final class SelectedPlacement extends PlacementModifier {
        static final MapCodec<SelectedPlacement> MAP=PlacementModifier.CODEC.listOf().fieldOf("placements").xmap(SelectedPlacement::new,p -> p.placements);
        private final List<PlacementModifier> placements;
        SelectedPlacement(List<PlacementModifier> placements) { this.placements=placements; }
        @Override public PlacementModifierType<?> type() { return SELECTED_TYPE; }
        @Override public Stream<BlockPos> getPositions(PlacementContext c,RandomSource r,BlockPos pos) { return placements.get(r.nextInt(placements.size())).getPositions(c,r,pos); }
    }
    public record WeightedNumber(JsonObject data) implements NumberProvider {
        public static final class Serializer implements net.minecraft.world.level.storage.loot.Serializer<WeightedNumber> {
            public void serialize(JsonObject json, WeightedNumber value, JsonSerializationContext context) { json.add("distribution_data", value.data()); }
            public WeightedNumber deserialize(JsonObject json, JsonDeserializationContext context) { return new WeightedNumber(json.getAsJsonObject("distribution_data")); }
        }
        @Override public LootNumberProviderType getType() { return WEIGHTED_NUMBER_TYPE; }
        @Override public float getFloat(LootContext c) {
            var rows=data.getAsJsonArray("distribution");int total=0;for(var row:rows)total+=row.getAsJsonObject().get("weight").getAsInt();
            int choice=c.getRandom().nextInt(total);
            for(var row:rows) { var r=row.getAsJsonObject();choice-=r.get("weight").getAsInt();if(choice<0)return r.get("data").getAsFloat(); }
            throw new IllegalStateException("Empty weighted number distribution");
        }
    }
    public static final class CuboidPlacement extends PlacementModifier {
        static final MapCodec<CuboidPlacement> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            IntProvider.CODEC.fieldOf("xz_size").forGetter(p -> p.xz),IntProvider.CODEC.fieldOf("y_size").forGetter(p -> p.y),
            Codec.BOOL.optionalFieldOf("include_edges",true).forGetter(p -> p.edges),Codec.BOOL.optionalFieldOf("include_interior",true).forGetter(p -> p.interior)).apply(i,CuboidPlacement::new));
        private final IntProvider xz,y; private final boolean edges,interior;
        CuboidPlacement(IntProvider xz,IntProvider y,boolean edges,boolean interior) { this.xz=xz;this.y=y;this.edges=edges;this.interior=interior; }
        @Override public PlacementModifierType<?> type() { return CUBOID_TYPE; }
        @Override public Stream<BlockPos> getPositions(PlacementContext c,RandomSource r,BlockPos origin) {
            int h=y.sample(r),w=xz.sample(r),l=xz.sample(r);
            var result=Stream.<BlockPos>builder();
            for(int x=0;x<=w;x++) for(int y=0;y<=h;y++) for(int z=0;z<=l;z++) {
                if ((edges || x!=0 && x!=w || y!=0 && y!=h) && (edges || z!=0 && z!=l || y!=0 && y!=h)
                    && (edges || x!=0 && x!=w || z!=0 && z!=l) && (interior || x==0 || x==w || y==0 || y==h || z==0 || z==l)) result.add(origin.offset(x,y,z));
            }
            return result.build();
        }
    }
    public record SpikeConfig(BlockState state,BlockPredicate on,BlockPredicate replace) implements FeatureConfiguration {
        static final Codec<SpikeConfig> CODEC = RecordCodecBuilder.create(i -> i.group(BlockState.CODEC.fieldOf("state").forGetter(SpikeConfig::state),BlockPredicate.CODEC.fieldOf("can_place_on").forGetter(SpikeConfig::on),BlockPredicate.CODEC.fieldOf("can_replace").forGetter(SpikeConfig::replace)).apply(i,SpikeConfig::new));
    }
    private static final class SpikeFeature extends Feature<SpikeConfig> {
        SpikeFeature() { super(SpikeConfig.CODEC); }
        @Override public boolean place(FeaturePlaceContext<SpikeConfig> c) {
            var level=c.level();var r=c.random();var origin=c.origin();var cfg=c.config();
            while(level.isEmptyBlock(origin) && origin.getY()>level.getMinBuildHeight()+2) origin=origin.below();
            if(!cfg.on.test(level,origin)) return false;
            origin=origin.above(r.nextInt(4));int height=r.nextInt(4)+7,width=height/4+r.nextInt(2);
            if(width>1 && r.nextInt(60)==0) origin=origin.above(10+r.nextInt(30));
            for(int y=0;y<height;y++) {
                float scale=(1F-(float)y/height)*width;int nw=Mth.ceil(scale);
                for(int x=-nw;x<=nw;x++) for(int z=-nw;z<=nw;z++) {
                    float dx=Math.abs(x)-.25F,dz=Math.abs(z)-.25F;
                    if((x!=0 || z!=0) && dx*dx+dz*dz>scale*scale || (x==-nw || x==nw || z==-nw || z==nw) && r.nextFloat()>.75F) continue;
                    var p=origin.offset(x,y,z);
                    if(level.getBlockState(p).isAir() || cfg.replace.test(level,p)) setBlock(level,p,cfg.state);
                    if(y!=0 && nw>1) { p=origin.offset(x,-y,z);if(level.getBlockState(p).isAir() || cfg.replace.test(level,p)) setBlock(level,p,cfg.state); }
                }
            }
            int pw=Mth.clamp(width-1,0,1);
            for(int x=-pw;x<=pw;x++) for(int z=-pw;z<=pw;z++) {
                var p=origin.offset(x,-1,z);int run=Math.abs(x)==1 && Math.abs(z)==1 ? r.nextInt(5):50;
                while(p.getY()>50 && (level.getBlockState(p).isAir() || cfg.replace.test(level,p) || level.getBlockState(p)==cfg.state)) {
                    setBlock(level,p,cfg.state);p=p.below();if(--run<=0) { p=p.below(r.nextInt(5)+1);run=r.nextInt(5); }
                }
            }
            return true;
        }
    }
    public static final class GroundDecorator extends TreeDecorator {
        static final MapCodec<GroundDecorator> MAP = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("tries",128).forGetter(p -> p.tries), Codec.INT.optionalFieldOf("radius",2).forGetter(p -> p.radius),
            Codec.INT.optionalFieldOf("height",1).forGetter(p -> p.height), BlockStateProvider.CODEC.fieldOf("block_state_provider").forGetter(p -> p.provider)).apply(i, GroundDecorator::new));
        private final int tries,radius,height; private final BlockStateProvider provider;
        GroundDecorator(int tries,int radius,int height,BlockStateProvider provider){this.tries=tries;this.radius=radius;this.height=height;this.provider=provider;}
        @Override protected TreeDecoratorType<?> type() { return GROUND_TYPE; }
        @Override public void place(TreeDecorator.Context c) {
            var positions = Stream.concat(c.logs().stream(),c.roots().stream()).toList();
            if (positions.isEmpty()) return;
            int bottom = positions.stream().mapToInt(BlockPos::getY).min().orElseThrow();
            var base = positions.stream().filter(p -> p.getY() == bottom).toList();
            int minX=base.stream().mapToInt(BlockPos::getX).min().orElseThrow()-radius;
            int maxX=base.stream().mapToInt(BlockPos::getX).max().orElseThrow()+radius;
            int minZ=base.stream().mapToInt(BlockPos::getZ).min().orElseThrow()-radius;
            int maxZ=base.stream().mapToInt(BlockPos::getZ).max().orElseThrow()+radius;
            var world = WORLD.get();
            for (int n=0;n<tries;n++) {
                var pos=new BlockPos(minX+c.random().nextInt(maxX-minX+1),bottom-height+c.random().nextInt(2*height+1),minZ+c.random().nextInt(maxZ-minZ+1));
                var above=pos.above();
                if ((world.getBlockState(above).isAir() || world.getBlockState(above).is(Blocks.VINE)) && world.getBlockState(pos).isSolidRender(world,pos)
                    && world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos).getY()<=above.getY()) {
                    c.setBlock(above,provider.getState(c.random(),above));
                }
            }
        }
    }
}

