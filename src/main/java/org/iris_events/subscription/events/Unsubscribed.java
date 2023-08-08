package org.iris_events.subscription.events;

import java.util.List;

import org.iris_events.annotations.Message;
import org.iris_events.annotations.Scope;
import org.iris_events.subscription.model.Resource;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(List<Resource> resources) {
}
