package me.chrommob.minestore.platforms.bungee;

import cloud.commandframework.bungee.BungeeCommandManager;
import cloud.commandframework.execution.AsynchronousCommandExecutionCoordinator;
import me.chrommob.minestore.common.MineStoreCommon;
import me.chrommob.minestore.common.interfaces.user.AbstractUser;
import me.chrommob.minestore.platforms.bungee.events.PlayerEventListenerBungee;
import me.chrommob.minestore.platforms.bungee.logger.LoggerBungee;
import me.chrommob.minestore.platforms.bungee.scheduler.BungeeScheduler;
import me.chrommob.minestore.platforms.bungee.user.BungeeUserGetter;
import me.chrommob.minestore.platforms.bungee.webCommand.CommandExecuterBungee;
import net.kyori.adventure.platform.bungeecord.BungeeAudiences;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Plugin;
import org.checkerframework.checker.nullness.qual.NonNull;

import java.io.File;
import java.util.function.Function;

@SuppressWarnings("unused")
public class MineStoreBungee extends Plugin {
    private static MineStoreBungee instance;
    private BungeeAudiences adventure;
    private MineStoreCommon common;

    @Override
    public void onEnable() {
        instance = this;
        this.adventure = BungeeAudiences.create(this);
        common = new MineStoreCommon();
        common.setPlatform("bungee");
        common.setPlatformName(getProxy().getName());
        common.setPlatformVersion(getProxy().getVersion());
        common.registerLogger(new LoggerBungee(this));
        common.registerScheduler(new BungeeScheduler(this));
        common.registerUserGetter(new BungeeUserGetter(this));
        common.setConfigLocation(new File(getDataFolder(), "config.yml"));
        common.registerCommandExecuter(new CommandExecuterBungee(this));
        common.registerPlayerJoinListener(new PlayerEventListenerBungee(this));
        Function cToA = commandSender -> new AbstractUser(commandSender instanceof ProxiedPlayer ? ((ProxiedPlayer) commandSender).getUniqueId() : null);
        common.registerCommandManager(new BungeeCommandManager<>(
                /* Owning plugin */ this,
                /* Execution coordinator */ AsynchronousCommandExecutionCoordinator.simpleCoordinator(),
                /* Function to get a command sender from a bukkit command source */ commandSource -> commandSource,
                /* Function to get a command source from a bukkit command sender */ commandSender -> commandSender
        ));
        common.init();
    }

    @Override
    public void onDisable() {
        if (this.adventure != null) {
            this.adventure.close();
            this.adventure = null;
        }
        common.stop();
    }

    public static MineStoreBungee getInstance() {
        return instance;
    }

    public @NonNull BungeeAudiences adventure() {
        if (this.adventure == null) {
            throw new IllegalStateException("Cannot retrieve audience provider while plugin is not enabled");
        }
        return this.adventure;
    }
}
