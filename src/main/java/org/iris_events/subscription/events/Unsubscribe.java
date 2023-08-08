package org.iris_events.subscription.events;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;
import org.iris_events.subscription.model.Resource;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(List<Resource> resources) {
}
