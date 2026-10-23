-- V1__init.sql: Initial schema for Open Connect Engine

-- Table: users
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(254) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- Table: chat_rooms
CREATE TABLE IF NOT EXISTS chat_rooms (
    id UUID PRIMARY KEY,
    room_code VARCHAR(36) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    description VARCHAR(1000) NOT NULL,
    owner_id UUID NOT NULL,
    closed_at TIMESTAMP WITH TIME ZONE,
    closed_by UUID,
    status VARCHAR(10) NOT NULL DEFAULT 'OPEN',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_chat_rooms_code UNIQUE (room_code),
    CONSTRAINT fk_chat_rooms_owner FOREIGN KEY (owner_id) REFERENCES users (id),
    CONSTRAINT fk_chat_rooms_closed_by FOREIGN KEY (closed_by) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_chat_rooms_status_created ON chat_rooms (status, created_at);

-- Table: room_members
CREATE TABLE IF NOT EXISTS room_members (
    room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_room_members PRIMARY KEY (room_id, user_id),
    CONSTRAINT uk_room_member UNIQUE (room_id, user_id),
    CONSTRAINT fk_room_members_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_room_members_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_room_members_user ON room_members (user_id);

-- Table: messages
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY,
    room_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(20) NOT NULL DEFAULT 'CHAT',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_messages_room FOREIGN KEY (room_id) REFERENCES chat_rooms (id) ON DELETE CASCADE,
    CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id)
);

CREATE INDEX IF NOT EXISTS idx_messages_room_created ON messages (room_id, created_at);
CREATE INDEX IF NOT EXISTS idx_messages_sender ON messages (sender_id);
