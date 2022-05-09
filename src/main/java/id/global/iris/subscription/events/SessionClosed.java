package id.global.iris.subscription.events;

import id.global.common.iris.annotations.Message;
import id.global.common.iris.annotations.Scope;

@Message(name = "session-closed", scope = Scope.FRONTEND)
public record SessionClosed(String userId, String sessionId) {
}
