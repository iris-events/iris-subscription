package id.global.iris.subscription.events;

import id.global.common.annotations.iris.Message;
import id.global.common.annotations.iris.Scope;

@Message(name = "resource-update", scope = Scope.SESSION)
public record ResourceUpdate(String resourceType, String resourceId, Object payload) {
}
