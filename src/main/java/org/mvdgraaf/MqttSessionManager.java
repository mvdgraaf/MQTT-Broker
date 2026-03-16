package org.mvdgraaf;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

public class MqttSessionManager {

    private final Map<String, Channel> clientChannels = new ConcurrentHashMap<>();

    private final Map<String, Set<Channel>> clientSubscriptions = new ConcurrentHashMap<>();

    public void addClient(String clientId, Channel channel) {
        Channel oldChannel = clientChannels.get(clientId);
        if (oldChannel != null && oldChannel.isActive()) {
            if (oldChannel.id().equals(channel.id())) {
                return;
            }
            System.out.println("Kick old client: " + clientId);
            oldChannel.close().addListener(future -> clientChannels.put(clientId, channel));
        } else {
            clientChannels.put(clientId, channel);
        }
    }

    public void subscribe(String topic, Channel channel) {
        clientSubscriptions.computeIfAbsent(topic, k -> new CopyOnWriteArraySet<>()).add(channel);
        System.out.println("New subscription on: " + topic);
    }

    public void unsubscribe(String topic, Channel channel) {
        Set<Channel> subscribers = clientSubscriptions.get(topic);
        if (subscribers != null) {
            subscribers.remove(channel);
            if (subscribers.isEmpty()) {
                clientSubscriptions.remove(topic);
            }
        }
    }
    public void broadcast(String topic, MqttPublishMessage message) {
        Set<Channel> targets = new HashSet<>();

        clientSubscriptions.forEach((filter, subscribers) -> {
            if (matches(filter, topic)) {
                targets.addAll(subscribers);
            }
        });

        try {
            MqttQoS qos = message.fixedHeader().qosLevel();
            int packetId = (qos != MqttQoS.AT_MOST_ONCE) ? message.variableHeader().packetId() : -1;            boolean isRetain = message.fixedHeader().isRetain();
            for (Channel ch : targets) {
                if (ch.isActive()) {
                    ByteBuf payload = message.payload().retainedDuplicate();
                    var builder = MqttMessageBuilders.publish()
                            .topicName(topic)
                            .retained(isRetain)
                            .qos(qos)
                            .payload(payload);
                    if (qos != MqttQoS.AT_MOST_ONCE) {
                        builder.messageId(packetId);
                    }
                    ch.writeAndFlush(builder.build());
                }
            }
        } finally {
            if (message.refCnt() > 0) {
                message.release();
            }
        }
    }

    public boolean matches(String filter, String topic) {
        if (filter.equals(topic)) return true;
        if (filter.equals("#")) return !topic.startsWith("$");
        String regex = filter
                .replace("$", "\\$")
                .replace("+", "[^/]+")
                .replace("#", ".*");

        return topic.matches("^" + regex + "$");
    }

    public void removeChannel(Channel channel) {
       clientChannels.values().removeIf(ch -> ch.id().equals(channel.id()));
       clientSubscriptions.values().removeIf(subs -> subs.remove(channel));
       clientSubscriptions.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }
}
