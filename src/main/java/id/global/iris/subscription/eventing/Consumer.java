package id.global.iris.subscription.eventing;

import javax.enterprise.context.ApplicationScoped;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import id.global.common.annotations.amqp.MessageHandler;

@ApplicationScoped
public class Consumer {

    private static final Logger log = LoggerFactory.getLogger(Consumer.class);

    @MessageHandler
    public void clientSessionClosed(final Models.ClientSessionClosed clientSessionClosed) {
        log.info("Client session closed. userId={}, sessionId={}", clientSessionClosed.userId(), clientSessionClosed.userId());
    }
}
