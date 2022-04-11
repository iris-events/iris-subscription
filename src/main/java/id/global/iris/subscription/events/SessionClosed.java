package id.global.iris.subscription.events;

import id.global.common.annotations.iris.Message;
import id.global.common.annotations.iris.Scope;

@Message(name = "session-closed", scope = Scope.FRONTEND)
public record SessionClosed(String userId, String sessionId) {
}
