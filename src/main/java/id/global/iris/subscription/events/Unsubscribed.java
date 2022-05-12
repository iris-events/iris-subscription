package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;
import id.global.common.iris.annotations.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(String resourceType, String resourceId) {
}
