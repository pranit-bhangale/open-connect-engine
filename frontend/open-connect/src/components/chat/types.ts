export interface ChatUser {
  id: string;
  username: string;
  isHost?: boolean;
}

export interface RoomMember {
  roomId: string;
  roomName: string;
  userId: string;
  userName: string;
  isHost?: boolean;
  joinedAt: string;
}

export interface RoomDetail {
  id: string;
  roomCode: string;
  roomName: string;
  description: string;
  owner: string;
  closedBy?: string | null;
  roomStatus: 'OPEN' | 'CLOSED';
  isHostOnline?: boolean;
  roomMembers: RoomMember[];
  createdAt: string;
  closedAt?: string | null;
}

export interface RoomResponse {
  user: ChatUser;
  room: RoomDetail;
}

export interface RawMessageResponse {
  id: string;
  roomDetail: {
    id: string;
    roomCode: string;
    roomName: string;
    description: string;
    owner: string | null;
    roomStatus: 'OPEN' | 'CLOSED';
  };
  userDetail: {
    id: string;
    username: string;
  };
  content: {
    message: string;
    messageType: 'CHAT' | 'TYPING';
  };
  sentAt: string;
}

export interface ChatMessage {
  id: string;
  roomId: string;
  senderId: string;
  senderName: string;
  content: string;
  type: 'CHAT' | 'TYPING';
  sentAt: string;
}

export interface RoomEvent {
  roomId: string;
  roomCode?: string;
  userId: string;
  username: string;
  eventType: 'JOINED' | 'LEFT' | 'CLOSED' | 'CHAT' | 'TYPING' | 'SYSTEM';
  message?: string;
  timestamp: string;
  roomStatus: 'OPEN' | 'CLOSED';
  isHost?: boolean;
  isHostOnline?: boolean;
  memberCount?: number;
}

export interface TypingUser {
  userId: string;
  username: string;
  lastTypedAt: number;
}

export interface ChatNotification {
  id: string;
  type: 'JOINED' | 'LEFT' | 'CLOSED' | 'INFO';
  username?: string;
  message: string;
  timestamp: number;
  timeString?: string;
}

export interface ScrollResponse<T> {
  contents: T[];
  nextScrollId?: string | null;
  prevScrollId?: string | null;
  hasNext: boolean;
  hasPrevious: boolean;
  pageSize: number;
}
