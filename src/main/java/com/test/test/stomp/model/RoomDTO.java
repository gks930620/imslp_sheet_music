package com.test.test.stomp.model;

import com.test.test.stomp.entity.RoomEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RoomDTO {
    private Long id;
    private String name;

    // Entity → DTO
    public static RoomDTO from(RoomEntity entity) {
        return new RoomDTO(entity.getId(), entity.getName());
    }
}

