package com.pranit.connect.controller;

import com.pranit.connect.dto.CreateRoom;
import com.pranit.connect.dto.JoinRoom;
import com.pranit.connect.dto.MessageRequest;
import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.OwnerDetail;
import com.pranit.connect.dto.RoomResponse;
import com.pranit.connect.service.RoomService;
import com.pranit.connect.wrapper.ScrollResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
@Validated
public class RoomController {

    private final RoomService roomService;
    private final SimpMessageSendingOperations messagingTemplate;

    @PostMapping(value = "/open", version = "v1")
    public ResponseEntity<RoomResponse> createNewRoom(@RequestBody @Valid CreateRoom request) {
        final RoomResponse response = this.roomService.createNewRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{id}", version = "v1")
    public ResponseEntity<RoomResponse> getPerRoomDetail(@PathVariable("id") UUID roomId) {
        final RoomResponse response = this.roomService.getRoomDetailById(roomId);
        return ResponseEntity.ok(response);
    }

    @GetMapping(value = "/code/{roomCode}", version = "v1")
    public ResponseEntity<RoomResponse> getRoomDetailByCode(@PathVariable("roomCode") String roomCode) {
        final RoomResponse response = this.roomService.getRoomDetailByCode(roomCode);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/join", version = "v1")
    public ResponseEntity<RoomResponse> joinChatRoom(@RequestBody @Valid JoinRoom request) {
        final RoomResponse response = this.roomService.joinChatRoom(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping(value = "/{id}/close", version = "v1")
    public ResponseEntity<RoomResponse> closeChatRoom(
            @PathVariable("id") UUID roomId,
            @RequestBody @Valid OwnerDetail detail) {
        final RoomResponse response = this.roomService.closeRoom(roomId, detail);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping(value = "/{id}/leave", version = "v1")
    public ResponseEntity<RoomResponse> leaveChatRoom(
            @PathVariable("id") UUID roomId,
            @RequestBody @Valid OwnerDetail detail) {
        final RoomResponse response = this.roomService.leaveRoom(roomId, detail);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping(value = "/{id}/messages", version = "v1")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable("id") UUID roomId,
            @RequestBody @Valid MessageRequest request) {
        final MessageResponse response = this.roomService.sendMessage(roomId, request);
        this.messagingTemplate.convertAndSend("/topic/room/" + roomId, response);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{id}/messages", version = "v1")
    public ResponseEntity<ScrollResponse<MessageResponse>> getMessages(
            @PathVariable("id") UUID roomId,
            @RequestParam(required = false) String userName,
            @RequestHeader(value = "X-User-Name", required = false) String headerName,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scrollId,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            @RequestParam(defaultValue = "forward") String scrollDirection
    ) {
        final String effectiveName = (userName != null && !userName.isBlank())
                ? userName.trim() : (headerName != null && !headerName.isBlank()
                ? headerName.trim() : null);
        final ScrollResponse<MessageResponse> response = this.roomService.getMessages
                (roomId, effectiveName, keyword, scrollId, pageSize, sortBy, sortDirection, scrollDirection);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
