package id.global.iris.subscription.events;

import java.util.List;

import id.global.common.annotations.amqp.Message;
import id.global.common.annotations.amqp.Scope;
import id.global.iris.subscription.model.Resource;

@Message(name = "subscribe", scope = Scope.FRONTEND)
public record Subscribe(List<Resource> resources) {
}
