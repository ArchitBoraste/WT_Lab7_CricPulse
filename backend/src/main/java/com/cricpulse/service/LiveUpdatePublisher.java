package com.cricpulse.service;

import com.cricpulse.dto.MatchDtos.MatchDetailDto;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Pushes updates to WebSocket (STOMP) subscribers.
 * <ul>
 *   <li>{@code /topic/matches} - lightweight summary of any match that changed (dashboard)</li>
 *   <li>{@code /topic/matches/{id}} - full snapshot of one match (match centre)</li>
 * </ul>
 */
@Component
public class LiveUpdatePublisher {

    private final SimpMessagingTemplate broker;

    public LiveUpdatePublisher(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    public void publish(MatchDetailDto detail) {
        broker.convertAndSend("/topic/matches", detail.summary());
        broker.convertAndSend("/topic/matches/" + detail.summary().id(), detail);
    }
}
