package com.pranit.connect.service.impl;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.Message;
import com.pranit.connect.entity.MessageType;
import com.pranit.connect.entity.RoomMember;
import com.pranit.connect.entity.User;
import com.pranit.connect.mail.properties.HostProperties;
import com.pranit.connect.repository.ChatRoomRepository;
import com.pranit.connect.repository.MessageRepository;
import com.pranit.connect.repository.RoomMemberRepository;
import com.pranit.connect.repository.UserRepository;
import com.pranit.connect.service.ChatBotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatBotServiceImpl implements ChatBotService {

    public static final String WELCOME_MESSAGE_TEXT = """
            Welcome to Open-Connect! 👋
            
            Hi there! I'm Pranit Bhangale. I've been notified that you created this room.
            Feel free to leave a message or share what's on your mind, and I'll jump into the conversation shortly!
            
            Best regards,
            Pranit Bhangale
            """.stripIndent().trim();

    private final UserRepository userRepository;
    private final ChatRoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MessageRepository messageRepository;
    private final HostProperties hostProperties;

    private final ConcurrentHashMap<UUID, Boolean> hostActiveRooms = new ConcurrentHashMap<>();

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void initializeRoomAndHost(final ChatRoom room, final User creator) {
        if (isHost(creator)) {
            markHostActive(room.getId());
            return;
        }
        final User hostUser = findOrCreateHostUser();
        if (!this.roomMemberRepository.existsByRoomAndUser(room, hostUser)) {
            this.roomMemberRepository.save(RoomMember.builder()
                    .room(room)
                    .user(hostUser)
                    .joinedAt(Instant.now())
                    .build());
        }
        final Message welcomeMsg = Message.builder()
                .room(room)
                .sender(hostUser)
                .content(WELCOME_MESSAGE_TEXT)
                .type(MessageType.CHAT)
                .build();
        this.messageRepository.save(welcomeMsg);
    }

    @Override
    public void handleVisitorMessage(final ChatRoom room, final User sender, final String content) {
        if (isHost(sender)) markHostActive(room.getId());
    }

    @Override
    public void markHostActive(final UUID roomId) {
        if (roomId != null) {
            this.hostActiveRooms.put(roomId, Boolean.TRUE);
            log.debug("Host marked ACTIVE in room {}", roomId);
        }
    }

    @Override
    public void markHostInactive(final UUID roomId) {
        if (roomId != null) {
            this.hostActiveRooms.remove(roomId);
            log.debug("Host marked INACTIVE in room {}", roomId);
        }
    }

    @Override
    public boolean isHostOnline(final UUID roomId) {
        return roomId != null && Boolean.TRUE.equals(this.hostActiveRooms.get(roomId));
    }

    @Override
    public boolean isHost(final String username, final String email) {
        if (username == null || email == null) return false;
        final String cleanUser = username.trim().toLowerCase();
        final String cleanEmail = email.trim().toLowerCase();
        final String targetEmail = hostProperties.email() != null && !hostProperties.email().isBlank()
                ? hostProperties.email().trim().toLowerCase() : "pranitbhangale14@gmail.com";
        final boolean emailMatches = cleanEmail.equals("pranitbhangale14@gmail.com") || cleanEmail.equals(targetEmail);
        final boolean nameMatches = cleanUser.equals("pranit")
                || cleanUser.startsWith("pranit ") || cleanUser.equals("pranit (host)");
        return emailMatches && nameMatches;
    }

    @Override
    public boolean isHost(final User user) {
        return user != null && isHost(user.getUsername(), user.getEmail());
    }

    @Override
    public User findHostUser() {
        final String targetEmail = hostProperties.email() != null && !hostProperties.email().isBlank()
                ? hostProperties.email().trim().toLowerCase() : "pranitbhangale14@gmail.com";
        return this.userRepository.findByEmail(targetEmail).orElse(null);
    }

    private User findOrCreateHostUser() {
        final User existing = findHostUser();
        if (existing != null) return existing;
        final String hostEmail = hostProperties.email() != null && !hostProperties.email().isBlank()
                ? hostProperties.email().trim().toLowerCase() : "pranitbhangale14@gmail.com";
        return this.userRepository.save(User.builder()
                .username("Pranit (Host)")
                .email(hostEmail)
                .build());
    }
}
