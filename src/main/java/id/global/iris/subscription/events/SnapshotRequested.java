package id.global.iris.subscription.events;

import id.global.common.iris.annotations.ExchangeType;
import id.global.common.iris.annotations.Message;

@Message(name = "snapshot-requested", exchangeType = ExchangeType.TOPIC)
public record SnapshotRequested(String resourceType, String resourceId) {
}
