package org.iris_events.subscription.collection;

public record Snapshot(String eventName, String routingKey, byte[] message) {
}
