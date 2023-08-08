package org.iris_events.subscription.events;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;
import org.iris_events.subscription.model.Resource;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(@JsonProperty("resource_type") String resourceType, @JsonProperty("resource_id") String resourceId, List<Resource> resources) {
}
