package id.global.iris.subscription.events;

import id.global.common.annotations.iris.Message;
import id.global.common.annotations.iris.Scope;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(String resourceType, String resourceId) {
}
