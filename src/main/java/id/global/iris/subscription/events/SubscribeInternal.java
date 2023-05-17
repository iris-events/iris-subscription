package id.global.iris.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import id.global.iris.common.annotations.Message;

@Message(name = "subscribe-internal")
public record SubscribeInternal(@JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") String resourceId) {
}
