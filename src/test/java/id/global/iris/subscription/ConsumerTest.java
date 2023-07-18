package id.global.iris.subscription;

import static id.global.iris.common.constants.MessagingHeaders.Message.EVENT_TYPE;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.Scope;
import id.global.iris.common.constants.Exchanges;
import id.global.iris.common.message.ResourceMessage;
import id.global.iris.messaging.runtime.BasicPropertiesProvider;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.producer.RoutingDetails;
import id.global.iris.subscription.events.SnapshotRequested;
import id.global.iris.subscription.events.Subscribe;
import id.global.iris.subscription.events.Subscribed;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;
import jakarta.inject.Inject;

@QuarkusTest
class ConsumerTest {

    @Inject
    Consumer consumer;

    @InjectMock
    EventContext eventContext;

    @InjectMock
    SubscriptionManager subscriptionManager;

    @InjectMock
    SubscriptionEventProducer producer;

    @InjectMock
    BasicPropertiesProvider basicPropertiesProvider;

    @Inject
    ObjectMapper objectMapper;

    @Nested
    class ResourceUpdatedNested {

        private static final String RESOURCE_TYPE = "inventory";
        private static final String RESOURCE_ID = "all";
        private static final String EVENT_NAME = "consumer-test-update";
        private String sessionId;

        @BeforeEach
        void beforeEach() {
            sessionId = UUID.randomUUID().toString();
        }

        @Test
        void emptySubscriptions() throws IOException {
            when(eventContext.getHeaderValue(EVENT_TYPE)).thenReturn(Optional.of(EVENT_NAME));
            when(subscriptionManager.getSubscriptions(RESOURCE_TYPE, RESOURCE_ID)).thenReturn(Set.of());

            consumer.resourceUpdated(new ResourceMessage(RESOURCE_TYPE, RESOURCE_ID, ""));

            verifyNoInteractions(producer);
        }

        @Test
        void resourceUpdated() throws IOException {
            when(eventContext.getHeaderValue(EVENT_TYPE)).thenReturn(Optional.of(EVENT_NAME));

            final var subscriptions = Set.of(new Subscription(RESOURCE_TYPE, RESOURCE_ID, sessionId));
            when(subscriptionManager.getSubscriptions(RESOURCE_TYPE, RESOURCE_ID)).thenReturn(subscriptions);

            final var exchange = Exchanges.SESSION.getValue();
            final var routingKey = buildRoutingKey(EVENT_NAME, exchange);
            final var subscriptionId = buildSubscriptionId(RESOURCE_TYPE, RESOURCE_ID);
            final var routingDetails = new RoutingDetails.Builder()
                    .eventName(EVENT_NAME)
                    .exchange(exchange)
                    .exchangeType(ExchangeType.TOPIC)
                    .routingKey(routingKey)
                    .scope(Scope.SESSION)
                    .sessionId(sessionId)
                    .subscriptionId(subscriptionId)
                    .build();
            final var payload = "a";
            consumer.resourceUpdated(new ResourceMessage(RESOURCE_TYPE, RESOURCE_ID, payload));

            verify(producer).sendResourceMessage(RESOURCE_TYPE, RESOURCE_ID, objectMapper.writeValueAsBytes(payload),
                    routingDetails);
        }

        private String buildRoutingKey(String eventName, String exchange) {
            return String.format("%s.%s", eventName, exchange);
        }
    }

    @Nested
    class SubscribeNested {

        private static final String RESOURCE_TYPE = "inventory";
        private static final String RESOURCE_ID = "all";
        private String sessionId;
        private Subscribe subscribe;

        @BeforeEach
        void beforeEach() throws IOException {
            sessionId = UUID.randomUUID().toString();
            when(eventContext.getSessionId()).thenReturn(Optional.of(sessionId));

            final var resource = new Resource(RESOURCE_TYPE, RESOURCE_ID);
            final var resources = List.of(resource);
            subscribe = new Subscribe(resources);
        }

        @Test
        void subscriptionManager() throws IOException {
            consumer.subscribe(subscribe);

            final var subscriptionArgumentCaptor = ArgumentCaptor.forClass(Subscription.class);
            verify(subscriptionManager).addSubscription(subscriptionArgumentCaptor.capture());
            final var subscription = subscriptionArgumentCaptor.getValue();
            assertThat(subscription.sessionId(), is(sessionId));
            assertThat(subscription.resourceId(), is(RESOURCE_ID));
            assertThat(subscription.resourceType(), is(RESOURCE_TYPE));
            assertThat(subscription.id(), is(buildSubscriptionId(RESOURCE_TYPE, RESOURCE_ID)));
        }

        @Test
        void eventContext() throws IOException {
            consumer.subscribe(subscribe);

            final var subscriptionIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
            verify(eventContext).setSubscriptionId(subscriptionIdArgumentCaptor.capture());
            final var subscriptionIdArgumentCaptorValue = subscriptionIdArgumentCaptor.getValue();
            assertThat(subscriptionIdArgumentCaptorValue, is(RESOURCE_TYPE + "-" + RESOURCE_ID));
        }

        @Test
        void subscribed() throws IOException {
            consumer.subscribe(subscribe);

            final var objectArgumentCaptor = ArgumentCaptor.forClass(Subscribed.class);
            verify(producer).send(objectArgumentCaptor.capture());
            final var subscribed = objectArgumentCaptor.getValue();

            assertThat(subscribed.resourceId(), is(RESOURCE_ID));
            assertThat(subscribed.resourceType(), is(RESOURCE_TYPE));
        }

        @Test
        void snapshotRequested() throws IOException {
            final var exchangeName = Exchanges.SNAPSHOT_REQUESTED.getValue();

            consumer.subscribe(subscribe);

            final var inOrder = inOrder(producer);
            final var subscribed = new Subscribed(RESOURCE_TYPE, RESOURCE_ID);
            inOrder.verify(producer).send(subscribed);

            final var subscriptionId = buildSubscriptionId(RESOURCE_TYPE, RESOURCE_ID);
            final var routingDetails = new RoutingDetails.Builder()
                    .eventName(exchangeName)
                    .exchange(exchangeName)
                    .exchangeType(ExchangeType.TOPIC)
                    .routingKey(RESOURCE_TYPE)
                    .scope(Scope.INTERNAL)
                    .sessionId(sessionId)
                    .subscriptionId(subscriptionId)
                    .build();
            final var snapshotRequested = new SnapshotRequested(RESOURCE_TYPE, RESOURCE_ID);
            final var payloadsAsBytes = objectMapper.writeValueAsBytes(snapshotRequested);
            inOrder.verify(producer).sendResourceMessage(RESOURCE_TYPE, RESOURCE_ID, payloadsAsBytes, routingDetails);
        }
    }

    private String buildSubscriptionId(final String resourceType, final String resourceId) {
        return String.format("%s-%s", resourceType, resourceId);
    }
}
