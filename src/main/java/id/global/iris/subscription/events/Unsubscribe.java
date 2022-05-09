package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;
import id.global.common.iris.annotations.Scope;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(String resourceType, String resourceId) {
}
