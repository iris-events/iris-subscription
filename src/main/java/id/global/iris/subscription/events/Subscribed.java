package id.global.iris.subscription.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "subscribed", scope = Scope.SESSION)
public record Subscribed(String resourceType, String resourceId) {
}
