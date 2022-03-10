package id.global.iris.subscription.events;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;

@Message(name = "session-closed", scope = Scope.FRONTEND)
public record SessionClosed(String userId, String sessionId) {
}
