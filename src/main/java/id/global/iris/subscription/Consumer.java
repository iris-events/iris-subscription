package id.global.iris.subscription;

import static id.global.iris.common.constants.MessagingHeaders.Message.CACHE_TTL;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.MessageHandler;
import id.global.iris.common.annotations.Scope;
import id.global.iris.common.constants.Exchanges;
import id.global.iris.common.constants.MessagingHeaders;
import id.global.iris.common.message.ResourceMessage;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.producer.RoutingDetails;
import id.global.iris.subscription.collection.RedisSnapshotCollection;
import id.global.iris.subscription.collection.Snapshot;
import id.global.iris.subscription.events.SessionClosed;
import id.global.iris.subscription.events.SnapshotRequested;
import id.global.iris.subscription.events.Subscribe;
import id.global.iris.subscription.events.SubscribeInternal;
import id.global.iris.subscription.events.Subscribed;
import id.global.iris.subscription.events.Unsubscribe;
import id.global.iris.subscription.events.Unsubscribed;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
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
                    .sessionId(subscription.sessionId())
                    .subscriptionId(subscription.id())
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
                    .sessionId(subscription.sessionId())
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
                .sessionId(subscription.sessionId())
                .subscriptionId(subscription.id())
                .build();
        final var snapshotRequestedMessage = new SnapshotRequested(resourceType, resourceId);
        final var payloadAsBytes = objectMapper.writeValueAsBytes(snapshotRequestedMessage);
        producer.sendResourceMessage(resourceType, resourceId, payloadAsBytes, routingDetails);
    }
}
