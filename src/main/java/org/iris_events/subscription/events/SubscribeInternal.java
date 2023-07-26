package org.iris_events.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.iris_events.annotations.Message;

@Message(name = "subscribe-internal")
public record SubscribeInternal(@JsonProperty("resource_type") String resourceType,
        @JsonProperty("resource_id") String resourceId) {
}
