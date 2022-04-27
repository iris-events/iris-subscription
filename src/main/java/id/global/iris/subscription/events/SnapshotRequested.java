package id.global.iris.subscription.events;

import id.global.common.annotations.iris.ExchangeType;
import id.global.common.annotations.iris.Message;

@Message(name = SnapshotRequested.EXCHANGE_NAME, exchangeType = ExchangeType.TOPIC)
public record SnapshotRequested(String resourceType, String resourceId) {
    public static final String EXCHANGE_NAME = "snapshot-requested";
}
