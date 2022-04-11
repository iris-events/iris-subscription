package id.global.iris.subscription.events;

import id.global.common.annotations.iris.Message;
import id.global.common.annotations.iris.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(String resourceType, String resourceId) {
}
