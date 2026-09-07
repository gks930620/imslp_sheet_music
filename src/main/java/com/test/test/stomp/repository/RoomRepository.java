package com.test.test.stomp.repository;

import com.test.test.stomp.entity.RoomEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<RoomEntity, Long> {

	boolean existsByNameIgnoreCase(String name);
}

