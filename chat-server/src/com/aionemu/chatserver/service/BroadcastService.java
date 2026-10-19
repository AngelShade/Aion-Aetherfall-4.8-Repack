package com.aionemu.chatserver.service;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

import com.aionemu.chatserver.model.ChatClient;
import com.aionemu.chatserver.model.Race;
import com.aionemu.chatserver.model.channel.ChatChannels;
import com.aionemu.chatserver.model.message.Message;
import com.aionemu.chatserver.network.aion.serverpackets.SM_CHANNEL_MESSAGE;

/**
 * @author ATracer
 */
public class BroadcastService {

	private static final BroadcastService instance = new BroadcastService();
	private final Map<Integer, ChatClient> clients = new ConcurrentHashMap<>();

	public static BroadcastService getInstance() {
		return instance;
	}

	private BroadcastService() {

	}

	public void addClient(ChatClient client) {
		clients.put(client.getClientId(), client);
	}

	public void removeClient(ChatClient client) {
		clients.remove(client.getClientId());
	}

	public void broadcastMessage(Message message) {
		for (ChatClient client : clients.values()) {
			if (client.isInChannel(message.getChannel()))
				sendMessage(client, message);
		}
	}

	public void sendMessage(ChatClient chatClient, Message message) {
		chatClient.getConnection().sendPacket(new SM_CHANNEL_MESSAGE(message));
	}

	/** A packet sender, not a registered account, socket or persistent second bot roster. */
	public static Message botFindGroupMessage(int serverId, int playerId, int raceId, String name, String text) {
		Race race = Race.getById(raceId);
		if (race == null || playerId <= 0 || name == null || !name.matches("[A-Za-z]{2,32}")
			|| text == null || text.isBlank() || text.length() > 256)
			return null;
		var channel = ChatChannels.findLfg(serverId, race);
		if (channel == null)
			return null;
		ChatClient sender = new ChatClient(playerId, new byte[0], "", name, race, (byte) 0);
		// Native MXWrapper formats the identifier as name@server.game (AION FourCC).
		sender.setIdentifier((name + "@" + serverId + "." + 0x4e4f4941).getBytes(StandardCharsets.UTF_16LE));
		return new Message(channel, text.getBytes(StandardCharsets.UTF_16LE), sender);
	}

	public void broadcastBotFindGroup(int serverId, int playerId, int raceId, String name, String text) {
		Message message = botFindGroupMessage(serverId, playerId, raceId, name, text);
		if (message != null)
			broadcastMessage(message);
	}
}
