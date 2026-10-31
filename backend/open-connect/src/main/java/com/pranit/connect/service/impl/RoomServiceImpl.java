package com.pranit.connect.service.impl;

import com.pranit.connect.dto.CreateRoom;
import com.pranit.connect.dto.JoinRoom;
import com.pranit.connect.dto.MessageRequest;
import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.OwnerDetail;
import com.pranit.connect.dto.RoomMemberResponse;
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
import com.pranit.connect.scroll.api.Scroll;
import com.pranit.connect.service.RoomService;
import com.pranit.connect.specification.EntitySpecification;
import com.pranit.connect.wrapper.ScrollResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final ChatRoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRepository messageRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse createNewRoom(final CreateRoom request) {
        final CreateRoom.UserRequest ur = request.user();
        final CreateRoom.RoomRequest rr = request.room();
        final User owner = findOrCreateUser(ur.email(), ur.username());
        final String roomCode = generateRoomCode();
        ChatRoom room = ChatRoom.builder()
                .roomCode(roomCode)
                .roomName(rr.roomName())
                .description(rr.description())
                .owner(owner)
                .status(RoomStatus.OPEN)
                .build();
        room = this.roomRepository.save(room);
        final RoomMember member = RoomMember.builder()
                .room(room)
                .user(owner)
                .joinedAt(Instant.now())
                .build();
        this.roomMemberRepository.save(member);
        publishPresenceEvent(room, owner, RoomEvent.EventType.JOINED);
        final List<RoomMemberResponse> memberResponses = mapMembers(room);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(true)
                        .build())
                .room(buildRoomDetail(room, memberResponses, true))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public RoomResponse getRoomDetailById(final UUID roomId) {
        final ChatRoom room = validateAndFetchRoomById(roomId);
        final User owner = room.getOwner();
        final boolean hostOnline = owner != null &&
                this.roomMemberRepository.existsByRoomAndUser(room, owner);
        final List<RoomMemberResponse> members = mapMembers(room);
        return RoomResponse.builder()
                .user(owner != null ? RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(true)
                        .build() : null)
                .room(buildRoomDetail(room, members, hostOnline))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public RoomResponse getRoomDetailByCode(final String roomCode) {
        final ChatRoom room = this.roomRepository.findByRoomCode(roomCode)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with code: " + roomCode));
        final User owner = room.getOwner();
        final boolean hostOnline = owner != null &&
                this.roomMemberRepository.existsByRoomAndUser(room, owner);
        final List<RoomMemberResponse> members = mapMembers(room);
        return RoomResponse.builder()
                .user(owner != null ? RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(true)
                        .build() : null)
                .room(buildRoomDetail(room, members, hostOnline))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse joinChatRoom(final JoinRoom request) {
        final JoinRoom.UserRequest ur = request.user();
        final JoinRoom.RoomDetail dr = request.detail();
        final User user = findOrCreateUser(ur.email(), ur.username());
        final String identifier = dr.getIdentifier();
        if (identifier == null || identifier.isBlank()) {
            throw new RoomNotFoundException("Room identifier (code or id) is required");
        }
        final ChatRoom room = resolveRoomByIdentifier(identifier);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("Cannot join room #" + room.getRoomCode() + ": the room is closed.");
        }
        final boolean isAlreadyMember = this.roomMemberRepository.existsByRoomAndUser(room, user);
        if (!isAlreadyMember) {
            final RoomMember member = RoomMember.builder()
                    .room(room)
                    .user(user)
                    .joinedAt(Instant.now())
                    .build();
            this.roomMemberRepository.save(member);
        }

        final User roomOwner = room.getOwner();
        final boolean isCallerOwner = roomOwner != null && user.getId().equals(roomOwner.getId());
        publishPresenceEvent(room, user, RoomEvent.EventType.JOINED);
        final boolean hostOnline = roomOwner != null &&
                this.roomMemberRepository.existsByRoomAndUser(room, roomOwner);
        final List<RoomMemberResponse> members = mapMembers(room);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .isHost(isCallerOwner)
                        .build())
                .room(buildRoomDetail(room, members, hostOnline))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse closeRoom(final UUID roomId, final OwnerDetail detail) {
        final ChatRoom room = validateAndFetchRoomById(roomId);
        final User closer = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        final User owner = room.getOwner();
        final boolean isOwner = owner != null && (
                owner.getId().equals(closer.getId()) ||
                owner.getEmail().trim().equalsIgnoreCase(closer.getEmail().trim())
        );
        if (!isOwner) {
            throw new RoomOperationNotAllowedException("Only the room owner who created the room can close this chat room.");
        }
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("Chat room #" + room.getRoomCode() + " is already closed.");
        }
        room.setStatus(RoomStatus.CLOSED);
        room.setClosedBy(closer);
        room.setClosedAt(Instant.now());
        final ChatRoom savedRoom = this.roomRepository.save(room);
        publishPresenceEvent(savedRoom, closer, RoomEvent.EventType.CLOSED);
        final List<RoomMemberResponse> memberResponses = mapMembers(savedRoom);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(true)
                        .build())
                .room(buildRoomDetail(savedRoom, memberResponses, false))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse leaveRoom(final UUID roomId, final OwnerDetail detail) {
        final ChatRoom room = validateAndFetchRoomById(roomId);
        final User leaver = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        if (!this.roomMemberRepository.existsByRoomAndUser(room, leaver)) {
            throw new RoomNotFoundException("User " + leaver.getUsername() + " is not a member of room: " + room.getRoomCode());
        }
        this.roomMemberRepository.deleteByRoomAndUser(room, leaver);
        final User owner = room.getOwner();
        final boolean isLeaverOwner = owner != null && (
                owner.getId().equals(leaver.getId()) ||
                owner.getEmail().trim().equalsIgnoreCase(leaver.getEmail().trim())
        );
        publishPresenceEvent(room, leaver, RoomEvent.EventType.LEFT);
        final boolean hostOnline = owner != null &&
                this.roomMemberRepository.existsByRoomAndUser(room, owner);
        final List<RoomMemberResponse> memberResponses = mapMembers(room);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(leaver.getId())
                        .username(leaver.getUsername())
                        .email(leaver.getEmail())
                        .isHost(isLeaverOwner)
                        .build())
                .room(buildRoomDetail(room, memberResponses, hostOnline))
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public ScrollResponse<MessageResponse> getMessages(
            final UUID roomId,
            final String userEmail,
            final String userName,
            final String keyword, final String scrollId,
            final int pageSize, final String sortBy,
            final String sortDirection, final String scrollDirection
    ) {
        validateAndFetchRoomById(roomId);
        Specification<Message> specification = EntitySpecification.hasRoomId(roomId)
                .and(EntitySpecification.hasMessageType(MessageType.CHAT));
        if (keyword != null && !keyword.isBlank()) {
            specification = specification.and(EntitySpecification.searchMessageKeyword(keyword));
        }
        final List<String> allowedSortProperties = List.of("createdAt", "id");
        return Scroll.scroll(this.messageRepository, specification, scrollId, pageSize, sortBy, sortDirection, scrollDirection,
                allowedSortProperties, MessageResponse::from);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public MessageResponse sendMessage(final UUID roomId, final MessageRequest request) {
        final ChatRoom room = validateAndFetchRoomById(roomId);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("Chat room #" + room.getRoomCode() + " is closed. No further messages can be sent.");
        }
        final User sender = this.userRepository.findById(request.senderId())
                .orElseThrow(() -> new RoomNotFoundException("Sender not found with ID: " + request.senderId()));
        if (!this.roomMemberRepository.existsByRoomAndUser(room, sender)) {
            throw new BaseException("User " + sender.getUsername() + " is not an active member of room: " + room.getRoomCode());
        }
        if (request.type() == MessageType.TYPING) {
            return MessageResponse.builder()
                    .id(UUID.randomUUID())
                    .roomDetail(MessageResponse.RoomDetail.builder()
                            .id(room.getId())
                            .roomCode(room.getRoomCode())
                            .roomName(room.getRoomName())
                            .description(room.getDescription())
                            .owner(room.getOwner() != null
                                    ? room.getOwner().getUsername() : null)
                            .roomStatus(room.getStatus())
                            .build())
                    .userDetail(MessageResponse.UserDetail.builder()
                            .id(sender.getId())
                            .username(sender.getUsername())
                            .build())
                    .content(MessageResponse.MessageContent.builder()
                            .message(request.content())
                            .messageType(MessageType.TYPING)
                            .build())
                    .sentAt(Instant.now())
                    .build();
        }
        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(request.content())
                .type(request.type() != null ? request.type() : MessageType.CHAT)
                .build();
        message = this.messageRepository.save(message);
        return MessageResponse.from(message);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public int cleanupStaleUnclosedRooms(final Instant cutoff) {
        final List<ChatRoom> staleRooms = this.roomRepository
                .findTop100ByStatusAndCreatedAtBeforeOrderByCreatedAtAsc(RoomStatus.OPEN, cutoff);
        if (staleRooms.isEmpty()) return 0;
        int count = 0;
        for (final ChatRoom room : staleRooms) {
            room.setStatus(RoomStatus.CLOSED);
            room.setClosedAt(Instant.now());
            this.roomRepository.save(room);
            final RoomEvent closeEvent = RoomEvent.builder()
                    .roomId(room.getId())
                    .roomCode(room.getRoomCode())
                    .eventType(RoomEvent.EventType.CLOSED)
                    .message("Room was automatically closed due to inactivity timeout.")
                    .timestamp(Instant.now())
                    .roomStatus(RoomStatus.CLOSED)
                    .isHost(false)
                    .isHostOnline(false)
                    .memberCount(0)
                    .build();
            this.messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/presence", closeEvent);
            this.messagingTemplate.convertAndSend("/topic/room/" + room.getId(), closeEvent);
            count++;
        }
        log.info("Cleaned up and auto-closed {} stale unclosed room(s) created before {}", count, cutoff);
        return count;
    }

    private ChatRoom validateAndFetchRoomById(final UUID roomId) {
        return this.roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));
    }

    private ChatRoom resolveRoomByIdentifier(final String identifier) {
        final var byCode = this.roomRepository.findByRoomCode(identifier);
        if (byCode.isPresent()) return byCode.get();
        try {
            final UUID uuid = UUID.fromString(identifier);
            final var byId = this.roomRepository.findById(uuid);
            if (byId.isPresent()) return byId.get();
        } catch (IllegalArgumentException ignored) {
        }
        throw new RoomNotFoundException("Room not found with code or ID: " + identifier);
    }

    private User findOrCreateUser(final String email, final String username) {
        return this.userRepository.findByEmail(email)
                .map(existing -> {
                    if (!existing.getUsername().equals(username)) {
                        existing.setUsername(username);
                        return this.userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> {
                    try {
                        return this.userRepository.save(User.builder()
                                .username(username)
                                .email(email)
                                .build());
                    } catch (DataIntegrityViolationException ex) {
                        return this.userRepository.findByEmail(email).orElseThrow(() -> ex);
                    }
                });
    }

    private String generateRoomCode() {
        String code;
        do {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (this.roomRepository.existsByRoomCode(code));
        return code;
    }

    private void publishPresenceEvent(final ChatRoom room, final User user, final RoomEvent.EventType eventType) {
        final boolean isOwner = room.getOwner() != null && user != null && room.getOwner().getId().equals(user.getId());
        publishPresenceEvent(room, user != null ? user.getId() : null, user != null ? user.getUsername() : null, eventType, isOwner);
    }

    private void publishPresenceEvent(final ChatRoom room, final UUID userId, final String username, final RoomEvent.EventType eventType, final boolean isHost) {
        final boolean hostOnline = room.getOwner() != null &&
                this.roomMemberRepository.existsByRoomAndUser(room, room.getOwner());
        final int memberCount = this.roomMemberRepository.findByRoom_Id(room.getId()).size();
        final String displayName = username != null ? username : "User";
        final String noticeMessage = switch (eventType) {
            case JOINED -> (isHost ? "Host " : "") + displayName + " joined the room.";
            case LEFT -> (isHost ? "Host " : "") + displayName + " left the room.";
            case CLOSED -> "Room has been closed by owner " + displayName + ". No further messages can be sent.";
            default -> eventType.name();
        };
        final RoomEvent event = RoomEvent.builder()
                .roomId(room.getId())
                .roomCode(room.getRoomCode())
                .userId(userId)
                .username(username)
                .eventType(eventType)
                .message(noticeMessage)
                .timestamp(Instant.now())
                .roomStatus(room.getStatus())
                .isHost(isHost)
                .isHostOnline(hostOnline)
                .memberCount(memberCount)
                .build();
        this.messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/presence", event);
        this.messagingTemplate.convertAndSend("/topic/room/" + room.getId(), event);
    }

    private List<RoomMemberResponse> mapMembers(final ChatRoom room) {
        final User owner = room.getOwner();
        return this.roomMemberRepository.findByRoom_Id(room.getId())
                .stream()
                .map(m -> RoomMemberResponse.builder()
                        .roomId(m.getRoom().getId())
                        .roomName(m.getRoom().getRoomName())
                        .userId(m.getUser().getId())
                        .userName(m.getUser().getUsername())
                        .userEmail(m.getUser().getEmail())
                        .isHost(owner != null && m.getUser().getId().equals(owner.getId()))
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
    }

    private RoomResponse.RoomDetail buildRoomDetail(final ChatRoom room, final List<RoomMemberResponse> members, final boolean hostOnline) {
        final User owner = room.getOwner();
        return RoomResponse.RoomDetail.builder()
                .id(room.getId())
                .roomCode(room.getRoomCode())
                .roomName(room.getRoomName())
                .description(room.getDescription())
                .owner(owner != null ? owner.getUsername() : "")
                .roomStatus(room.getStatus())
                .isHostOnline(hostOnline)
                .closedBy(room.getClosedBy() != null ? room.getClosedBy().getUsername() : null)
                .closedAt(room.getClosedAt())
                .roomMembers(members)
                .createdAt(room.getCreatedAt())
                .build();
    }
}
