package com.test.test.stomp.service;

import com.test.test.common.exception.BusinessRuleException;
import com.test.test.common.exception.DuplicateResourceException;
import com.test.test.common.exception.EntityNotFoundException;
import com.test.test.stomp.entity.RoomEntity;
import com.test.test.stomp.model.RoomDTO;
import com.test.test.stomp.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoomService {

    private final RoomRepository roomRepository;

    public List<RoomDTO> getAllRooms() {
        return roomRepository.findAll().stream()
            .map(RoomDTO::from)
            .toList();
    }

    public RoomDTO getRoom(Long roomId) {
        RoomEntity entity = roomRepository.findById(roomId)
            .orElseThrow(() -> EntityNotFoundException.of("채팅방", roomId));
        return RoomDTO.from(entity);
    }

    @Transactional
    public RoomDTO createRoom(String rawName) {
        String roomName = rawName == null ? "" : rawName.trim();
        if (roomName.isEmpty()) {
            throw new BusinessRuleException("채팅방 이름은 비어 있을 수 없습니다.");
        }
        if (roomRepository.existsByNameIgnoreCase(roomName)) {
            throw DuplicateResourceException.alreadyExists("이미 존재하는 채팅방 이름입니다.");
        }

        RoomEntity saved = roomRepository.save(new RoomEntity(null, roomName));
        return RoomDTO.from(saved);
    }
}

