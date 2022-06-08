package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;
import id.global.iris.common.annotations.Scope;

@Message(name = "resource-update", scope = Scope.SESSION)
public record ResourceUpdate(String resourceType, String resourceId, Object payload) {
}
