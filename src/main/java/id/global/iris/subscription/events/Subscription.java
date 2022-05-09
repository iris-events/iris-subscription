package id.global.iris.subscription.events;

import id.global.common.iris.annotations.ExchangeType;
import id.global.common.iris.annotations.Message;

@Message(name = "subscription", exchangeType = ExchangeType.TOPIC)
public record Subscription() {
}
