package com.pranit.connect.repository;

import com.pranit.connect.entity.ChatRoom;
import com.pranit.connect.entity.RoomMember;
import com.pranit.connect.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface RoomMemberRepository extends JpaRepository<RoomMember, RoomMember.RoomMemberId> {
    boolean existsByRoomAndUser(ChatRoom room, User user);

    List<RoomMember> findByRoom_Id(UUID id);

    void deleteByRoomAndUser(ChatRoom room, User leaver);

    void deleteByRoom_Id(UUID roomId);
}
