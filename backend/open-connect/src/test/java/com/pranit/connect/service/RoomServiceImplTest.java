package com.pranit.connect.service;

import com.pranit.connect.dto.CreateRoom;
import com.pranit.connect.dto.JoinRoom;
import com.pranit.connect.dto.MessageRequest;
import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.OwnerDetail;
import com.pranit.connect.dto.RoomResponse;
import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.Message;
import com.pranit.connect.entity.MessageType;
import com.pranit.connect.entity.RoomMember;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.entity.User;
import com.pranit.connect.event.RoomEvent;
import com.pranit.connect.exception.BaseException;
import com.pranit.connect.exception.RoomClosedException;
import com.pranit.connect.exception.RoomNotFoundException;
import com.pranit.connect.exception.RoomOperationNotAllowedException;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import com.pranit.connect.repository.UserRepository;
import com.pranit.connect.service.impl.RoomServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageSendingOperations;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceImplTest {

    @Mock
    private ChatRoomRepository roomRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoomMemberRepository roomMemberRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private RoomServiceImpl roomService;

    private User owner;
    private User regularUser;
    private ChatRoom room;
    private UUID roomId;

    @BeforeEach
    void setUp() {
        roomId = UUID.randomUUID();
        owner = User.builder()
                .id(UUID.randomUUID())
                .username("alice_owner")
                .email("alice@example.com")
                .createdAt(Instant.now())
                .build();

        regularUser = User.builder()
                .id(UUID.randomUUID())
                .username("bob_member")
                .email("bob@example.com")
                .createdAt(Instant.now())
                .build();

        room = ChatRoom.builder()
                .id(roomId)
                .roomCode("123456")
                .roomName("General Chat")
                .description("Discussion Room")
                .owner(owner)
                .status(RoomStatus.OPEN)
                .createdAt(Instant.now())
                .build();
    }

    @Test
    @DisplayName("createNewRoom creates room, sets creator as owner, adds owner to members, and broadcasts JOINED")
    void testCreateNewRoom() {
        final CreateRoom request = new CreateRoom(
                new CreateRoom.UserRequest("alice_owner", "alice@example.com"),
                new CreateRoom.RoomRequest("General Chat", "Discussion Room")
        );

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        when(roomRepository.existsByRoomCode(any())).thenReturn(false);
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(invocation -> {
            ChatRoom r = invocation.getArgument(0);
            r.setId(roomId);
            return r;
        });

        final RoomMember member = RoomMember.builder().room(room).user(owner).joinedAt(Instant.now()).build();
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of(member));
        when(roomMemberRepository.existsByRoomAndUser(any(ChatRoom.class), eq(owner))).thenReturn(true);

        final RoomResponse response = roomService.createNewRoom(request);

        assertThat(response).isNotNull();
        assertThat(response.user().isHost()).isTrue();
        assertThat(response.user().username()).isEqualTo("alice_owner");
        assertThat(response.room().roomName()).isEqualTo("General Chat");
        assertThat(response.room().roomStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.room().isHostOnline()).isTrue();

        verify(roomMemberRepository).save(any(RoomMember.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room/" + roomId + "/presence"), any(RoomEvent.class));
        verify(messagingTemplate, times(1)).convertAndSend(eq("/topic/room/" + roomId), any(RoomEvent.class));
    }

    @Test
    @DisplayName("joinChatRoom by roomCode adds user to room and notifies room members")
    void testJoinChatRoom_ByRoomCode() {
        final JoinRoom request = new JoinRoom(
                new JoinRoom.UserRequest("bob_member", "bob@example.com"),
                new JoinRoom.RoomDetail("123456")
        );

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(regularUser));
        when(roomRepository.findByRoomCode("123456")).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomAndUser(room, regularUser)).thenReturn(false);

        final RoomMember ownerMember = RoomMember.builder().room(room).user(owner).joinedAt(Instant.now()).build();
        final RoomMember bobMember = RoomMember.builder().room(room).user(regularUser).joinedAt(Instant.now()).build();
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of(ownerMember, bobMember));
        when(roomMemberRepository.existsByRoomAndUser(room, owner)).thenReturn(true);

        final RoomResponse response = roomService.joinChatRoom(request);

        assertThat(response.user().username()).isEqualTo("bob_member");
        assertThat(response.user().isHost()).isFalse();
        assertThat(response.room().roomMembers()).hasSize(2);

        verify(roomMemberRepository).save(any(RoomMember.class));
        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId + "/presence"), eventCaptor.capture());

        RoomEvent event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo(RoomEvent.EventType.JOINED);
        assertThat(event.username()).isEqualTo("bob_member");
        assertThat(event.isHost()).isFalse();
    }

    @Test
    @DisplayName("joinChatRoom by roomId (UUID) allows joining via shared room ID")
    void testJoinChatRoom_ByRoomId() {
        final JoinRoom request = new JoinRoom(
                new JoinRoom.UserRequest("bob_member", "bob@example.com"),
                new JoinRoom.RoomDetail(null, roomId.toString())
        );

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(regularUser));
        when(roomRepository.findByRoomCode(roomId.toString())).thenReturn(Optional.empty());
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(roomMemberRepository.existsByRoomAndUser(room, regularUser)).thenReturn(false);
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of());

        final RoomResponse response = roomService.joinChatRoom(request);

        assertThat(response.room().id()).isEqualTo(roomId);
        verify(roomMemberRepository).save(any(RoomMember.class));
    }

    @Test
    @DisplayName("joinChatRoom throws RoomClosedException when room is CLOSED")
    void testJoinChatRoom_ClosedRoom_Fails() {
        room.setStatus(RoomStatus.CLOSED);

        final JoinRoom request = new JoinRoom(
                new JoinRoom.UserRequest("bob_member", "bob@example.com"),
                new JoinRoom.RoomDetail("123456")
        );

        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(regularUser));
        when(roomRepository.findByRoomCode("123456")).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.joinChatRoom(request))
                .isInstanceOf(RoomClosedException.class)
                .hasMessageContaining("the room is closed");
    }

    @Test
    @DisplayName("leaveRoom allows regular member to leave, notifies room, and room remains OPEN")
    void testLeaveRoom_RegularMember() {
        final OwnerDetail detail = new OwnerDetail("bob_member", "bob@example.com");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(regularUser));
        when(roomMemberRepository.existsByRoomAndUser(room, regularUser)).thenReturn(true);
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of());

        final RoomResponse response = roomService.leaveRoom(roomId, detail);

        assertThat(response.room().roomStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.user().isHost()).isFalse();

        verify(roomMemberRepository).deleteByRoomAndUser(room, regularUser);

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId + "/presence"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RoomEvent.EventType.LEFT);
        assertThat(eventCaptor.getValue().username()).isEqualTo("bob_member");
    }

    @Test
    @DisplayName("leaveRoom allows owner to leave, sets isHostOnline to false, but room remains OPEN")
    void testLeaveRoom_OwnerLeaves_RoomRemainsOpen() {
        final OwnerDetail detail = new OwnerDetail("alice_owner", "alice@example.com");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        when(roomMemberRepository.existsByRoomAndUser(room, owner)).thenReturn(true).thenReturn(false);
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of());

        final RoomResponse response = roomService.leaveRoom(roomId, detail);

        // Room must still be OPEN so other members can chat
        assertThat(response.room().roomStatus()).isEqualTo(RoomStatus.OPEN);
        assertThat(response.user().isHost()).isTrue();
        assertThat(response.room().isHostOnline()).isFalse();

        verify(roomMemberRepository).deleteByRoomAndUser(room, owner);

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId + "/presence"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RoomEvent.EventType.LEFT);
        assertThat(eventCaptor.getValue().isHost()).isTrue();
        assertThat(eventCaptor.getValue().isHostOnline()).isFalse();
    }

    @Test
    @DisplayName("closeRoom succeeds when requested by room owner")
    void testCloseRoom_ByOwner_Success() {
        final OwnerDetail detail = new OwnerDetail("alice_owner", "alice@example.com");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));
        when(roomMemberRepository.findByRoom_Id(roomId)).thenReturn(List.of());

        final RoomResponse response = roomService.closeRoom(roomId, detail);

        assertThat(response.room().roomStatus()).isEqualTo(RoomStatus.CLOSED);
        assertThat(response.room().closedBy()).isEqualTo("alice_owner");
        assertThat(response.room().closedAt()).isNotNull();

        ArgumentCaptor<RoomEvent> eventCaptor = ArgumentCaptor.forClass(RoomEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId + "/presence"), eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo(RoomEvent.EventType.CLOSED);
        assertThat(eventCaptor.getValue().roomStatus()).isEqualTo(RoomStatus.CLOSED);
    }

    @Test
    @DisplayName("closeRoom by non-owner is rejected with RoomOperationNotAllowedException")
    void testCloseRoom_ByNonOwner_Fails() {
        final OwnerDetail detail = new OwnerDetail("bob_member", "bob@example.com");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(regularUser));

        assertThatThrownBy(() -> roomService.closeRoom(roomId, detail))
                .isInstanceOf(RoomOperationNotAllowedException.class)
                .hasMessageContaining("Only the room owner who created the room can close this chat room");
    }

    @Test
    @DisplayName("closeRoom throws RoomClosedException if room is already CLOSED")
    void testCloseRoom_AlreadyClosed_Fails() {
        room.setStatus(RoomStatus.CLOSED);
        final OwnerDetail detail = new OwnerDetail("alice_owner", "alice@example.com");

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> roomService.closeRoom(roomId, detail))
                .isInstanceOf(RoomClosedException.class)
                .hasMessageContaining("already closed");
    }

    @Test
    @DisplayName("sendMessage succeeds for active member when room is OPEN")
    void testSendMessage_Success() {
        final MessageRequest request = new MessageRequest(roomId, regularUser.getId(), "Hello everyone!", MessageType.CHAT);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
        when(roomMemberRepository.existsByRoomAndUser(room, regularUser)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> {
            Message m = i.getArgument(0);
            m.setId(UUID.randomUUID());
            return m;
        });

        final MessageResponse response = roomService.sendMessage(roomId, request);

        assertThat(response).isNotNull();
        assertThat(response.content().message()).isEqualTo("Hello everyone!");
        assertThat(response.userDetail().username()).isEqualTo("bob_member");
        verify(messageRepository).save(any(Message.class));
    }

    @Test
    @DisplayName("sendMessage throws RoomClosedException when room is CLOSED")
    void testSendMessage_ClosedRoom_Fails() {
        room.setStatus(RoomStatus.CLOSED);
        final MessageRequest request = new MessageRequest(roomId, regularUser.getId(), "Hello?", MessageType.CHAT);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        assertThatThrownBy(() -> roomService.sendMessage(roomId, request))
                .isInstanceOf(RoomClosedException.class)
                .hasMessageContaining("is closed. No further messages can be sent.");
    }

    @Test
    @DisplayName("sendMessage throws BaseException when sender is not an active member")
    void testSendMessage_NonMember_Fails() {
        final MessageRequest request = new MessageRequest(roomId, regularUser.getId(), "Hello?", MessageType.CHAT);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(userRepository.findById(regularUser.getId())).thenReturn(Optional.of(regularUser));
        when(roomMemberRepository.existsByRoomAndUser(room, regularUser)).thenReturn(false);

        assertThatThrownBy(() -> roomService.sendMessage(roomId, request))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("is not an active member");
    }

    @Test
    @DisplayName("cleanupStaleUnclosedRooms auto-closes unclosed rooms and broadcasts CLOSED event")
    void testCleanupStaleUnclosedRooms() {
        final Instant cutoff = Instant.now().minusSeconds(86400);
        final ChatRoom staleRoom = ChatRoom.builder()
                .id(UUID.randomUUID())
                .roomCode("888999")
                .roomName("Stale Room")
                .status(RoomStatus.OPEN)
                .createdAt(cutoff.minusSeconds(100))
                .build();

        when(roomRepository.findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(RoomStatus.OPEN, cutoff))
                .thenReturn(List.of(staleRoom));
        when(roomRepository.save(any(ChatRoom.class))).thenAnswer(i -> i.getArgument(0));

        final int cleanedCount = roomService.cleanupStaleUnclosedRooms(cutoff);

        assertThat(cleanedCount).isEqualTo(1);
        assertThat(staleRoom.getStatus()).isEqualTo(RoomStatus.CLOSED);
        assertThat(staleRoom.getClosedAt()).isNotNull();

        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + staleRoom.getId() + "/presence"), any(RoomEvent.class));
        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + staleRoom.getId()), any(RoomEvent.class));
    }
}
