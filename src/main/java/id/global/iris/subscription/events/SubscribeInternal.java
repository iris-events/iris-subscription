package id.global.iris.subscription.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "subscribe-internal")
public record SubscribeInternal(String resourceType, String resourceId, Scope scope) {
}
