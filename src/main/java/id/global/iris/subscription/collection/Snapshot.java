package id.global.iris.subscription.collection;

public record Snapshot(String eventName, String routingKey, byte[] message) {
}
