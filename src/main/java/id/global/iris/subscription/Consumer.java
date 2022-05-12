package id.global.iris.subscription;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.common.iris.annotations.ExchangeType;
import id.global.common.iris.annotations.MessageHandler;
import id.global.common.iris.annotations.Scope;
import id.global.common.iris.constants.Exchanges;
import id.global.common.iris.constants.MessagingHeaders;
import id.global.iris.messaging.runtime.AmqpBasicPropertiesProvider;
import id.global.iris.messaging.runtime.api.message.ResourceMessage;
import id.global.iris.messaging.runtime.channel.ChannelService;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.producer.AmqpProducer;
import id.global.iris.messaging.runtime.producer.RoutingDetails;
import id.global.iris.subscription.events.SessionClosed;
import id.global.iris.subscription.events.SnapshotRequested;
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
    private static final String CHANNEL_ID = "iris-subscription" + UUID.randomUUID();

    @Inject
    EventContext eventContext;

    @Inject
    SubscriptionManager subscriptionManager;

    @Inject
    AmqpProducer producer;

    @Inject
    @Named("producerChannelService")
    ChannelService channelService;

    @Inject
    AmqpBasicPropertiesProvider amqpBasicPropertiesProvider;

    @Inject
    ObjectMapper objectMapper;

    @MessageHandler
    public void subscribeInternal(final SubscribeInternal subscribe) throws IOException {
        log.info("Subscribe internal received: {}", subscribe);
        subscribe(subscribe.resourceType(), subscribe.resourceId());
    }

    @MessageHandler
    public void subscribe(final Subscribe subscribe) throws IOException {
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
        final var resourceType = unsubscribe.resourceType();
        final var resourceId = unsubscribe.resourceId();
        subscriptionManager.unsubscribe(sessionId, resourceType, resourceId);
        return new Unsubscribed(resourceType, resourceId);
    }

    @MessageHandler(bindingKeys = "*.resource")
    public void resourceUpdated(final ResourceMessage resourceMessage) throws IOException {
        final var resourceType = resourceMessage.resourceType();
        final var resourceId = resourceMessage.resourceId();
        final var payloadBytes = objectMapper.writeValueAsBytes(resourceMessage.payload());
        final var eventName = eventContext.getHeaders().get(MessagingHeaders.Message.EVENT_TYPE).toString();
        final var sessionExchange = Exchanges.SESSION.getValue();
        final var routingKey = String.format("%s.%s", eventName, sessionExchange);

        Set<Subscription> subscriptions = subscriptionManager.getSubscriptions(resourceType,
                resourceId);

        if (subscriptions.isEmpty()) {
            return;
        }

        final var channel = channelService.getOrCreateChannelById(CHANNEL_ID);
        subscriptions.forEach(subscription -> {
            final var amqpBasicProperties = amqpBasicPropertiesProvider.getOrCreateAmqpBasicProperties(
                    new RoutingDetails(eventName, sessionExchange, ExchangeType.TOPIC, routingKey, Scope.SESSION, null,
                            subscription.sessionId(), subscription.id()));
            try {
                log.info("Sending message. Exchange = {}, routingKey = {}, amqpBasicProperties = {}, sessionId = {}",
                        sessionExchange, routingKey, amqpBasicProperties, subscription.sessionId());
                channel.basicPublish(sessionExchange, routingKey, true, amqpBasicProperties, payloadBytes);
            } catch (IOException e) {
                log.error("Could not send resource message.", e);
            }
        });
    }

    private void subscribe(final String resourceType, final String resourceId) throws IOException {
        final var subscription = new Subscription(resourceType, resourceId, eventContext.getSessionId().orElse(null));
        subscriptionManager.addSubscription(subscription);
        eventContext.setSubscriptionId(subscription.id());

        sendSnapshotRequested(subscription);

        final var subscribed = new Subscribed(resourceType, resourceId);
        producer.send(subscribed);
    }

    private void sendSnapshotRequested(final Subscription subscription) throws IOException {
        final var resourceType = subscription.resourceType();
        final var snapshotRequested = new SnapshotRequested(resourceType, subscription.resourceId());

        final var routingKey = String.format("%s.%s", resourceType, SnapshotRequested.EXCHANGE_NAME);

        final var channel = channelService.getOrCreateChannelById(CHANNEL_ID);
        final var routingDetails = new RoutingDetails(SnapshotRequested.EXCHANGE_NAME, SnapshotRequested.EXCHANGE_NAME,
                ExchangeType.TOPIC, routingKey, Scope.INTERNAL, null, subscription.sessionId(), subscription.id());
        final var amqpBasicProperties = amqpBasicPropertiesProvider.getOrCreateAmqpBasicProperties(routingDetails);
        final var payloadAsBytes = objectMapper.writeValueAsBytes(snapshotRequested);
        channel.basicPublish(SnapshotRequested.EXCHANGE_NAME, routingKey, true, amqpBasicProperties, payloadAsBytes);
    }
}
