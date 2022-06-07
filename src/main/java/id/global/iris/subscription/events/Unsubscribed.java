package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(String resourceType, String resourceId) {
}
