package com.qrmenu.shared.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Thin write-side facade used by domain modules right alongside the entity save that
 * the event describes, so both land in the same transaction (the "transactional" half
 * of Transactional Outbox). Publishing to in-process subscribers is a separate,
 * later step - see OutboxPollerScheduler.
 */
@Component
public class OutboxEventWriter {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxEventWriter(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void write(String aggregateType, UUID aggregateId, String eventType, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload: " + eventType, e);
        }
        repository.save(new OutboxEvent(aggregateType, aggregateId, eventType, json));
    }
}
