package id.global.iris.subscription.events;

import id.global.common.iris.annotations.ExchangeType;
import id.global.common.iris.annotations.Message;

@Message(name = SnapshotRequested.EXCHANGE_NAME, exchangeType = ExchangeType.TOPIC)
public record SnapshotRequested(String resourceType, String resourceId) {
    public static final String EXCHANGE_NAME = "snapshot-requested";
}
