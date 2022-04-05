package id.global.iris.subscription.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "unsubscribe", scope = Scope.FRONTEND)
public record Unsubscribe(String resourceType, String resourceId) {
}
