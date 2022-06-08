package id.global.iris.subscription.events;

import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.Message;

@Message(name = "snapshot-requested", exchangeType = ExchangeType.TOPIC)
public record SnapshotRequested(String resourceType, String resourceId) {
}
