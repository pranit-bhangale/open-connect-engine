package com.pranit.connect.scheduler;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.event.RoomEvent;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "open-connect.cleanup.enabled", havingValue = "true", matchIfMissing = true)
public class RoomCleanupService {

    private final ChatRoomRepository roomRepository;
    private final MessageRepository messageRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    /**
     * Nightly scheduled cleanup job.
     * 1. Detects and auto-closes unclosed rooms older than unclosedTtlHours.
     * 2. Purges stale closed rooms older than retentionDays.
     */
    @Scheduled(cron = "${open-connect.cleanup.cron:0 0 3 * * ?}")
    public void nightlyRoomCleanup() {
        log.info("Starting scheduled room cleanup job at {}", Instant.now());
        final int unclosedCleaned = cleanupUnclosedRooms();
        final int purgedCount = purgeStaleRooms();
        log.info("Completed scheduled room cleanup. Unclosed auto-closed: {}, Stale purged: {}",
                unclosedCleaned, purgedCount);
    }

    /**
     * Optimally cleans up unclosed (OPEN) rooms older than unclosedTtlHours.
     * Auto-closes them, broadcasts real-time notifications to subscribers, and disables chat.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int cleanupUnclosedRooms() {
        long unclosedTtlHours = 24;
        final Instant cutoff = Instant.now().minus(Duration.ofHours(unclosedTtlHours));
        int total = 0;
        int batch = 0;
        int maxBatches = 10;
        while (batch < maxBatches) {
            final List<ChatRoom> unclosedRooms = this.roomRepository
                    .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(RoomStatus.OPEN, cutoff);
            if (unclosedRooms.isEmpty()) break;
            for (final ChatRoom room : unclosedRooms) {
                room.setStatus(RoomStatus.CLOSED);
                room.setClosedAt(Instant.now());
                this.roomRepository.save(room);
                final RoomEvent closeEvent = RoomEvent.builder()
                        .roomId(room.getId())
                        .roomCode(room.getRoomCode())
                        .eventType(RoomEvent.EventType.CLOSED)
                        .message("Room was automatically closed by system due to inactivity timeout.")
                        .timestamp(Instant.now())
                        .roomStatus(RoomStatus.CLOSED)
                        .isHost(false)
                        .isHostOnline(false)
                        .memberCount(0)
                        .build();
                this.messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/presence", closeEvent);
                this.messagingTemplate.convertAndSend("/topic/room/" + room.getId(), closeEvent);
                total++;
            }
            batch++;
            if (unclosedRooms.size() < 100) break;
        }
        log.info("Cleaned up and closed {} unclosed room(s) created before {}", total, cutoff);
        return total;
    }

    /**
     * Purges stale closed rooms older than retentionDays.
     * Deletes associated messages, members, and the room entity.
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int purgeStaleRooms() {
        long retentionDays = 15;
        final Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        final List<ChatRoom> oldRooms = this.roomRepository
                .findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(cutoff);
        if (oldRooms.isEmpty()) return 0;
        for (final ChatRoom room : oldRooms) {
            cleanRoomData(room);
        }
        log.info("Purged {} stale room(s) older than {} days", oldRooms.size(), retentionDays);
        return oldRooms.size();
    }

    private void cleanRoomData(final ChatRoom room) {
        final UUID roomId = room.getId();
        final String roomCode = room.getRoomCode();
        this.messageRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned messages for room {} ({})", room.getRoomName(), roomCode);
        this.roomMemberRepository.deleteByRoom_Id(roomId);
        log.debug("Cleaned members for room {} ({})", room.getRoomName(), roomCode);
        this.roomRepository.delete(room);
        log.debug("Deleted room entity {}", roomCode);
    }
}
