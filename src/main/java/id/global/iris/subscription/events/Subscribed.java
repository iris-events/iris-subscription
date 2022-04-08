package id.global.iris.subscription.events;

import id.global.common.annotations.iris.Message;
import id.global.common.annotations.iris.Scope;

@Message(name = "subscribed", scope = Scope.SESSION)
public record Subscribed(String resourceType, String resourceId) {
}
