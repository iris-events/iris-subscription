package org.iris_events.subscription.events;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId) {
}
