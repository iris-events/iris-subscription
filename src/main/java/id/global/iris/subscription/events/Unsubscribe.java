package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(String resourceType, String resourceId) {
}
