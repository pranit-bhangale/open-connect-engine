package com.pranit.connect.specification;

import com.pranit.connect.entity.Message;
import com.pranit.connect.entity.MessageType;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@UtilityClass
public class EntitySpecification {

    public static Specification<Message> searchMessageKeyword(final String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) return cb.conjunction();
            final String likePattern = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
            return cb.like(cb.lower(root.get("content")), likePattern);
        };
    }

    public static Specification<Message> hasRoomId(final UUID roomId) {
        return (root, query, cb) -> {
            if (roomId == null) return cb.conjunction();
            return cb.equal(root.get("room").get("id"), roomId);
        };
    }

    public static Specification<Message> hasSenderId(final UUID senderId) {
        return (root, query, cb) -> {
            if (senderId == null) return cb.conjunction();
            return cb.equal(root.get("sender").get("id"), senderId);
        };
    }

    public static Specification<Message> hasMessageType(final MessageType type) {
        return (root, query, cb) -> {
            if (type == null) return cb.conjunction();
            return cb.equal(root.get("type"), type);
        };
    }

    public static Specification<Message> createdAfter(final Instant startTime) {
        return (root, query, cb) -> {
            if (startTime == null) return cb.conjunction();
            return cb.greaterThanOrEqualTo(root.get("createdAt"), startTime);
        };
    }

    public static Specification<Message> createdBefore(final Instant endTime) {
        return (root, query, cb) -> {
            if (endTime == null) return cb.conjunction();
            return cb.lessThanOrEqualTo(root.get("createdAt"), endTime);
        };
    }
}
