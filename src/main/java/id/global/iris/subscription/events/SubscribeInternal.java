package id.global.iris.subscription.events;

import id.global.iris.common.annotations.Message;

@Message(name = "subscribe-internal")
public record SubscribeInternal(String resourceType, String resourceId) {
}
