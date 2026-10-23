package com.pranit.connect.scheduler;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomCleanupService {

    private final ChatRoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;

    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void nightlyRoomCleanup() {
        log.info("Initiating Cleanup process at: {}", Instant.now());
        Instant now = Instant.now();
        Instant timeFrame = now.minus(15, ChronoUnit.DAYS);
        // Find rooms Open and Closed older than 15 days from creation
        List<ChatRoom> oldRooms = roomRepository.findAll().stream()
                .filter(room -> room.getCreatedAt() != null && room.getCreatedAt().isBefore(timeFrame))
                .limit(100)
                .toList();
        log.info("Found {} rooms older than 15 days to clean up", oldRooms.size());
        // Clean up each old room
        for (ChatRoom room : oldRooms) {
            cleanRoomData(room);
        }
        log.info("Finished Cleanup process at: {}", Instant.now());
    }

    private void cleanRoomData(ChatRoom room) {
        UUID roomId = room.getId();
        String roomCode = room.getRoomCode();
        // Delete all messages for this room only
        messageRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned messages from room | name: {} | code: {}", room.getRoomName(), roomCode);
        // Delete all memberships for this room only
        roomMemberRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned members from room | name: {} | code: {}", room.getRoomName(), roomCode);
        // Delete the room entity entirely
        roomRepository.delete(room);
        log.debug("Deleted room entity | code: {}", roomCode);
    }
}
