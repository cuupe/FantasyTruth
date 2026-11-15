package dev.fantasytruth.abyss;

import dev.fantasytruth.FantasyTruth;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class AbyssEntities {
    private static final DeferredRegister<EntityType<?>> TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, FantasyTruth.ID);

    public static final RegistryObject<EntityType<CorpseWeeper>> WEEPER = TYPES.register("corpse_weeper",
            () -> EntityType.Builder.of(CorpseWeeper::new, MobCategory.MONSTER)
                    .sized(1.30F, 2.15F).clientTrackingRange(10).build("stratacubes:corpse_weeper"));

    public static final RegistryObject<EntityType<Demonfish>> DEMONFISH = TYPES.register("demonfish",
            () -> EntityType.Builder.of(Demonfish::new, MobCategory.MONSTER)
                    .sized(0.82F, 0.66F).clientTrackingRange(8).build("stratacubes:demonfish"));

    public static final RegistryObject<EntityType<Hammerbeak>> HAMMERBEAK = TYPES.register("hammerbeak",
            () -> EntityType.Builder.of(Hammerbeak::new, MobCategory.CREATURE)
                    .sized(0.82F, 1.05F).clientTrackingRange(8).build("stratacubes:hammerbeak"));
    public static final RegistryObject<EntityType<Silkfang>> SILKFANG = TYPES.register("silkfang",
            () -> EntityType.Builder.of(Silkfang::new, MobCategory.MONSTER)
                    .sized(1.25F, 0.8F).clientTrackingRange(8).build("stratacubes:silkfang"));
    public static final RegistryObject<EntityType<CrimsonSplitjaw>> SPLITJAW = TYPES.register("crimson_splitjaw",
            () -> EntityType.Builder.of(CrimsonSplitjaw::new, MobCategory.MONSTER)
                    .sized(2.65F, 1.95F).clientTrackingRange(12).build("stratacubes:crimson_splitjaw"));

    public static final RegistryObject<EntityType<Inbyo>> INBYO = TYPES.register("inbyo",
            () -> EntityType.Builder.of(Inbyo::new, MobCategory.MONSTER)
                    .sized(1.45F, 1.60F).clientTrackingRange(8).build("stratacubes:inbyo"));
    public static final RegistryObject<EntityType<Ottobas>> OTTOBAS = TYPES.register("ottobas",
            () -> EntityType.Builder.of(Ottobas::new, MobCategory.CREATURE)
                    .sized(2.55F, 1.98F).clientTrackingRange(10).build("stratacubes:ottobas"));
    public static final RegistryObject<EntityType<Rohana>> ROHANA = TYPES.register("rohana",
            () -> EntityType.Builder.of(Rohana::new, MobCategory.AMBIENT)
                    .sized(0.35F, 0.30F).clientTrackingRange(6).build("stratacubes:rohana"));

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        bus.addListener(AbyssEntities::attributes);
    }

    private static void attributes(EntityAttributeCreationEvent event) {
        event.put(WEEPER.get(), CorpseWeeper.attributes().build());
        event.put(DEMONFISH.get(), Demonfish.attributes().build());
        event.put(HAMMERBEAK.get(), Hammerbeak.attributes().build());
        event.put(SILKFANG.get(), Silkfang.attributes().build());
        event.put(SPLITJAW.get(), CrimsonSplitjaw.attributes().build());
        event.put(INBYO.get(), Inbyo.attributes().build());
        event.put(OTTOBAS.get(), Ottobas.attributes().build());
        event.put(ROHANA.get(), Rohana.attributes().build());
    }

    private AbyssEntities() {}
}
