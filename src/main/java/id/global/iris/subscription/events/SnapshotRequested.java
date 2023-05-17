package id.global.iris.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;
import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.Message;

@Message(name = "snapshot-requested", exchangeType = ExchangeType.TOPIC)
public record SnapshotRequested(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId) {
}
