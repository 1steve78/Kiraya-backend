package com.hyperlocal.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

@Data
@AllArgsConstructor
public class RealtimeEvent<T> {
    private EventType type;
    private Instant timestamp;
    private T data;
}
