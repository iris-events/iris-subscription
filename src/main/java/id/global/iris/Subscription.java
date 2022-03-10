package id.global.iris;

import id.global.common.annotations.amqp.Message;

@Message(name = "subscription")
public record Subscription(int id) {
    // TODO
}
