package io.freshwater.banx.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import io.freshwater.banx.api.FreshwaterBanXProvider;
import io.freshwater.banx.common.Protocol;
import io.freshwater.banx.velocity.api.FreshwaterBanXApiImpl;
import io.freshwater.banx.velocity.command.BanCommand;
import io.freshwater.banx.velocity.command.BanInfoCommand;
import io.freshwater.banx.velocity.command.BanListCommand;
import io.freshwater.banx.velocity.command.FbxCommand;
import io.freshwater.banx.velocity.command.KickCommand;
import io.freshwater.banx.velocity.command.TempBanCommand;
import io.freshwater.banx.velocity.command.UnbanCommand;
import io.freshwater.banx.velocity.config.PluginConfig;
import io.freshwater.banx.velocity.http.HttpApiServer;
import io.freshwater.banx.velocity.listener.LoginListener;
import io.freshwater.banx.velocity.listener.MatrixMessageListener;
import io.freshwater.banx.velocity.punish.MessageRenderer;
import io.freshwater.banx.velocity.punish.PunishmentService;
import io.freshwater.banx.velocity.storage.Database;
import io.freshwater.banx.velocity.sync.BridgeSync;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "freshwaterbanx",
        name = "FreshwaterBanX",
        version = "1.1.0",
        description = "Ban/kick management for Velocity, integrated with the Matrix anti-cheat.",
        authors = {"FreshwaterIsland"}
)
public final class FreshwaterBanX {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private Database database;
    private PunishmentService service;
    private BridgeSync bridgeSync;
    private HttpApiServer httpServer;
    private boolean active;

    @Inject
    public FreshwaterBanX(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        PluginConfig config;
        try {
            config = PluginConfig.load(dataDirectory, logger);
        } catch (Exception e) {
            logger.error("Failed to load config.yml; plugin will be inactive", e);
            return;
        }

        try {
            database = new Database(config.database(), logger);
            database.init();
        } catch (Exception e) {
            logger.error("Failed to connect to MySQL; plugin will be inactive. Check the 'database' section of config.yml", e);
            return;
        }

        MessageRenderer renderer = new MessageRenderer();
        service = new PunishmentService(proxy, database, renderer, logger, config);

        // Plugin message channel from the Paper bridge.
        MinecraftChannelIdentifier channel =
                MinecraftChannelIdentifier.create(Protocol.CHANNEL_NAMESPACE, Protocol.CHANNEL_NAME);
        proxy.getChannelRegistrar().register(channel);

        bridgeSync = new BridgeSync(proxy, service, channel, logger);

        proxy.getEventManager().register(this, new LoginListener(service, logger));
        proxy.getEventManager().register(this, new MatrixMessageListener(this, proxy, service, bridgeSync, logger));

        registerCommands();

        if (config.httpEnabled()) {
            try {
                httpServer = new HttpApiServer(service, config.httpBind(), config.httpPort(),
                        config.httpToken(), logger);
                httpServer.start();
            } catch (Exception e) {
                logger.error("Failed to start HTTP API server", e);
            }
        }

        FreshwaterBanXProvider.register(new FreshwaterBanXApiImpl(service));
        active = true;
        logger.info("FreshwaterBanX enabled.");
    }

    private void registerCommands() {
        CommandManager cm = proxy.getCommandManager();
        register(cm, new BanCommand(proxy, service, this, logger), "ban", "fbxban");
        register(cm, new TempBanCommand(proxy, service, this, logger), "tempban", "fbxtempban");
        register(cm, new KickCommand(proxy, service, this, logger), "kick", "fbxkick");
        register(cm, new UnbanCommand(proxy, service, this, logger), "unban", "fbxunban", "pardon");
        register(cm, new BanListCommand(proxy, service, this, logger), "banlist", "fbxbanlist");
        register(cm, new BanInfoCommand(proxy, service, this, logger), "baninfo", "fbxbaninfo");
        register(cm, new FbxCommand(proxy, service, this, logger, this::reloadConfig), "fbanx", "freshwaterbanx");
    }

    private void register(CommandManager cm, com.velocitypowered.api.command.Command command,
                          String name, String... aliases) {
        CommandMeta meta = cm.metaBuilder(name).aliases(aliases).plugin(this).build();
        cm.register(meta, command);
    }

    /** Re-reads config.yml and swaps it into the running service. DB/HTTP settings require a restart. */
    public void reloadConfig() throws Exception {
        PluginConfig config = PluginConfig.load(dataDirectory, logger);
        if (service != null) {
            service.setConfig(config);
        }
        if (bridgeSync != null) {
            bridgeSync.pushToAll();
        }
        logger.info("FreshwaterBanX configuration reloaded.");
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        FreshwaterBanXProvider.unregister();
        if (httpServer != null) {
            httpServer.stop();
        }
        if (database != null) {
            database.close();
        }
        if (active) {
            logger.info("FreshwaterBanX disabled.");
        }
    }
}
