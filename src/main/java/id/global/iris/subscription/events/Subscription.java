package id.global.iris.subscription.events;

import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.Message;

@Message(name = "subscription", exchangeType = ExchangeType.TOPIC)
public record Subscription() {
}
