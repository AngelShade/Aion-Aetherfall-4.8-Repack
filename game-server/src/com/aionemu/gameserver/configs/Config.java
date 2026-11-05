package com.aionemu.gameserver.configs;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aionemu.commons.configs.CommonsConfig;
import com.aionemu.commons.configs.DatabaseConfig;
import com.aionemu.commons.configuration.ConfigurableProcessor;
import com.aionemu.commons.utils.NetworkUtils;
import com.aionemu.commons.utils.PropertiesUtils;
import com.aionemu.commons.utils.ServerAddress;
import com.aionemu.gameserver.GameServerError;
import com.aionemu.gameserver.configs.administration.AdminConfig;
import com.aionemu.gameserver.configs.administration.CommandsConfig;
import com.aionemu.gameserver.configs.main.*;
import com.aionemu.gameserver.configs.network.NetworkConfig;
import com.aionemu.gameserver.configs.network.PffConfig;
import com.aionemu.gameserver.services.event.EventService;

import ch.qos.logback.classic.ClassicConstants;

/**
 * @author Nemesiss, SoulKeeper
 */
public class Config {

	private static final List<Class<?>> CONFIGS = Arrays.asList(AdminConfig.class, CommandsConfig.class, AIConfig.class,
		AutoGroupConfig.class, BrokerMarketConfig.class, CentralMarketSimulationConfig.class, CommonsConfig.class, CleaningConfig.class, CraftConfig.class, CustomConfig.class, DropConfig.class, EventsConfig.class,
		FallDamageConfig.class, GSConfig.class, GeoDataConfig.class, GroupConfig.class, HousingConfig.class, HTMLConfig.class,
		InstanceConfig.class, LegionConfig.class, LoggingConfig.class, MembershipConfig.class, MobScalingConfig.class, NameConfig.class, PeriodicSaveConfig.class,
		PlayerBotConfig.class, PlayerBotEconomyConfig.class, PlayerBotMerchantConfig.class, PlayerBotWardenConfig.class, PlayerBotPopulationConfig.class, PlayerTransferConfig.class, PricesConfig.class, PunishmentConfig.class, RankingConfig.class, RatesConfig.class, SecurityConfig.class,
		ShutdownConfig.class, SiegeConfig.class, ThreadConfig.class, WorldConfig.class, DatabaseConfig.class, NetworkConfig.class, PffConfig.class);

	/**
	 * Load configs of the given classes or all if allowedConfigs is empty.
	 */
	public static void load(Class<?>... allowedConfigs) {
		Properties properties = loadProperties();
		properties.putAll(EventService.getInstance().getActiveEventConfigProperties());
		List<Class<?>> configs = getClasses();
		for (Class<?> config : allowedConfigs) {
			if (!configs.contains(config))
				throw new IllegalArgumentException(config + " is not an allowed config");
		}
		boolean processAllConfigs = allowedConfigs.length == 0;
		Set<String> unusedProperties = ConfigurableProcessor.process(properties, processAllConfigs ? configs.toArray() : allowedConfigs);
		if (processAllConfigs || Arrays.asList(allowedConfigs).contains(MobScalingConfig.class))
			MobScalingConfig.validate();
		if (processAllConfigs && !unusedProperties.isEmpty()) {
			removePropertiesUsedInLogbackXml(unusedProperties);
			unusedProperties.forEach(p -> LoggerFactory.getLogger(Config.class).warn("Config property {} is unknown and therefore ignored.", p));
		}

		if (NetworkConfig.CLIENT_CONNECT_ADDRESS.getAddress().isAnyLocalAddress()) {
			InetAddress localIPv4 = NetworkUtils.findLocalIPv4();
			if (localIPv4 == null)
				throw new GameServerError("No IP for Aion client advertisement configured and local IP discovery failed. Please configure gameserver.network.client.connect_address");
			NetworkConfig.CLIENT_CONNECT_ADDRESS = new InetSocketAddress(localIPv4, NetworkConfig.CLIENT_CONNECT_ADDRESS.getPort());
			LoggerFactory.getLogger(Config.class).info("No IP for Aion client advertisement configured, using {}", localIPv4.getHostAddress());
		}
	}

	private static void removePropertiesUsedInLogbackXml(Set<String> properties) {
		String logbackXml = System.getProperty(ClassicConstants.CONFIG_FILE_PROPERTY);
		if (logbackXml != null) {
			try {
				String logbackXmlContent = Files.readString(Path.of(logbackXml));
				properties.removeIf(property -> logbackXmlContent.contains("${" + property + '}'));
			} catch (IOException e) {
				LoggerFactory.getLogger(Config.class).error("", e);
			}
		}
	}

	private static Properties loadProperties() {
		Properties properties = loadProperties(Path.of("."));
		String host = ServerAddress.apply(properties, "gameserver.network.client.socket_address", "gameserver.network.client.connect_address");
		if (host != null) {
			properties.setProperty("gameserver.marketplace.bind", "0.0.0.0");
			// A private LAN reaches the windows on the interface each client used. Beyond it, they need the shared address.
			if (properties.getProperty("gameserver.web.public_url", "").isBlank() && !privateAddress(host))
				properties.setProperty("gameserver.web.public_url", "http://" + host + ":" + properties.getProperty("gameserver.marketplace.port", "8091"));
		}
		return properties;
	}

	private static boolean privateAddress(String host) {
		try {
			var address = java.net.InetAddress.getByName(host);
			return address.isSiteLocalAddress() || address.isLinkLocalAddress();
		} catch (java.net.UnknownHostException e) {
			return false;
		}
	}

	/** Same startup loading/override order with an explicit root for offline verification. */
	private static Properties loadProperties(Path root) {
		Logger log = LoggerFactory.getLogger(Config.class);
		List<String> defaultsFolders = Arrays.asList("./config/administration", "./config/main", "./config/network");
		Properties defaults = new Properties();
		try {
			for (String configDir : defaultsFolders) {
				log.info("Loading default configuration values from: {}/*", configDir);
				PropertiesUtils.loadFromDirectory(defaults, root.resolve(configDir).toFile(), false);
			}
			// Load only named server configuration files; character metadata is not configuration.
			Path playerbots = root.resolve("./config/playerbots/playerbots.properties");
			if (Files.isRegularFile(playerbots)) {
				log.info("Loading default configuration values from: ./config/playerbots/playerbots.properties");
				defaults.putAll(PropertiesUtils.load(playerbots.toFile(), null));
			}
			Path features = root.resolve("./config/playerbots/features.properties");
			if (Files.isRegularFile(features)) {
				log.info("Loading default configuration values from: ./config/playerbots/features.properties");
				defaults.putAll(PropertiesUtils.load(features.toFile(), null));
			}
			Path navigation = root.resolve("./config/playerbots/navigation.properties");
			if (Files.isRegularFile(navigation)) {
				log.info("Loading default configuration values from: ./config/playerbots/navigation.properties");
				defaults.putAll(PropertiesUtils.load(navigation.toFile(), null));
			}
			Path pvp = root.resolve("./config/playerbots/pvp.properties");
			if (Files.isRegularFile(pvp)) {
				log.info("Loading default configuration values from: ./config/playerbots/pvp.properties");
				defaults.putAll(PropertiesUtils.load(pvp.toFile(), null));
			}
			Path populationManagement = root.resolve("./config/playerbots/population-management.properties");
			if (Files.isRegularFile(populationManagement)) {
				log.info("Loading default configuration values from: ./config/playerbots/population-management.properties");
				defaults.putAll(PropertiesUtils.load(populationManagement.toFile(), null));
			}
			Path roadMerchants = root.resolve("./config/playerbots/road-merchants.properties");
			if (Files.isRegularFile(roadMerchants)) {
				log.info("Loading default configuration values from: ./config/playerbots/road-merchants.properties");
				defaults.putAll(PropertiesUtils.load(roadMerchants.toFile(), null));
			}
			Path roadWardens = root.resolve("./config/playerbots/road-wardens.properties");
			if (Files.isRegularFile(roadWardens)) {
				log.info("Loading default configuration values from: ./config/playerbots/road-wardens.properties");
				defaults.putAll(PropertiesUtils.load(roadWardens.toFile(), null));
			}
			Path economy = root.resolve("./config/playerbots/economy.properties");
			if (Files.isRegularFile(economy)) {
				log.info("Loading default configuration values from: ./config/playerbots/economy.properties");
				defaults.putAll(PropertiesUtils.load(economy.toFile(), null));
			}
			log.info("Loading: ./config/mygs.properties");
			Properties properties = PropertiesUtils.load(root.resolve("./config/mygs.properties").toFile(), defaults);
			if (properties.isEmpty())
				log.info("No override properties found");
			return properties;
		} catch (Exception e) {
			throw new GameServerError("Can't load gameserver configuration:", e);
		}
	}

	public static List<Class<?>> getClasses() {
		return CONFIGS;
	}
}
