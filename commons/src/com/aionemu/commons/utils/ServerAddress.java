package com.aionemu.commons.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Properties;

import org.slf4j.LoggerFactory;

/**
 * One address for players to reach this server, shared by the login, game and chat servers. The file is created
 * beside their folders on first start, so every install has it and updates never replace it. Blank keeps every
 * server on this PC, as configured.
 */
public final class ServerAddress {

	private static final Path FILE = Path.of("../server-address.properties");
	private static final String KEY = "server.address";
	private static final String TEMPLATE = """
		# Leave this blank to play on this PC.
		# To let other PCs join, enter the address they reach this PC on, for example:
		# server.address = 192.168.1.130
		# Allow TCP 2106, 7777, 10241 and 8091 through the firewall, then restart the servers.
		# Other players enter the same address in their launcher's connection settings.
		server.address =
		""";

	private ServerAddress() {}

	/** @return the IP or hostname players use, or null when this server stays on this PC */
	public static String configured() {
		return configured(FILE);
	}

	static String configured(Path file) {
		if (!Files.isRegularFile(file)) {
			create(file);
			return null;
		}
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

	/** The three servers can start together, and a read-only folder must not stop any of them. */
	private static void create(Path file) {
		try {
			Files.writeString(file, TEMPLATE, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		} catch (IOException e) {
			LoggerFactory.getLogger(ServerAddress.class).debug("Server address file not created", e);
		}
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
