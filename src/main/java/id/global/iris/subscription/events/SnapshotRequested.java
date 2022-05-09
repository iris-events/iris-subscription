package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;

@Message(name = "snapshot-requested")
public record SnapshotRequested(String resourceType, String resourceId) {
}
