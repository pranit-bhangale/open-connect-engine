package com.pranit.connect.controller;

import com.pranit.connect.dto.JoinRoom;
import com.pranit.connect.dto.MessageRequest;
import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.OwnerDetail;
import com.pranit.connect.dto.RoomResponse;
import com.pranit.connect.service.RoomService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;

import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
@Validated
public class ChatController {

    private final RoomService roomService;

    @MessageMapping("/sendMessage/{roomId}")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendMessage(
            @DestinationVariable @NotNull UUID roomId,
            @Payload @Valid MessageRequest request) {
        return this.roomService.sendMessage(roomId, request);
    }

    @MessageMapping("/room/{roomId}/send")
    @SendTo("/topic/room/{roomId}")
    public MessageResponse sendRoomMessage(
            @DestinationVariable @NotNull UUID roomId,
            @Payload @Valid MessageRequest request) {
        return this.roomService.sendMessage(roomId, request);
    }

    @MessageMapping("/room/join")
    public RoomResponse joinRoomViaSocket(@Payload @Valid JoinRoom request) {
        return this.roomService.joinChatRoom(request);
    }

    @MessageMapping("/room/{roomId}/leave")
    public RoomResponse leaveRoomViaSocket(
            @DestinationVariable @NotNull UUID roomId,
            @Payload @Valid OwnerDetail detail) {
        return this.roomService.leaveRoom(roomId, detail);
    }

    @MessageMapping("/room/{roomId}/close")
    public RoomResponse closeRoomViaSocket(
            @DestinationVariable @NotNull UUID roomId,
            @Payload @Valid OwnerDetail detail) {
        return this.roomService.closeRoom(roomId, detail);
    }
}
