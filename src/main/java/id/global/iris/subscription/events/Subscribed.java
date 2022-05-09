package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;
import id.global.common.iris.annotations.Scope;

@Message(name = "subscribed", scope = Scope.SESSION)
public record Subscribed(String resourceType, String resourceId) {
}
