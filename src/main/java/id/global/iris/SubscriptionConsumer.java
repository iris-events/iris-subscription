package id.global.iris;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.common.annotations.amqp.MessageHandler;

@ApplicationScoped
public class SubscriptionConsumer {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionConsumer.class.getName());

    @MessageHandler
    public void handleSubscription(Subscription subscription) {
        logger.info(String.format("Got subscription %d", subscription.id()));
    }
}
