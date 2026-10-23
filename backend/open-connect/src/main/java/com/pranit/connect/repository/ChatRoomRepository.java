package com.pranit.connect.repository;

import com.pranit.connect.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {
    boolean existsByRoomCode(String code);

    Optional<ChatRoom> findByRoomCode(String roomCode);

    List<ChatRoom> findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(Instant cutoff);
}
