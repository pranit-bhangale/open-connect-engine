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
        final Instant timeFrame = Instant.now().minus(15, ChronoUnit.DAYS);
        final List<ChatRoom> oldRooms = roomRepository.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(timeFrame);
        log.info("Found {} rooms older than 15 days to clean up", oldRooms.size());
        for (final ChatRoom room : oldRooms) {
            cleanRoomData(room);
        }
        log.info("Finished Cleanup process at: {}", Instant.now());
    }

    private void cleanRoomData(ChatRoom room) {
        UUID roomId = room.getId();
        String roomCode = room.getRoomCode();
        messageRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned messages from room | name: {} | code: {}", room.getRoomName(), roomCode);
        roomMemberRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned members from room | name: {} | code: {}", room.getRoomName(), roomCode);
        roomRepository.delete(room);
        log.debug("Deleted room entity | code: {}", roomCode);
    }
}
