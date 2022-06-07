package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "subscribed", scope = Scope.SESSION)
public record Subscribed(String resourceType, String resourceId) {
}
