package id.global.iris.subscription;

import static id.global.common.constants.iris.MessagingHeaders.Message.EVENT_TYPE;
import static id.global.common.constants.iris.MessagingHeaders.Message.SUBSCRIPTION_ID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;
import javax.inject.Named;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;

import id.global.common.annotations.iris.ExchangeType;
import id.global.common.annotations.iris.Scope;
import id.global.common.constants.iris.Exchanges;
import id.global.iris.messaging.runtime.AmqpBasicPropertiesProvider;
import id.global.iris.messaging.runtime.api.message.ResourceMessage;
import id.global.iris.messaging.runtime.channel.ChannelService;
import id.global.iris.messaging.runtime.context.EventContext;
import id.global.iris.messaging.runtime.producer.AmqpProducer;
import id.global.iris.messaging.runtime.producer.RoutingDetails;
import id.global.iris.subscription.events.SnapshotRequested;
import id.global.iris.subscription.events.Subscribe;
import id.global.iris.subscription.events.Subscribed;
import id.global.iris.subscription.model.Resource;
import id.global.iris.subscription.model.Subscription;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.mockito.InjectMock;

@QuarkusTest
class ConsumerTest {

    @Inject
    Consumer consumer;

    @InjectMock
    EventContext eventContext;

    @InjectMock
    SubscriptionManager subscriptionManager;

    @InjectMock
    AmqpProducer producer;

    @InjectMock
    @Named("producerChannelService")
    ChannelService channelService;

    @InjectMock
    AmqpBasicPropertiesProvider amqpBasicPropertiesProvider;

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
            when(eventContext.getHeaders()).thenReturn(Map.of(EVENT_TYPE, EVENT_NAME));
            when(subscriptionManager.getSubscriptions(RESOURCE_TYPE, RESOURCE_ID)).thenReturn(Set.of());

            consumer.resourceUpdated(new ResourceMessage(RESOURCE_TYPE, RESOURCE_ID, ""));

            verifyNoInteractions(channelService);
        }

        @Test
        void resourceUpdated() throws IOException {
            when(eventContext.getHeaders()).thenReturn(Map.of(EVENT_TYPE, EVENT_NAME));

            final var subscriptions = Set.of(new Subscription(RESOURCE_TYPE, RESOURCE_ID, sessionId));
            when(subscriptionManager.getSubscriptions(RESOURCE_TYPE, RESOURCE_ID)).thenReturn(subscriptions);

            final var channel = mock(Channel.class);
            when(channelService.getOrCreateChannelById(anyString())).thenReturn(channel);

            final var exchange = Exchanges.SESSION.getValue();
            final var routingKey = buildRoutingKey(EVENT_NAME, exchange);
            final var subscriptionId = buildSubscriptionId(RESOURCE_TYPE, RESOURCE_ID);
            final var routingDetails = new RoutingDetails(EVENT_NAME, exchange, ExchangeType.TOPIC, routingKey, Scope.SESSION,
                    null, sessionId, subscriptionId);
            final var basicProperties = new AMQP.BasicProperties();
            when(amqpBasicPropertiesProvider.getOrCreateAmqpBasicProperties(routingDetails)).thenReturn(basicProperties);

            final var payload = "a";
            consumer.resourceUpdated(new ResourceMessage(RESOURCE_TYPE, RESOURCE_ID, payload));

            verify(channel).basicPublish(exchange, routingKey, true, basicProperties, objectMapper.writeValueAsBytes(payload));
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
        void beforeEach() {
            sessionId = UUID.randomUUID().toString();
            when(eventContext.getSessionId()).thenReturn(Optional.of(sessionId));

            final var resource = new Resource(RESOURCE_TYPE, RESOURCE_ID);
            final var resources = List.of(resource);
            subscribe = new Subscribe(resources);
        }

        @Test
        void subscriptionManager() {
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
        void eventContext() {
            consumer.subscribe(subscribe);

            final var subscriptionIdArgumentCaptor = ArgumentCaptor.forClass(String.class);
            verify(eventContext).setHeader(eq(SUBSCRIPTION_ID), subscriptionIdArgumentCaptor.capture());
            final var subscriptionIdArgumentCaptorValue = subscriptionIdArgumentCaptor.getValue();
            assertThat(subscriptionIdArgumentCaptorValue, is(RESOURCE_TYPE + "-" + RESOURCE_ID));
        }

        @Test
        void producer() {
            consumer.subscribe(subscribe);

            final var objectArgumentCaptor = ArgumentCaptor.forClass(Object.class);
            verify(producer, times(2)).send(objectArgumentCaptor.capture());
            final var allValues = objectArgumentCaptor.getAllValues();

            assertThat(allValues.get(0), instanceOf(Subscribed.class));
            final var subscribed = (Subscribed) allValues.get(0);
            assertThat(subscribed.resourceId(), is(RESOURCE_ID));
            assertThat(subscribed.resourceType(), is(RESOURCE_TYPE));

            assertThat(allValues.get(1), instanceOf(SnapshotRequested.class));
            final var snapshotRequested = (SnapshotRequested) allValues.get(1);
            assertThat(snapshotRequested.resourceId(), is(RESOURCE_ID));
            assertThat(snapshotRequested.resourceType(), is(RESOURCE_TYPE));
        }
    }

    private String buildSubscriptionId(final String resourceType, final String resourceId) {
        return String.format("%s-%s", resourceType, resourceId);
    }
}