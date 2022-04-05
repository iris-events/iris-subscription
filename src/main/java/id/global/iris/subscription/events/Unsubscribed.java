package id.global.iris.subscription.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "unsubscribed", scope = Scope.SESSION)
public record Unsubscribed(String resourceType, String resourceId) {
}
