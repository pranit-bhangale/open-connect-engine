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
import com.pranit.connect.mail.event.RoomCreatedEvent;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import com.pranit.connect.repository.UserRepository;
import com.pranit.connect.scroll.api.Scroll;
import com.pranit.connect.service.ChatBotService;
import com.pranit.connect.service.RoomService;
import com.pranit.connect.specification.EntitySpecification;
import com.pranit.connect.wrapper.ScrollResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final ApplicationEventPublisher eventPublisher;
    private final ChatBotService chatBotService;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public RoomResponse createNewRoom(final CreateRoom request) {
        final CreateRoom.UserRequest ur = request.user();
        final CreateRoom.RoomRequest rr = request.room();
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
        final String roomCode = generateRoomCode();
        ChatRoom room = ChatRoom.builder()
                .roomCode(roomCode)
                .roomName(rr.roomName())
                .description(rr.description())
                .owner(owner)
                .status(RoomStatus.OPEN)
                .build();
        room = roomRepository.save(room);
        final RoomMember member = RoomMember.builder()
                .room(room)
                .user(owner)
                .joinedAt(Instant.now())
                .build();
        roomMemberRepository.save(member);
        this.chatBotService.initializeRoomAndHost(room, owner);
        publishPresenceEvent(room, member.getUser(), RoomEvent.EventType.JOINED);
        this.eventPublisher.publishEvent(RoomCreatedEvent.builder()
                .creatorName(owner.getUsername())
                .creatorEmail(owner.getEmail())
                .roomName(room.getRoomName())
                .roomCode(room.getRoomCode())
                .build());
        final List<RoomMemberResponse> memberResponses = this.roomMemberRepository
                .findByRoom_Id(room.getId())
                .stream()
                .map(m -> RoomMemberResponse.builder()
                        .roomId(m.getRoom().getId())
                        .roomName(m.getRoom().getRoomName())
                        .userId(m.getUser().getId())
                        .userName(m.getUser().getUsername())
                        .isHost(this.chatBotService.isHost(m.getUser()))
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(this.chatBotService.isHost(owner))
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .isHostOnline(this.chatBotService.isHostOnline(room.getId()))
                        .closedBy(room.getClosedBy() != null
                                ? room.getClosedBy().getUsername() : null)
                        .closedAt(room.getClosedAt())
                        .roomMembers(memberResponses)
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

    private void publishPresenceEvent(final ChatRoom room, final User user, final RoomEvent.EventType eventType) {
        publishPresenceEvent(room, user.getId(), user.getUsername(), eventType, this.chatBotService.isHost(user));
    }

    private void publishPresenceEvent(final ChatRoom room, final UUID userId, final String username, final RoomEvent.EventType eventType, final boolean isHost) {
        final boolean hostOnline = this.chatBotService.isHostOnline(room.getId());
        this.messagingTemplate.convertAndSend("/topic/room/" + room.getId() + "/presence",
                RoomEvent.builder()
                        .roomId(room.getId())
                        .userId(userId)
                        .username(username)
                        .eventType(eventType)
                        .timestamp(Instant.now())
                        .roomStatus(room.getStatus())
                        .isHost(isHost)
                        .isHostOnline(hostOnline)
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
                        .isHost(this.chatBotService.isHost(member.getUser()))
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(this.chatBotService.isHost(owner))
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .isHostOnline(this.chatBotService.isHostOnline(room.getId()))
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
        final ChatRoom room = this.roomRepository.findByRoomCode(dr.roomCode())
                .orElseThrow(() -> new RoomNotFoundException("Room not found with code: " + dr.roomCode()));
        if (room.getStatus() != RoomStatus.OPEN) {
            throw new RoomClosedException("Cannot join a closed room");
        }
        final boolean isMember = this.roomMemberRepository.existsByRoomAndUser(room, user);
        if (!isMember) {
            final RoomMember member = RoomMember.builder()
                    .room(room)
                    .user(user)
                    .joinedAt(Instant.now())
                    .build();
            this.roomMemberRepository.save(member);
        }
        if (this.chatBotService.isHost(user)) this.chatBotService.markHostActive(room.getId());
        publishPresenceEvent(room, user, RoomEvent.EventType.JOINED);
        final List<RoomMemberResponse> members = this.roomMemberRepository
                .findByRoom_Id(room.getId())
                .stream()
                .map(m -> RoomMemberResponse.builder()
                        .roomId(room.getId())
                        .roomName(room.getRoomName())
                        .userId(m.getUser().getId())
                        .userName(m.getUser().getUsername())
                        .isHost(this.chatBotService.isHost(m.getUser()))
                        .joinedAt(m.getJoinedAt())
                        .build())
                .toList();
        final User roomOwner = room.getOwner();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .isHost(this.chatBotService.isHost(user))
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(roomOwner.getUsername())
                        .roomStatus(room.getStatus())
                        .isHostOnline(this.chatBotService.isHostOnline(room.getId()))
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
        final User closer = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        if (!this.chatBotService.isHost(closer)) {
            throw new RoomClosedException("Only the portfolio owner can close chat rooms.");
        }
        this.chatBotService.markHostInactive(roomId);
        if (room.getStatus() != RoomStatus.CLOSED) {
            room.setStatus(RoomStatus.CLOSED);
            room.setClosedBy(closer);
            room.setClosedAt(Instant.now());
        }
        final ChatRoom savedRoom = roomRepository.save(room);
        final User owner = room.getOwner();
        final List<RoomMemberResponse> memberResponses = this.roomMemberRepository
                .findByRoom_Id(room.getId())
                .stream()
                .map(member -> RoomMemberResponse.builder()
                        .roomId(savedRoom.getId())
                        .roomName(savedRoom.getRoomName())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getUsername())
                        .isHost(this.chatBotService.isHost(member.getUser()))
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        publishPresenceEvent(room, closer, RoomEvent.EventType.CLOSED);
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(this.chatBotService.isHost(owner))
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .isHostOnline(false)
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
        final User leaver = this.userRepository.findByEmail(detail.email())
                .orElseThrow(() -> new RoomNotFoundException("User not found with email: " + detail.email()));
        if (!this.roomMemberRepository.existsByRoomAndUser(room, leaver)) {
            throw new RoomNotFoundException("User is not a member of room: " + room.getRoomCode());
        }
        this.roomMemberRepository.deleteByRoomAndUser(room, leaver);
        if (this.chatBotService.isHost(leaver)) {
            this.chatBotService.markHostInactive(roomId);
        }
        publishPresenceEvent(room, leaver, RoomEvent.EventType.LEFT);
        final List<RoomMemberResponse> memberResponses = this.roomMemberRepository
                .findByRoom_Id(room.getId())
                .stream()
                .map(member -> RoomMemberResponse.builder()
                        .roomId(room.getId())
                        .roomName(room.getRoomName())
                        .userId(member.getUser().getId())
                        .userName(member.getUser().getUsername())
                        .isHost(this.chatBotService.isHost(member.getUser()))
                        .joinedAt(member.getJoinedAt())
                        .build())
                .toList();
        final User owner = room.getOwner();
        return RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(owner.getId())
                        .username(owner.getUsername())
                        .email(owner.getEmail())
                        .isHost(this.chatBotService.isHost(owner))
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(room.getId())
                        .roomCode(room.getRoomCode())
                        .roomName(room.getRoomName())
                        .description(room.getDescription())
                        .owner(owner.getUsername())
                        .roomStatus(room.getStatus())
                        .isHostOnline(this.chatBotService.isHostOnline(room.getId()))
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
        final boolean callerIsHost;
        if (userName != null && userEmail != null) {
            callerIsHost = this.chatBotService.isHost(userName, userEmail);
        } else if (userEmail != null) {
            callerIsHost = this.userRepository.findByEmail(userEmail)
                    .map(this.chatBotService::isHost)
                    .orElse(false);
        } else {
            callerIsHost = false;
        }
        if (callerIsHost) {
            final boolean wasOnline = this.chatBotService.isHostOnline(roomId);
            this.chatBotService.markHostActive(roomId);
            if (!wasOnline) {
                final User hostUser = this.chatBotService.findHostUser();
                final UUID hId = hostUser != null ? hostUser.getId() : UUID.randomUUID();
                final String hName = hostUser != null ? hostUser.getUsername() : "Pranit (Host)";
                this.messagingTemplate.convertAndSend("/topic/room/" + roomId + "/presence",
                        RoomEvent.builder()
                                .roomId(roomId)
                                .userId(hId)
                                .username(hName)
                                .eventType(RoomEvent.EventType.HOST_STATUS)
                                .timestamp(Instant.now())
                                .roomStatus(RoomStatus.OPEN)
                                .isHost(true)
                                .isHostOnline(true)
                                .build());
            }
            specification = specification.and((root, _, cb) ->
                    cb.and(
                            cb.notLike(root.get("content"), "%Welcome to Open-Connect!%"),
                            cb.notLike(root.get("content"), "%currently away/offline%"),
                            cb.notLike(root.get("content"), "%noted that you're here%"),
                            cb.notLike(root.get("content"), "%notified that you're here%"),
                            cb.notLike(root.get("content"), "%notified on my phone%")
                    ));
        }
        final List<String> allowedSortProperties = List.of("createdAt", "id");
        final ScrollResponse<MessageResponse> response = Scroll.scroll(this.messageRepository, specification, scrollId, pageSize, sortBy, sortDirection, scrollDirection,
                allowedSortProperties, MessageResponse::from);
        if (callerIsHost && response.contents() != null) {
            final List<MessageResponse> filtered = response.contents().stream()
                    .filter(m -> {
                        if (m.content() == null || m.content().message() == null) return true;
                        final String text = m.content().message();
                        return !text.contains("Welcome to Open-Connect!") &&
                                !text.contains("currently away/offline") &&
                                !text.contains("noted that you're here") &&
                                !text.contains("notified that you're here") &&
                                !text.contains("notified on my phone");
                    })
                    .toList();
            return ScrollResponse.<MessageResponse>builder()
                    .contents(filtered)
                    .nextScrollId(response.nextScrollId())
                    .prevScrollId(response.prevScrollId())
                    .hasNext(response.hasNext())
                    .hasPrevious(response.hasPrevious())
                    .pageSize(response.pageSize())
                    .build();
        }
        return response;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public MessageResponse sendMessage(final UUID roomId, final MessageRequest request) {
        final ChatRoom room = validateAndFetchRoomById(roomId);
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new RoomClosedException("Chat room #" + roomId + " is closed. No further messages can be sent.");
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
                .type(request.type())
                .build();
        message = this.messageRepository.save(message);
        final MessageResponse response = MessageResponse.from(message);
        this.chatBotService.handleVisitorMessage(room, sender, request.content());
        return response;
    }

    private ChatRoom validateAndFetchRoomById(final UUID roomId) {
        return this.roomRepository.findById(roomId)
                .orElseThrow(() -> new RoomNotFoundException("Room not found with ID: " + roomId));
    }
}
