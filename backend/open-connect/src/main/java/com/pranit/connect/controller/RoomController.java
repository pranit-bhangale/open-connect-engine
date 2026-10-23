package com.pranit.connect.controller;

import com.pranit.connect.dto.CreateRoom;
import com.pranit.connect.dto.JoinRoom;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping(value = "/open", version = "v1")
    public ResponseEntity<RoomResponse> createNewRoom(@RequestBody @Valid CreateRoom request) {
        final RoomResponse response = this.roomService.createNewRoom(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping(value = "/{id}", version = "v1")
    public ResponseEntity<RoomResponse> getPerRoomDetail(@PathVariable("id") UUID roomId) {
        final RoomResponse response = roomService.getRoomDetailById(roomId);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/join", version = "v1")
    public ResponseEntity<RoomResponse> joinChatRoom(@RequestBody @Valid JoinRoom request) {
        final RoomResponse response = this.roomService.joinChatRoom(request);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping(value = "/{id}/close", version = "v1")
    public ResponseEntity<RoomResponse> closeChatRoom(@PathVariable("id") UUID roomId, @RequestBody @Valid OwnerDetail detail) {
        final RoomResponse response = this.roomService.closeRoom(roomId, detail);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PatchMapping(value = "/{id}/leave", version = "v1")
    public ResponseEntity<RoomResponse> leaveChatRoom(@PathVariable("id") UUID roomId, @RequestBody @Valid OwnerDetail detail) {
        final RoomResponse response = this.roomService.leaveRoom(roomId, detail);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @GetMapping(value = "/messages", version = "v1")
    public ResponseEntity<ScrollResponse<MessageResponse>> getMessages(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String scrollId,
            @RequestParam(defaultValue = "5") @Min(1) @Max(5) int pageSize,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection,
            @RequestParam(defaultValue = "forward") String scrollDirection
    ) {
        final ScrollResponse<MessageResponse> response = this.roomService.getMessages
                (keyword, scrollId, pageSize, sortBy, sortDirection, scrollDirection);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
