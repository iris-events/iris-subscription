package id.global.iris.subscription;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.fasterxml.jackson.databind.ObjectMapper;

import id.global.iris.common.annotations.ExchangeType;
import id.global.iris.common.annotations.Scope;
import id.global.iris.common.constants.Exchanges;
import id.global.iris.common.constants.MessagingHeaders;
import id.global.iris.common.message.ResourceMessage;
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
public class SubscriptionIntegrationTest {

    @Inject
    Consumer consumer;
    @Inject
    ObjectMapper objectMapper;
    @InjectMock
    SubscriptionEventProducer eventProducer;
    @InjectMock
    EventContext eventContext;

    String resourceType;
    String resourceId;
    Subscription subscription;
    String eventName;
    String payload;
    ResourceMessage resourceMessage;

    @BeforeEach
    void beforeEach() {
        String sessionId = UUID.randomUUID().toString();
        resourceType = "res-type-" + UUID.randomUUID();
        resourceId = "res-id-" + UUID.randomUUID();
        subscription = new Subscription(resourceType, resourceId, sessionId);
        eventName = "event-type-" + UUID.randomUUID();
        payload = """
                {
                    "foo": "bar"
                }""";
        resourceMessage = new ResourceMessage(resourceType, resourceId, payload);
        when(eventContext.getSessionId()).thenReturn(Optional.of(sessionId));
        when(eventContext.getHeaderValue(MessagingHeaders.Message.EVENT_TYPE)).thenReturn(Optional.of(eventName));
    }

    @Test
    public void shouldSubscribeSuccessfully() throws Exception {
        consumer.subscribe(new Subscribe(List.of(new Resource(resourceType, resourceId))));

        final var expectedSubscribedEvent = new Subscribed(resourceType, resourceId);
        final var expectedSnapshotRequestedPayload = getSnapshotRequested(resourceType, resourceId);
        final var exchangeName = Exchanges.SNAPSHOT_REQUESTED.getValue();
        final var snapshotRequestedRoutingDetails = new RoutingDetails.Builder()
                .eventName(exchangeName)
                .exchange(exchangeName)
                .exchangeType(ExchangeType.TOPIC)
                .routingKey(resourceType)
                .scope(Scope.INTERNAL)
                .sessionId(subscription.sessionId())
                .subscriptionId(subscription.id())
                .build();

        InOrder inOrder = inOrder(eventProducer);
        inOrder.verify(eventProducer).send(expectedSubscribedEvent);
        inOrder.verify(eventProducer).sendResourceMessage(resourceType, resourceId, expectedSnapshotRequestedPayload,
                snapshotRequestedRoutingDetails);

        consumer.resourceUpdated(resourceMessage);
        final var routingKey = String.format("%s.%s", eventName, Exchanges.SESSION.getValue());
        final var resourceUpdatedRoutingDetails = new RoutingDetails.Builder()
                .eventName(eventName)
                .exchange(Exchanges.SESSION.getValue())
                .exchangeType(ExchangeType.TOPIC)
                .routingKey(routingKey)
                .scope(Scope.SESSION)
                .sessionId(subscription.sessionId())
                .subscriptionId(subscription.id())
                .build();
        inOrder.verify(eventProducer)
                .sendResourceMessage(resourceType, resourceId, objectMapper.writeValueAsBytes(resourceMessage.payload()),
                        resourceUpdatedRoutingDetails);
        verifyNoMoreInteractions(eventProducer);
    }

    @Test
    public void shouldReturnCachedSnapshotImmediately() throws Exception {
        when(eventContext.getHeaderValue(MessagingHeaders.Message.CACHE_TTL)).thenReturn(Optional.of("300"));

        consumer.resourceUpdated(resourceMessage);
        consumer.subscribe(new Subscribe(List.of(new Resource(resourceType, resourceId))));

        final var routingKey = String.format("%s.%s", eventName, Exchanges.SESSION.getValue());
        final var routingDetails = new RoutingDetails.Builder()
                .eventName(eventName)
                .exchange(Exchanges.SESSION.getValue())
                .exchangeType(ExchangeType.TOPIC)
                .routingKey(routingKey)
                .scope(Scope.SESSION)
                .sessionId(subscription.sessionId())
                .subscriptionId(subscription.id())
                .build();

        InOrder inOrder = inOrder(eventProducer);
        inOrder.verify(eventProducer).send(any(Subscribed.class));
        inOrder.verify(eventProducer)
                .sendResourceMessage(resourceType, resourceId, objectMapper.writeValueAsBytes(payload), routingDetails);
        inOrder.verify(eventProducer).sendResourceMessage(anyString(), anyString(), any(), any(RoutingDetails.class));
        verifyNoMoreInteractions(eventProducer);
    }

    private byte[] getSnapshotRequested(String resourceType, String resourceId) throws Exception {
        final var snapshotRequestedMessage = new SnapshotRequested(resourceType, resourceId);
        return objectMapper.writeValueAsBytes(snapshotRequestedMessage);
    }
}
