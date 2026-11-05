package com.aionemu.gameserver.model.templates.flypath;

public class FlightPath {

	private final Type type;
	private final int id;
	private int distance;
	private final long startTime = System.currentTimeMillis();

	public FlightPath(Type type, int id, int distance) {
		this.type = type;
		this.id = id;
		this.distance = distance;
	}

	public Type getType() {
		return type;
	}

	public int getId() {
		return id;
	}

	public int getDistance() {
		return distance;
	}

	public void setDistance(int distance) {
		this.distance = distance;
	}

	/** @return Milliseconds since the flight started. */
	public long getElapsedTime() {
		return System.currentTimeMillis() - startTime;
	}

	public enum Type {
		FLIGHT_TRANSPORTER,
		WINDSTREAM
	}
}
