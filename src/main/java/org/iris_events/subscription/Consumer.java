package org.iris_events.subscription;

import static org.iris_events.common.MessagingHeaders.Message.CACHE_TTL;
import static org.iris_events.subscription.exception.ErrorCode.BAD_REQUEST;

import java.io.IOException;
import java.util.Set;

import org.iris_events.annotations.ExchangeType;
import org.iris_events.annotations.MessageHandler;
import org.iris_events.annotations.Scope;
import org.iris_events.common.Exchanges;
import org.iris_events.common.MessagingHeaders;
import org.iris_events.common.message.ResourceMessage;
import org.iris_events.context.EventContext;
import org.iris_events.exception.BadPayloadException;
import org.iris_events.producer.RoutingDetails;
import org.iris_events.subscription.collection.RedisSnapshotCollection;
import org.iris_events.subscription.collection.Snapshot;
import org.iris_events.subscription.events.SessionClosed;
import org.iris_events.subscription.events.SnapshotRequested;
import org.iris_events.subscription.events.Subscribe;
import org.iris_events.subscription.events.SubscribeInternal;
import org.iris_events.subscription.events.Subscribed;
import org.iris_events.subscription.events.Unsubscribe;
import org.iris_events.subscription.events.Unsubscribed;
import org.iris_events.subscription.model.Resource;
import org.iris_events.subscription.model.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

@ApplicationScoped
public class Consumer {
    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    @Inject
    EventContext eventContext;

    @Inject
    SubscriptionManager subscriptionManager;

    @Inject
    RedisSnapshotCollection snapshotCollection;

    @Inject
    SubscriptionEventProducer producer;

    @Inject
    ObjectMapper objectMapper;

    void startup(@Observes StartupEvent event) {
        log.info("Starting Iris consumer.");
    }

    @MessageHandler
    public void subscribeInternal(final SubscribeInternal subscribe) throws IOException {
        log.debug("Subscribe internal received: {}", subscribe);
        subscribe(subscribe.resourceType(), subscribe.resourceId());
    }

    @MessageHandler
    public void subscribe(final Subscribe subscribe) throws IOException {
        log.debug("Subscribe received: {}", subscribe);
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
        log.debug("Unsubscribe received: {}", unsubscribe);
        final var resources = unsubscribe.resources();
        final var sessionId = eventContext.getSessionId().orElse(null);
        for (Resource resource : resources) {
            if (resource.resourceType() == null) {
                throw new BadPayloadException(BAD_REQUEST, "Missing resource type information");
            }
            subscriptionManager.unsubscribe(sessionId, resource);
        }
        return new Unsubscribed(resources);
    }

    @MessageHandler(bindingKeys = "*.resource")
    public void resourceUpdated(final ResourceMessage resourceMessage) throws IOException {
        final var resourceType = resourceMessage.resourceType();
        final var resourceId = resourceMessage.resourceId();
        final var payloadAsBytes = objectMapper.writeValueAsBytes(resourceMessage.payload());
        final var eventName = eventContext.getHeaderValue(MessagingHeaders.Message.EVENT_TYPE)
                                      .orElseThrow(() -> new RuntimeException("Missing required event type header!"));
        final var routingKey = String.format("%s.%s", eventName, Exchanges.SESSION.getValue());
        final var snapshot = new Snapshot(eventName, routingKey, payloadAsBytes);

        eventContext.getHeaderValue(CACHE_TTL)
                .map(Integer::valueOf)
                .ifPresent(cacheTtl -> snapshotCollection.insert(resourceType, resourceId, snapshot, cacheTtl));

        Set<Subscription> subscriptions = subscriptionManager.getSubscriptions(resourceType, resourceId);
        if (subscriptions.isEmpty()) {
            return;
        }

        subscriptions.forEach(subscription -> {
            final var routingDetails = new RoutingDetails.Builder()
                                               .eventName(eventName)
                                               .exchange(Exchanges.SESSION.getValue())
                                               .exchangeType(ExchangeType.TOPIC)
                                               .routingKey(routingKey)
                                               .scope(Scope.SESSION)
                                               .subscriptionId(subscription.id())
                                               .sessionId(subscription.sessionId())
                                               .build();
            producer.sendResourceMessage(resourceType, resourceId, payloadAsBytes, routingDetails);
        });
    }

    private void subscribe(final String resourceType, final String resourceId) throws IOException {
        final var subscription = new Subscription(resourceType, resourceId, eventContext.getSessionId().orElse(null));
        subscriptionManager.addSubscription(subscription);
        eventContext.setSubscriptionId(subscription.id());

        final var subscribed = new Subscribed(resourceType, resourceId);
        producer.send(subscribed);

        // check for possible cached snapshot
        final var optionalSnapshot = snapshotCollection.get(resourceType, resourceId);
        if (optionalSnapshot.isPresent()) {
            log.info("Found snapshot on subscribe... sending snapshot. resourceType={}, resourceId={}", resourceType,
                    resourceId);
            final var snapshot = optionalSnapshot.get();
            final var eventName = snapshot.eventName();
            final var routingKey = snapshot.routingKey();

            final var routingDetails = new RoutingDetails.Builder()
                                               .eventName(eventName)
                                               .exchange(Exchanges.SESSION.getValue())
                                               .exchangeType(ExchangeType.TOPIC)
                                               .routingKey(routingKey)
                                               .scope(Scope.SESSION)
                                               .subscriptionId(subscription.id())
                                               .build();
            producer.sendResourceMessage(resourceType, resourceId, snapshot.message(), routingDetails);
        }

        sendSnapshotRequested(subscription);
    }

    private void sendSnapshotRequested(final Subscription subscription) throws IOException {
        final var resourceType = subscription.resourceType();
        final var resourceId = subscription.resourceId();
        final var exchangeName = Exchanges.SNAPSHOT_REQUESTED.getValue();
        final var routingDetails = new RoutingDetails.Builder()
                                           .eventName(exchangeName)
                                           .exchange(exchangeName)
                                           .exchangeType(ExchangeType.TOPIC)
                                           .routingKey(resourceType)
                                           .scope(Scope.INTERNAL)
                                           .subscriptionId(subscription.id())
                                           .build();
        final var snapshotRequestedMessage = new SnapshotRequested(resourceType, resourceId);
        final var payloadAsBytes = objectMapper.writeValueAsBytes(snapshotRequestedMessage);
        producer.sendResourceMessage(resourceType, resourceId, payloadAsBytes, routingDetails);
    }
}
