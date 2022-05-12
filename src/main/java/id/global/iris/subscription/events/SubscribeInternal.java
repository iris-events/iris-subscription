package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;

@Message(name = "subscribe-internal")
public record SubscribeInternal(String resourceType, String resourceId) {
}
