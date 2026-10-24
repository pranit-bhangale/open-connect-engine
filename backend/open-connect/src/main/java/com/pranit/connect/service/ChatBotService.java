package com.pranit.connect.service;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.User;

import java.util.UUID;

public interface ChatBotService {

    void initializeRoomAndHost(ChatRoom room, User creator);

    void handleVisitorMessage(ChatRoom room, User sender, String content);

    void markHostActive(UUID roomId);

    void markHostInactive(UUID roomId);

    boolean isHostOnline(UUID roomId);

    boolean isHost(String username, String email);

    boolean isHost(User user);

    User findHostUser();
}
