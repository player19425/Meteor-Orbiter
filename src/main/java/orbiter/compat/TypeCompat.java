package orbiter.compat;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public final class TypeCompat {
    private TypeCompat() {}

    public static final EntityType<Player> PLAYER = EntityTypes.PLAYER;
    public static final EntityType<CaveSpider> CAVE_SPIDER = EntityTypes.CAVE_SPIDER;
    public static final EntityType<Zombie> ZOMBIE = EntityTypes.ZOMBIE;
    public static final EntityType<AreaEffectCloud> AREA_EFFECT_CLOUD = EntityTypes.AREA_EFFECT_CLOUD;
    public static final EntityType<PrimedTnt> TNT = EntityTypes.TNT;
    public static final EntityType<ItemEntity> ITEM = EntityTypes.ITEM;
    public static final EntityType<ArmorStand> ARMOR_STAND = EntityTypes.ARMOR_STAND;

    public static final BlockEntityType<SignBlockEntity> SIGN = BlockEntityTypes.SIGN;
    public static final BlockEntityType<HangingSignBlockEntity> HANGING_SIGN = BlockEntityTypes.HANGING_SIGN;
}
