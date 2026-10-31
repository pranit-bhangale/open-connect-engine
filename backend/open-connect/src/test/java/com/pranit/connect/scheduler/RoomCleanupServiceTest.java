package com.pranit.connect.scheduler;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.event.RoomEvent;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomCleanupServiceTest {

    @Mock
    private ChatRoomRepository roomRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private RoomCleanupService cleanupService;

    @Test
    @DisplayName("cleanupUnclosedRooms auto-closes stale unclosed rooms and broadcasts CLOSED event")
    void testCleanupUnclosedRooms() {
        final UUID roomId = UUID.randomUUID();
        final ChatRoom staleRoom = ChatRoom.builder()
                .id(roomId)
                .roomCode("123456")
                .roomName("Abandoned Room")
                .status(RoomStatus.OPEN)
                .createdAt(Instant.now().minusSeconds(100000))
                .build();

        when(roomRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(RoomStatus.OPEN), any(Instant.class)))
                .thenReturn(List.of(staleRoom));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        final int cleaned = cleanupService.cleanupUnclosedRooms();

        assertThat(cleaned).isEqualTo(1);
        assertThat(staleRoom.getStatus()).isEqualTo(RoomStatus.CLOSED);
        assertThat(staleRoom.getClosedAt()).isNotNull();

        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId + "/presence"), any(RoomEvent.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), any(RoomEvent.class));
    }

    @Test
    @DisplayName("purgeStaleRooms deletes messages, members, and room entities")
    void testPurgeStaleRooms() {
        final UUID roomId = UUID.randomUUID();
        final ChatRoom oldRoom = ChatRoom.builder()
                .id(roomId)
                .roomCode("654321")
                .roomName("Old Room")
                .status(RoomStatus.CLOSED)
                .createdAt(Instant.now().minusSeconds(2000000))
                .build();

        when(roomRepository.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of(oldRoom));

        final int purged = cleanupService.purgeStaleRooms();

        assertThat(purged).isEqualTo(1);
        verify(messageRepository).deleteByRoom_Id(roomId);
        verify(roomMemberRepository).deleteByRoom_Id(roomId);
        verify(roomRepository).delete(oldRoom);
    }

    @Test
    @DisplayName("nightlyRoomCleanup runs both unclosed cleanup and purge")
    void testNightlyRoomCleanup() {
        when(roomRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(RoomStatus.OPEN), any(Instant.class)))
                .thenReturn(List.of());
        when(roomRepository.findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any(Instant.class)))
                .thenReturn(List.of());

        cleanupService.nightlyRoomCleanup();

        verify(roomRepository, times(1))
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(eq(RoomStatus.OPEN), any(Instant.class));
        verify(roomRepository, times(1))
                .findTop100ByCreatedAtBeforeOrderByCreatedAtAsc(any(Instant.class));
    }
}
