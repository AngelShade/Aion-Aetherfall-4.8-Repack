package com.aionemu.commons.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import org.slf4j.LoggerFactory;

/**
 * One address for players to reach this server, shared by the login, game and chat servers. Blank or missing
 * keeps every server on this PC, as configured.
 */
public final class ServerAddress {

	private static final Path FILE = Path.of("../server-address.properties");
	private static final String KEY = "server.address";

	private ServerAddress() {}

	/** @return the IP or hostname players use, or null when this server stays on this PC */
	public static String configured() {
		return configured(FILE);
	}

	static String configured(Path file) {
		if (!Files.isRegularFile(file))
			return null;
		Properties properties = new Properties();
		try (var in = Files.newBufferedReader(file)) {
			properties.load(in);
		} catch (IOException e) {
			throw new Error("Can't read " + file, e);
		}
		String host = properties.getProperty(KEY, "").trim();
		if (host.isEmpty())
			return null;
		if (!host.matches("[A-Za-z0-9.-]{1,253}"))
			throw new Error(KEY + " must be an IP address or hostname, without a port: " + host);
		return host;
	}

	/** Opens a listener to the network and points clients at the shared address; its port stays as configured. */
	public static String apply(Properties properties, String socketKey, String connectKey) {
		return apply(properties, socketKey, connectKey, configured());
	}

	static String apply(Properties properties, String socketKey, String connectKey, String host) {
		if (host == null)
			return null;
		String socket = properties.getProperty(socketKey, "");
		String port = socket.substring(socket.lastIndexOf(':') + 1);
		if (!port.matches("\\d{1,5}"))
			throw new Error("Can't read the port of " + socketKey + ": " + socket);
		properties.setProperty(socketKey, "0.0.0.0:" + port);
		if (connectKey != null)
			properties.setProperty(connectKey, host + ":" + port);
		LoggerFactory.getLogger(ServerAddress.class).info("Players connect through {}:{}", host, port);
		return host;
	}
}
