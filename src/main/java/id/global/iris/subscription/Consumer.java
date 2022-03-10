package id.global.iris.subscription;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.common.annotations.amqp.MessageHandler;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.producer.AmqpProducer;
import id.global.iris.subscription.events.SessionClosed;
import id.global.iris.subscription.events.Subscribe;
import id.global.iris.subscription.events.SubscribeInternal;
import id.global.iris.subscription.events.Subscribed;
import id.global.iris.subscription.events.Unsubscribe;
import id.global.iris.subscription.events.Unsubscribed;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;

@ApplicationScoped
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    @Inject
    EventContext eventContext;

    @Inject
    SubscriptionManager subscriptionManager;

    @Inject
    AmqpProducer producer;

    @MessageHandler
    public void subscribeInternal(final SubscribeInternal subscribe) {
        log.info("Subscribe internal received: {}", subscribe);
        subscribe(subscribe.resourceType(), subscribe.resourceId());
    }

    @MessageHandler
    public void subscribe(final Subscribe subscribe) {
        log.info("Subscribe received: {}", subscribe);
        for (Resource resource : subscribe.resources()) {
            subscribe(resource.resourceType(), resource.resourceId());
        }
    }

    @MessageHandler
    public void sessionClosed(final SessionClosed sessionClosed) {
        log.info("Session closed received: {}", sessionClosed);
        final var sessionId = sessionClosed.sessionId();
        subscriptionManager.unsubscribe(sessionId);
    }

    @MessageHandler
    public Unsubscribed unsubscribe(final Unsubscribe unsubscribe) {
        log.info("Unsubscribe received: {}", unsubscribe);
        final var sessionId = eventContext.getSessionId().orElse(null);
        subscriptionManager.unsubscribe(sessionId);

        return new Unsubscribed(unsubscribe.resourceType(), unsubscribe.resourceId());
    }

    private void subscribe(final String resourceType, final String resourceId) {
        subscriptionManager.addSubscription(
                new Subscription(resourceType,
                        resourceId,
                        eventContext.getSessionId().orElse(null)));

        final var subscribed = new Subscribed(resourceType, resourceId);
        producer.send(subscribed);
    }
}
