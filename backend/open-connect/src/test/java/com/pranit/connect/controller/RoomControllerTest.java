package com.pranit.connect.controller;

import com.pranit.connect.dto.MessageResponse;
import com.pranit.connect.dto.RoomResponse;
import com.pranit.connect.entity.MessageType;
import com.pranit.connect.entity.RoomStatus;
import com.pranit.connect.exception.GlobalExceptionHandler;
import com.pranit.connect.exception.RoomClosedException;
import com.pranit.connect.exception.RoomOperationNotAllowedException;
import com.pranit.connect.service.RoomService;
import com.pranit.connect.version.config.VersionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.accept.ApiVersionStrategy;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RoomControllerTest {

    private MockMvc mockMvc;

    @Mock
    private RoomService roomService;

    @Mock
    private SimpMessageSendingOperations messagingTemplate;

    @InjectMocks
    private RoomController roomController;

    private UUID roomId;

    static class TestVersionConfigurer extends ApiVersionConfigurer {
        public ApiVersionStrategy getStrategy() {
            return getApiVersionStrategy();
        }
    }

    @BeforeEach
    void setUp() {
        final TestVersionConfigurer configurer = new TestVersionConfigurer();
        new VersionConfig().configureApiVersioning(configurer);

        mockMvc = MockMvcBuilders.standaloneSetup(roomController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setApiVersionStrategy(configurer.getStrategy())
                .build();
        roomId = UUID.randomUUID();
    }

    @Test
    @DisplayName("POST /api/rooms/open creates a new room")
    void testCreateNewRoom() throws Exception {
        final String json = """
                {
                    "user": {
                        "username": "alice"
                    },
                    "room": {
                        "roomName": "Engineering",
                        "description": "Tech chat"
                    }
                }
                """;

        final RoomResponse response = RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(UUID.randomUUID())
                        .username("alice")
                        .isHost(true)
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomCode("456789")
                        .roomName("Engineering")
                        .roomStatus(RoomStatus.OPEN)
                        .isHostOnline(true)
                        .roomMembers(List.of())
                        .build())
                .build();

        when(roomService.createNewRoom(any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms/open")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.username").value("alice"))
                .andExpect(jsonPath("$.user.isHost").value(true))
                .andExpect(jsonPath("$.room.roomCode").value("456789"))
                .andExpect(jsonPath("$.room.roomStatus").value("OPEN"));
    }

    @Test
    @DisplayName("GET /api/rooms/{id} returns room details")
    void testGetRoomById() throws Exception {
        final RoomResponse response = RoomResponse.builder()
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomCode("456789")
                        .roomName("Engineering")
                        .roomStatus(RoomStatus.OPEN)
                        .isHostOnline(true)
                        .build())
                .build();

        when(roomService.getRoomDetailById(roomId)).thenReturn(response);

        mockMvc.perform(get("/api/rooms/" + roomId)
                        .header("X-API-Version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.id").value(roomId.toString()))
                .andExpect(jsonPath("$.room.roomCode").value("456789"));
    }

    @Test
    @DisplayName("GET /api/rooms/code/{roomCode} returns room details by code")
    void testGetRoomByCode() throws Exception {
        final RoomResponse response = RoomResponse.builder()
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomCode("456789")
                        .roomName("Engineering")
                        .roomStatus(RoomStatus.OPEN)
                        .isHostOnline(true)
                        .build())
                .build();

        when(roomService.getRoomDetailByCode("456789")).thenReturn(response);

        mockMvc.perform(get("/api/rooms/code/456789")
                        .header("X-API-Version", "v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.roomCode").value("456789"));
    }

    @Test
    @DisplayName("POST /api/rooms/join joins room")
    void testJoinRoom() throws Exception {
        final String json = """
                {
                    "user": {
                        "username": "bob"
                    },
                    "detail": {
                        "roomCode": "456789"
                    }
                }
                """;

        final RoomResponse response = RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .id(UUID.randomUUID())
                        .username("bob")
                        .isHost(false)
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomCode("456789")
                        .roomStatus(RoomStatus.OPEN)
                        .build())
                .build();

        when(roomService.joinChatRoom(any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value("bob"))
                .andExpect(jsonPath("$.user.isHost").value(false));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/close by owner succeeds")
    void testCloseRoom_ByOwner() throws Exception {
        final String json = """
                {
                    "username": "alice"
                }
                """;

        final RoomResponse response = RoomResponse.builder()
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomStatus(RoomStatus.CLOSED)
                        .closedBy("alice")
                        .build())
                .build();

        when(roomService.closeRoom(eq(roomId), any())).thenReturn(response);

        mockMvc.perform(patch("/api/rooms/" + roomId + "/close")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.roomStatus").value("CLOSED"))
                .andExpect(jsonPath("$.room.closedBy").value("alice"));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/close by non-owner returns 403 Forbidden")
    void testCloseRoom_ByNonOwner_ReturnsForbidden() throws Exception {
        final String json = """
                {
                    "username": "charlie"
                }
                """;

        when(roomService.closeRoom(eq(roomId), any()))
                .thenThrow(new RoomOperationNotAllowedException("Only the room owner who created the room can close this chat room."));

        mockMvc.perform(patch("/api/rooms/" + roomId + "/close")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Only the room owner who created the room can close this chat room."));
    }

    @Test
    @DisplayName("PATCH /api/rooms/{id}/leave removes user from room")
    void testLeaveRoom() throws Exception {
        final String json = """
                {
                    "username": "bob"
                }
                """;

        final RoomResponse response = RoomResponse.builder()
                .user(RoomResponse.UserResponse.builder()
                        .username("bob")
                        .isHost(false)
                        .build())
                .room(RoomResponse.RoomDetail.builder()
                        .id(roomId)
                        .roomStatus(RoomStatus.OPEN)
                        .build())
                .build();

        when(roomService.leaveRoom(eq(roomId), any())).thenReturn(response);

        mockMvc.perform(patch("/api/rooms/" + roomId + "/leave")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.room.roomStatus").value("OPEN"));
    }

    @Test
    @DisplayName("POST /api/rooms/{id}/messages sends message and broadcasts over websocket")
    void testSendMessage() throws Exception {
        final UUID senderId = UUID.randomUUID();
        final String json = """
                {
                    "roomId": "%s",
                    "senderId": "%s",
                    "content": "Hello REST!",
                    "type": "CHAT"
                }
                """.formatted(roomId, senderId);

        final MessageResponse response = MessageResponse.builder()
                .id(UUID.randomUUID())
                .userDetail(MessageResponse.UserDetail.builder().id(senderId).username("alice").build())
                .content(MessageResponse.MessageContent.builder().message("Hello REST!").messageType(MessageType.CHAT).build())
                .sentAt(Instant.now())
                .build();

        when(roomService.sendMessage(eq(roomId), any())).thenReturn(response);

        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content.message").value("Hello REST!"));

        verify(messagingTemplate).convertAndSend(eq("/topic/room/" + roomId), eq(response));
    }

    @Test
    @DisplayName("POST /api/rooms/{id}/messages on closed room returns 400 Bad Request")
    void testSendMessage_ClosedRoom_ReturnsBadRequest() throws Exception {
        final UUID senderId = UUID.randomUUID();
        final String json = """
                {
                    "roomId": "%s",
                    "senderId": "%s",
                    "content": "Hello?",
                    "type": "CHAT"
                }
                """.formatted(roomId, senderId);

        when(roomService.sendMessage(eq(roomId), any()))
                .thenThrow(new RoomClosedException("Chat room is closed. No further messages can be sent."));

        mockMvc.perform(post("/api/rooms/" + roomId + "/messages")
                        .header("X-API-Version", "v1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Chat room is closed. No further messages can be sent."));
    }
}
