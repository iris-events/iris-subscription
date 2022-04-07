package id.global.iris.subscription.events;

import id.global.common.annotations.iris.ExchangeType;
import id.global.common.annotations.iris.Message;

@Message(name = "subscription", exchangeType = ExchangeType.TOPIC)
public record Subscription() {
}
