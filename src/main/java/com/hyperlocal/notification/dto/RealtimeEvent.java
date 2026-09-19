package com.hyperlocal.notification.dto;

import com.hyperlocal.notification.enums.EventType;

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
