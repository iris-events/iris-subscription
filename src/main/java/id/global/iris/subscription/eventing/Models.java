package id.global.iris.subscription.eventing;

import id.global.common.annotations.amqp.Message;

public class Models {
    @Message(name = "client-session-closed")
    public record ClientSessionClosed(String userId, String sessionId) {
    }
}
