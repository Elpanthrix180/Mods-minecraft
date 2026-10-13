package com.robotsconstructores.registry;

import com.robotsconstructores.RobotsConstructores;
import com.robotsconstructores.menu.BuilderRobotMenu;
import com.robotsconstructores.menu.MinerRobotMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, RobotsConstructores.MOD_ID);

    public static final RegistryObject<MenuType<BuilderRobotMenu>> BUILDER_ROBOT =
            MENUS.register("builder_robot", () -> IForgeMenuType.create(BuilderRobotMenu::fromNetwork));

    public static final RegistryObject<MenuType<MinerRobotMenu>> MINER_ROBOT =
            MENUS.register("miner_robot", () -> IForgeMenuType.create(MinerRobotMenu::fromNetwork));

    private ModMenus() {}
}
