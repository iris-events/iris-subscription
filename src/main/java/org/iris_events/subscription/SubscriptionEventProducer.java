package org.iris_events.subscription;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.iris_events.common.Exchanges;
import org.iris_events.runtime.BasicPropertiesProvider;
import org.iris_events.runtime.channel.ChannelService;
import org.iris_events.producer.EventProducer;
import org.iris_events.producer.RoutingDetails;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

@ApplicationScoped
public class SubscriptionEventProducer {
    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventProducer.class);
    private static final String CHANNEL_ID = "iris-subscription" + UUID.randomUUID();
    private final EventProducer eventProducer;
    private final ChannelService channelService;
    private final BasicPropertiesProvider basicPropertiesProvider;

    @Inject
    public SubscriptionEventProducer(final EventProducer eventProducer,
            final @Named("producerChannelService") ChannelService channelService,
            final BasicPropertiesProvider basicPropertiesProvider) {
        this.eventProducer = eventProducer;
        this.channelService = channelService;
        this.basicPropertiesProvider = basicPropertiesProvider;
    }

    public void sendResourceMessage(String resourceType, String resourceId, byte[] payloadAsBytes,
            RoutingDetails routingDetails) {
        final var amqpBasicProperties = basicPropertiesProvider.getOrCreateAmqpBasicProperties(routingDetails);
        final var routingKey = routingDetails.getRoutingKey();
        try {
            if (log.isDebugEnabled()) {
                log.debug("Sending message. exchange={}, routingKey={}, amqpBasicProperties={}, resourceType={}, resourceId={}",
                        Exchanges.SESSION.getValue(), routingKey, amqpBasicProperties, resourceType, resourceId);
            }
            final var channel = channelService.getOrCreateChannelById(CHANNEL_ID);
            channel.basicPublish(Exchanges.SESSION.getValue(), routingKey, true, amqpBasicProperties, payloadAsBytes);
        } catch (IOException e) {
            log.error(
                    String.format("Could not send resource message. exchange=%s, routingKey=%s, resourceType=%s, resourceId=%s",
                            Exchanges.SESSION.getValue(), routingKey, resourceType, resourceId),
                    e);
        }
    }

    public void send(final Object message) {
        eventProducer.send(message);
    }
}
