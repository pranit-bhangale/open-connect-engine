package com.pranit.connect.service;

import com.pranit.connect.dto.CreateRoom;
import com.pranit.connect.dto.JoinRoom;
import com.pranit.connect.dto.MessageRequest;
import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.OwnerDetail;
import com.pranit.connect.dto.RoomResponse;
import com.pranit.connect.wrapper.ScrollResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.UUID;

public interface RoomService {

    RoomResponse createNewRoom(@Valid CreateRoom request);

    RoomResponse getRoomDetailById(UUID roomId);

    RoomResponse joinChatRoom(@Valid JoinRoom request);

    RoomResponse closeRoom(UUID roomId, OwnerDetail detail);

    RoomResponse leaveRoom(UUID roomId, @Valid OwnerDetail detail);

    ScrollResponse<MessageResponse> getMessages(String keyword, String scrollId, @Min(1) @Max(5) int pageSize, String sortBy, String sortDirection, String scrollDirection);

    MessageResponse sendMessage(UUID roomId, @Valid MessageRequest request);
}
