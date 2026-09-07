package com.test.test.stomp.controller;

import com.test.test.common.dto.ApiResponse;
import com.test.test.stomp.model.RoomCreateRequest;
import com.test.test.stomp.model.RoomDTO;
import com.test.test.stomp.service.RoomService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<RoomDTO>>> getRooms() {
        return ResponseEntity.ok(ApiResponse.success("Room list fetched", roomService.getAllRooms()));
    }

    @GetMapping("/{roomId}")
    public ResponseEntity<ApiResponse<RoomDTO>> getRoom(@PathVariable Long roomId) {
        return ResponseEntity.ok(ApiResponse.success("Room fetched", roomService.getRoom(roomId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<RoomDTO>> createRoom(@Valid @RequestBody RoomCreateRequest request) {
        RoomDTO room = roomService.createRoom(request.getName());
        return ResponseEntity.created(URI.create("/api/rooms/" + room.getId()))
            .body(ApiResponse.success("Room created", room));
    }
}
