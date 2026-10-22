package orbiter.compat;

import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.spider.CaveSpider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HangingSignBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;

public final class TypeCompat {
    private TypeCompat() {}

    public static final EntityType<Player> PLAYER = EntityType.PLAYER;
    public static final EntityType<CaveSpider> CAVE_SPIDER = EntityType.CAVE_SPIDER;
    public static final EntityType<Zombie> ZOMBIE = EntityType.ZOMBIE;
    public static final EntityType<AreaEffectCloud> AREA_EFFECT_CLOUD = EntityType.AREA_EFFECT_CLOUD;
    public static final EntityType<PrimedTnt> TNT = EntityType.TNT;
    public static final EntityType<ItemEntity> ITEM = EntityType.ITEM;
    public static final EntityType<ArmorStand> ARMOR_STAND = EntityType.ARMOR_STAND;

    public static final BlockEntityType<SignBlockEntity> SIGN = BlockEntityType.SIGN;
    public static final BlockEntityType<HangingSignBlockEntity> HANGING_SIGN = BlockEntityType.HANGING_SIGN;
}
