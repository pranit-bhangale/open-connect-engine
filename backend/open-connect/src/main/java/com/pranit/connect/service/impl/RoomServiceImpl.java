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
import com.pranit.connect.entity.RoomMember;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.entity.User;
import com.pranit.connect.event.RoomEvent;
import com.pranit.connect.exception.BaseException;
import com.pranit.connect.exception.RoomClosedException;
import com.pranit.connect.exception.RoomNotFoundException;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import com.pranit.connect.repository.UserRepository;
import com.pranit.connect.scroll.api.Scroll;
import com.pranit.connect.service.RoomService;
import com.pranit.connect.specification.EntitySpecification;
import com.pranit.connect.wrapper.ScrollResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

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
        // Look up or create/update user from the request
        final User owner = userRepository.findByEmail(ur.email())
                .map(existing -> {
                    if (!existing.getUsername().equals(ur.username())) {
                        existing.setUsername(ur.username());
                        return userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.builder()
                        .username(ur.username())
                        .email(ur.email())
                        .build()));
        // Generate a unique room code
        final String roomCode = generateRoomCode();
        // Create and save the chat room
        ChatRoom room = ChatRoom.builder()
                .roomCode(roomCode)
                .roomName(rr.roomName())
                .description(rr.description())
                .owner(owner)
                .status(RoomStatus.OPEN)
                .build();
        room = roomRepository.save(room);
        // Add owner as room member
        RoomMember member = RoomMember.builder()
                .room(room)
                .user(owner)
                .joinedAt(Instant.now())
                .build();
        member = roomMemberRepository.save(member);
        // Build RoomMemberResponse for the response
        final RoomMemberResponse memberResponse = RoomMemberResponse.builder()
                .roomId(room.getId())
                .roomName(room.getRoomName())
                .userId(owner.getId())
                .userName(owner.getUsername())
                .joinedAt(member.getJoinedAt())
                .build();
        publishPresenceEvent(room, member.getUser().getId(), member.getUser().getUsername(), RoomEvent.EventType.JOINED);
        // Build and return RoomResponse
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(Collections.singletonList(memberResponse))
                        .createdAt(room.getCreatedAt())
                        .build())
                .build();
    }

    private String generateRoomCode() {
        String code;
        do {
            code = String.format("%06d", ThreadLocalRandom.current().nextInt(100000, 1000000));
        } while (roomRepository.existsByRoomCode(code));
        return code;
    }

    private void publishPresenceEvent(ChatRoom room, UUID userId, String username, RoomEvent.EventType eventType) {
        messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/presence",
                RoomEvent.builder()
                        .roomId(room.getId())
                        .userId(userId)
                        .username(username)
                        .eventType(eventType)
                        .timestamp(Instant.now())
                        .roomStatus(room.getStatus())
                        .build());
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public RoomResponse getRoomDetailById(final UUID roomId) {
        final ChatRoom room = this.validateAndFetchRoomById(roomId);
        final User owner = room.getOwner();
        final List<RoomMemberResponse> members = this.roomMemberRepository.findByRoom_Id(room.getId())
                .stream()
                .map(member -> RoomMemberResponse.builder()
                        .roomId(room.getId())
                        .roomName(room.getRoomName())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getUsername())
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(members)
                        .createdAt(room.getCreatedAt())
                        .build())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse joinChatRoom(final JoinRoom request) {
        final JoinRoom.UserRequest ur = request.user();
        final JoinRoom.RoomDetail dr = request.detail();
        // Look up or create/update user from the request
        final User user = this.userRepository.findByEmail(ur.email())
                .map(existing -> {
                    if (!existing.getUsername().equals(ur.username())) {
                        existing.setUsername(ur.username());
                        return this.userRepository.save(existing);
                    }
                    return existing;
                })
                .orElseGet(() -> this.userRepository.save(User.builder()
                        .username(ur.username())
                        .email(ur.email())
                        .build()));
        // Find room by roomCode
        final ChatRoom room = this.roomRepository.findByRoomCode(dr.roomCode())
                .orElseThrow(() -> new RoomNotFoundException("Room not found with code: " + dr.roomCode()));
        // Check room status
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new RoomClosedException("Cannot join a closed room");
        }
        // Check if user is already a member
        final boolean isMember = this.roomMemberRepository.existsByRoomAndUser(room, user);
        if (!isMember) {
            final RoomMember member = RoomMember.builder()
                    .room(room)
                    .user(user)
                    .joinedAt(Instant.now())
                    .build();
            this.roomMemberRepository.save(member);
        }
        publishPresenceEvent(room, user.getId(), user.getUsername(), RoomEvent.EventType.JOINED);
        // Fetch all room members
        final List<RoomMemberResponse> members = this.roomMemberRepository.findByRoom_Id(room.getId())
                .stream()
                .map(m -> RoomMemberResponse.builder()
                        .roomId(room.getId())
                        .roomName(room.getRoomName())
                        .userId(m.getUser().getId())
                        .userName(m.getUser().getUsername())
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
        final User roomOwner = room.getOwner();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(roomOwner.getUsername())
                        .roomStatus(room.getStatus())
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(members)
                        .createdAt(room.getCreatedAt())
                        .build())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse closeRoom(final UUID roomId, final OwnerDetail detail) {
        final ChatRoom room = this.validateAndFetchRoomById(roomId);
        // Look up the user trying to close the room
        final User closer = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        // Only the room owner can close the room
        if (!room.getOwner().getId().equals(closer.getId())) {
            throw new RoomClosedException("Only the room owner can close the room");
        }
        if (room.getStatus() != RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.CLOSED);
            room.setClosedBy(closer);
            room.setClosedAt(Instant.now());
        }
        final ChatRoom savedRoom = roomRepository.save(room);
        final User owner = room.getOwner();
        final List<RoomMemberResponse> memberResponses = this.roomMemberRepository.findByRoom_Id(room.getId())
                .stream()
                .map(member -> RoomMemberResponse.builder()
                        .roomId(savedRoom.getId())
                        .roomName(savedRoom.getRoomName())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getUsername())
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        publishPresenceEvent(room, closer.getId(), closer.getUsername(), RoomEvent.EventType.CLOSED);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(memberResponses)
                        .createdAt(room.getCreatedAt())
                        .build())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse leaveRoom(final UUID roomId, final OwnerDetail detail) {
        final ChatRoom room = this.validateAndFetchRoomById(roomId);
        // Look up the user trying to leave
        final User leaver = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        if (!this.roomMemberRepository.existsByRoomAndUser(room, leaver)) {
            throw new RoomNotFoundException("User is not a member of room: " + room.getRoomCode());
        }
        // Remove user from room members
        this.roomMemberRepository.deleteByRoomAndUser(room, leaver);
        // Publish LEFT event to all users in the room
        publishPresenceEvent(room, leaver.getId(), leaver.getUsername(), RoomEvent.EventType.LEFT);
        // Fetch remaining members
        final List<RoomMemberResponse> memberResponses = this.roomMemberRepository.findByRoom_Id(room.getId())
                .stream()
                .map(member -> RoomMemberResponse.builder()
                        .roomId(room.getId())
                        .roomName(room.getRoomName())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getUsername())
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        final User owner = room.getOwner();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(memberResponses)
                        .createdAt(room.getCreatedAt())
                        .build())
                .build();
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public ScrollResponse<MessageResponse> getMessages(
            final UUID roomId,
            final String keyword, final String scrollId,
            final int pageSize, final String sortBy,
            final String sortDirection, final String scrollDirection
    ) {
        validateAndFetchRoomById(roomId);
        Specification<Message> specification = EntitySpecification.hasRoomId(roomId);
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
            throw new RoomClosedException("Chat room #" + roomId + " is closed. No further messages can be sent.");
        }
        // Look up the sender user
        final User sender = this.userRepository.findById(request.senderId())
                .orElseThrow(() -> new RoomNotFoundException("Sender not found with ID: " + request.senderId()));
        // Ensure sender is a member of the room
        if (!this.roomMemberRepository.existsByRoomAndUser(room, sender)) {
            throw new BaseException("User " + sender.getUsername() + " is not an active member of room: " + room.getRoomCode());
        }
        // Create and save the message
        Message message = Message.builder()
                .room(room)
                .sender(sender)
                .content(request.content())
                .type(request.type())
                .build();
        message = this.messageRepository.save(message);
        return MessageResponse.from(message);
    }

    private ChatRoom validateAndFetchRoomById(final UUID roomId) {
        return this.roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));
    }
}
