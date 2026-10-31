import React, { useState, useEffect, useRef, useCallback } from 'react';
import type {
  ChatUser,
  RoomDetail,
  ChatMessage,
  ChatNotification,
  TypingUser,
  RoomEvent,
  RoomResponse,
} from './types';
import { StompClient } from './StompClient';
import {
  CHAT_WS_URL,
  fetchMessagesApi,
  closeRoomApi,
  leaveRoomApi,
  checkBackendHealth,
  ServiceUnavailableError,
} from './chatApi';
import RoomEntryForm from './RoomEntryForm';
import ChatHeader from './ChatHeader';
import ChatMessageList from './ChatMessageList';
import ChatInput from './ChatInput';
import TypingIndicator from './TypingIndicator';
import ChatNotifications from './ChatNotifications';
import ServiceUnavailableView from './ServiceUnavailableView';
import ConfirmModal from './ConfirmModal';
import WelcomePopup from './WelcomePopup';
import OwnerCloseBanner from './OwnerCloseBanner';
import { FiLoader, FiAlertTriangle } from 'react-icons/fi';

export const ChatWindow: React.FC = () => {
  // Pure in-memory session
  const [currentUser, setCurrentUser] = useState<ChatUser | null>(null);
  const [currentRoom, setCurrentRoom] = useState<RoomDetail | null>(null);

  // Clear legacy stored session keys
  useEffect(() => {
    try {
      localStorage.removeItem('open_connect_active_room');
      localStorage.removeItem('open_connect_active_user');
      localStorage.removeItem('open_connect_username');
      localStorage.removeItem('open_connect_email');
    } catch {
      // ignore
    }
  }, []);

  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [notifications, setNotifications] = useState<ChatNotification[]>([]);
  const [activeTypers, setActiveTypers] = useState<TypingUser[]>([]);
  const [loadingHistory, setLoadingHistory] = useState<boolean>(() => Boolean(currentRoom?.id));
  const [closingCountdown, setClosingCountdown] = useState<number | null>(null);
  const [showWelcomePopup, setShowWelcomePopup] = useState<boolean>(false);

  // Custom Leave / Close Confirm Modal State
  const [confirmConfig, setConfirmConfig] = useState<{
    isOpen: boolean;
    type: 'danger' | 'warning';
    title: string;
    message: string;
    confirmText: string;
    icon: 'lock' | 'logout' | 'alert';
    onConfirm: () => void;
  } | null>(null);
  const [confirmLoading, setConfirmLoading] = useState(false);

  // Backend Health & Connectivity State
  const [backendStatus, setBackendStatus] = useState<'checking' | 'online' | 'offline'>('checking');
  const [isRetryingHealth, setIsRetryingHealth] = useState(false);
  const [wsConnected, setWsConnected] = useState(false);

  const stompClientRef = useRef<StompClient | null>(null);
  const lastTypingSentRef = useRef<number>(0);

  // Manual Health check callback for Retry button
  const runHealthCheck = useCallback(async () => {
    setIsRetryingHealth(true);
    const result = await checkBackendHealth();
    setIsRetryingHealth(false);
    if (result.isOnline) {
      setBackendStatus('online');
    } else {
      setBackendStatus('offline');
    }
  }, []);

  // Perform health probe asynchronously on mount to verify backend availability
  useEffect(() => {
    let isMounted = true;
    checkBackendHealth().then((result) => {
      if (!isMounted) return;
      if (result.isOnline) {
        setBackendStatus('online');
      } else {
        setBackendStatus('offline');
      }
    });

    return () => {
      isMounted = false;
    };
  }, []);

  // Restore messages on room enter if backend is online
  useEffect(() => {
    if (backendStatus !== 'online') return;
    if (currentRoom?.id && messages.length === 0) {
      let isMounted = true;
      setLoadingHistory(true);
      fetchMessagesApi(currentRoom.id, 40, null, currentUser?.username)
        .then((history) => {
          if (isMounted) setMessages(history.contents);
        })
        .catch((err) => {
          if (isMounted && err instanceof ServiceUnavailableError) {
            setBackendStatus('offline');
          }
        })
        .finally(() => {
          if (isMounted) setLoadingHistory(false);
        });
      return () => {
        isMounted = false;
      };
    }
  }, [backendStatus, currentRoom?.id, messages.length]);

  // Helper to add temporary notification popup with timestamp
  const addNotification = useCallback((type: ChatNotification['type'], message: string, timeString?: string) => {
    const newNotif: ChatNotification = {
      id: `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`,
      type,
      message,
      timestamp: Date.now(),
      timeString: timeString || new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }),
    };
    setNotifications((prev) => [...prev.slice(-3), newNotif]);

    setTimeout(() => {
      setNotifications((prev) => prev.filter((n) => n.id !== newNotif.id));
    }, 4500);
  }, []);

  const addNotificationRef = useRef(addNotification);
  useEffect(() => {
    addNotificationRef.current = addNotification;
  }, [addNotification]);

  // Cleanup expired typing users every 1.5 seconds
  useEffect(() => {
    const interval = setInterval(() => {
      const now = Date.now();
      setActiveTypers((prev) => prev.filter((t) => now - t.lastTypedAt < 3000));
    }, 1500);

    return () => clearInterval(interval);
  }, []);

  // Redirect to Create / Join 5 seconds after room is closed
  useEffect(() => {
    if (currentRoom?.roomStatus !== 'CLOSED') {
      setClosingCountdown(null);
      return;
    }

    setClosingCountdown(5);

    const interval = setInterval(() => {
      setClosingCountdown((prev) => {
        if (prev === null || prev <= 1) {
          clearInterval(interval);
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    const timer = setTimeout(() => {
      // Cleanly disconnect STOMP client
      if (stompClientRef.current) {
        stompClientRef.current.disconnect();
        stompClientRef.current = null;
      }
      // Reset in-memory state so view redirects to Create / Join form
      setCurrentRoom(null);
      setCurrentUser(null);
      setMessages([]);
      setActiveTypers([]);
      setNotifications([]);
      setClosingCountdown(null);
      setShowWelcomePopup(false);
    }, 5000);

    return () => {
      clearInterval(interval);
      clearTimeout(timer);
    };
  }, [currentRoom?.roomStatus]);

  // STOMP WebSocket Connection & Topic Subscriptions
  const roomId = currentRoom?.id;
  const currentUserId = currentUser?.id;

  useEffect(() => {
    if (backendStatus !== 'online') return;
    if (!roomId) {
      if (stompClientRef.current) {
        stompClientRef.current.disconnect();
        stompClientRef.current = null;
      }
      return;
    }

    const client = new StompClient(CHAT_WS_URL);
    stompClientRef.current = client;

    client.onConnect(() => {
      setWsConnected(true);

      // 1. Subscribe to Chat & Typing Messages
      client.subscribe(`/topic/room/${roomId}`, (body) => {
        try {
          const raw = JSON.parse(body);

          // Ignore presence events if they arrive on the message topic
          if (
            raw.eventType ||
            raw.type === 'JOINED' ||
            raw.type === 'LEFT' ||
            raw.type === 'CLOSED' ||
            raw.type === 'SYSTEM'
          ) {
            return;
          }

          // Handle Typing Indicator broadcast
          if (raw.content?.messageType === 'TYPING' || raw.type === 'TYPING') {
            const typerId = raw.userDetail?.id || raw.senderId;
            const typerName = raw.userDetail?.username || raw.senderName || 'Someone';

            if (typerId && typerId !== currentUserId) {
              setActiveTypers((prev) => {
                const existing = prev.find((t) => t.userId === typerId);
                if (existing) {
                  return prev.map((t) =>
                    t.userId === typerId ? { ...t, lastTypedAt: Date.now() } : t
                  );
                }
                return [...prev, { userId: typerId, username: typerName, lastTypedAt: Date.now() }];
              });
            }
            return;
          }

          // Extract content safely
          const contentText =
            typeof raw.content === 'object' && raw.content?.message !== undefined
              ? raw.content.message
              : typeof raw.content === 'string'
              ? raw.content
              : '';

          // Only proceed if there is actual message content
          if (!contentText || !contentText.trim()) {
            return;
          }

          // Handle Regular Chat Message broadcast
          const newMsg: ChatMessage = {
            id: raw.id || `${Date.now()}-${Math.random()}`,
            roomId: raw.roomDetail?.id || raw.roomId,
            senderId: raw.userDetail?.id || raw.senderId || 'unknown',
            senderName: raw.userDetail?.username || raw.senderName || 'Anonymous',
            content: contentText,
            type: raw.content?.messageType || raw.type || 'CHAT',
            sentAt: raw.sentAt || new Date().toISOString(),
          };

          // Remove sender from typing indicator upon message receipt
          setActiveTypers((prev) => prev.filter((t) => t.userId !== newMsg.senderId));

          // Append message with deduplication by ID
          setMessages((prev) => {
            if (newMsg.id && prev.some((m) => m.id === newMsg.id)) {
              return prev;
            }
            return [...prev, newMsg];
          });
        } catch {
          // silent error handling
        }
      });

      // 2. Subscribe to Presence Events (JOINED, LEFT, CLOSED)
      client.subscribe(`/topic/room/${roomId}/presence`, (body) => {
        try {
          const event: RoomEvent = JSON.parse(body);
          const timeStr = event.timestamp
            ? new Date(event.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            : new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

          if (event.eventType === 'JOINED') {
            addNotificationRef.current('JOINED', `${event.username} joined the chat`, timeStr);
            setCurrentRoom((prev) => {
              if (!prev) return prev;
              const exists = prev.roomMembers.some((m) => m.userId === event.userId);
              if (exists) return prev;
              return {
                ...prev,
                roomMembers: [
                  ...prev.roomMembers,
                  {
                    roomId: event.roomId,
                    roomName: prev.roomName,
                    userId: event.userId,
                    userName: event.username,
                    joinedAt: event.timestamp,
                  },
                ],
              };
            });
          } else if (event.eventType === 'LEFT') {
            addNotificationRef.current('LEFT', `${event.username} left the chat`, timeStr);
            setCurrentRoom((prev) => {
              if (!prev) return prev;
              return {
                ...prev,
                roomMembers: prev.roomMembers.filter((m) => m.userId !== event.userId),
              };
            });
          } else if (event.eventType === 'CLOSED') {
            setCurrentRoom((prev) => {
              if (!prev) return prev;
              return {
                ...prev,
                roomStatus: 'CLOSED',
              };
            });
          }
        } catch {
          // silent error handling
        }
      });
    });

    client.onDisconnect(() => {
      setWsConnected(false);
    });

    client.onError(() => {
      setWsConnected(false);
      checkBackendHealth().then((result) => {
        if (!result.isOnline) {
          setBackendStatus('offline');
        }
      });
    });

    client.connect();

    return () => {
      client.disconnect();
      if (stompClientRef.current === client) {
        stompClientRef.current = null;
      }
    };
  }, [backendStatus, roomId, currentUserId]);

  // Handle successful room entry (create or join)
  const handleRoomEntrySuccess = (data: RoomResponse) => {
    setBackendStatus('online');
    setCurrentUser(data.user);
    setCurrentRoom(data.room);
    setMessages([]);
    setActiveTypers([]);
    setShowWelcomePopup(true);

    const nowTime = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    addNotification('INFO', `Welcome to "${data.room.roomName}"`, nowTime);

    // Fetch message history for the entered room
    setLoadingHistory(true);
    fetchMessagesApi(data.room.id, 40, null, data.user?.username)
      .then((history) => {
        setMessages(history.contents);
      })
      .catch((err) => {
        if (err instanceof ServiceUnavailableError) {
          addNotification('INFO', 'Could not load past message history: backend unreachable.');
        }
      })
      .finally(() => {
        setLoadingHistory(false);
      });
  };

  // Handler for entry form service failure
  const handleEntryError = (err: unknown) => {
    if (err instanceof ServiceUnavailableError) {
      setBackendStatus('offline');
    }
  };

  // Send message
  const handleSendMessage = (text: string) => {
    if (!currentRoom || !currentUser || !stompClientRef.current) return;
    if (currentRoom.roomStatus === 'CLOSED') return;

    stompClientRef.current.send(`/app/sendMessage/${currentRoom.id}`, {
      roomId: currentRoom.id,
      senderId: currentUser.id,
      content: text,
      type: 'CHAT',
    });
  };

  // Emit typing indicator (throttled to 1.5s)
  const handleTyping = () => {
    if (!currentRoom || !currentUser || !stompClientRef.current) return;
    if (currentRoom.roomStatus === 'CLOSED') return;

    const now = Date.now();
    if (now - lastTypingSentRef.current > 1500) {
      lastTypingSentRef.current = now;
      stompClientRef.current.send(`/app/sendMessage/${currentRoom.id}`, {
        roomId: currentRoom.id,
        senderId: currentUser.id,
        content: '...',
        type: 'TYPING',
      });
    }
  };

  // Close room confirmation (room owner only)
  const handleCloseRoom = () => {
    if (!currentRoom || !currentUser) return;
    setConfirmConfig({
      isOpen: true,
      type: 'danger',
      title: 'Close Room for All?',
      message:
        'Are you sure you want to close this room? This will permanently end the chat session for all participants.',
      confirmText: 'Close Room',
      icon: 'lock',
      onConfirm: async () => {
        setConfirmLoading(true);
        try {
          await closeRoomApi(currentRoom.id, currentUser.username);
          setCurrentRoom((prev) => (prev ? { ...prev, roomStatus: 'CLOSED' } : prev));
        } catch (err) {
          addNotification('CLOSED', err instanceof Error ? err.message : 'Failed to close room');
        } finally {
          setConfirmLoading(false);
          setConfirmConfig(null);
        }
      },
    });
  };

  // Leave room confirmation
  const handleLeaveRoom = () => {
    if (!currentRoom || !currentUser) return;
    setConfirmConfig({
      isOpen: true,
      type: 'warning',
      title: 'Leave This Room?',
      message:
        'You will exit this conversation. You can rejoin at any time using the 6-digit room code.',
      confirmText: 'Leave Room',
      icon: 'logout',
      onConfirm: async () => {
        setConfirmLoading(true);
        try {
          await leaveRoomApi(currentRoom.id, currentUser.username);
        } catch {
          // ignore
        } finally {
          setConfirmLoading(false);
          setConfirmConfig(null);
          if (stompClientRef.current) {
            stompClientRef.current.disconnect();
            stompClientRef.current = null;
          }
          setCurrentRoom(null);
          setCurrentUser(null);
          setMessages([]);
          setActiveTypers([]);
          setNotifications([]);
          setShowWelcomePopup(false);
        }
      },
    });
  };

  const isOwner = Boolean(
    currentRoom &&
      currentUser &&
      (currentRoom.owner || '').toLowerCase().trim() === (currentUser.username || '').toLowerCase().trim()
  );

  return (
    <div className="w-full h-full flex flex-col relative overflow-hidden rounded-2xl sm:rounded-3xl bg-[#0c0d14]/85 backdrop-blur-2xl border border-white/[0.1] shadow-2xl font-sans">
      {/* Notifications Overlay */}
      <ChatNotifications notifications={notifications} />

      {/* Welcome Greeting Pop-up on Room Entry */}
      {currentRoom && currentUser && (
        <WelcomePopup
          room={currentRoom}
          currentUser={currentUser}
          isOpen={showWelcomePopup}
          onClose={() => setShowWelcomePopup(false)}
        />
      )}

      {/* Custom Leave / Close Confirmation Modal */}
      {confirmConfig && (
        <ConfirmModal
          isOpen={confirmConfig.isOpen}
          type={confirmConfig.type}
          title={confirmConfig.title}
          message={confirmConfig.message}
          confirmText={confirmConfig.confirmText}
          icon={confirmConfig.icon}
          loading={confirmLoading}
          onConfirm={confirmConfig.onConfirm}
          onCancel={() => setConfirmConfig(null)}
        />
      )}

      {/* Conditional Views */}
      {backendStatus === 'offline' ? (
        /* 1. Service Unavailable View */
        <ServiceUnavailableView
          onRetry={runHealthCheck}
          isRetrying={isRetryingHealth}
        />
      ) : backendStatus === 'checking' ? (
        /* 2. Checking Status Loader */
        <div className="flex flex-col items-center justify-center h-full text-center p-6 relative">
          <FiLoader className="w-9 h-9 text-indigo-500 animate-spin mb-3.5" />
          <p className="text-base font-semibold text-white">Connecting to Open Connect...</p>
          <p className="text-xs text-zinc-400 mt-1.5">Checking backend engine availability</p>
        </div>
      ) : !currentRoom || !currentUser ? (
        /* 3. Room Entry Form (Create / Join) */
        <RoomEntryForm
          onSuccess={handleRoomEntrySuccess}
          onError={handleEntryError}
        />
      ) : (
        /* 4. Active Chat Room */
        <div className="flex flex-col h-full relative">
          {/* STOMP Disconnected Warning Bar */}
          {!wsConnected && closingCountdown === null && (
            <div className="px-4 py-2 bg-amber-500/15 border-b border-amber-500/30 text-amber-200 text-xs flex items-center justify-center gap-2 flex-shrink-0">
              <FiAlertTriangle className="w-4 h-4 text-amber-400 animate-pulse" />
              <span>Connecting to live chat stream... Syncing messages.</span>
            </div>
          )}

          {/* Chat Header */}
          <ChatHeader
            room={currentRoom}
            currentUser={currentUser}
            onCloseRoom={handleCloseRoom}
            onLeaveRoom={handleLeaveRoom}
          />

          {/* Owner Message & 5-Second Room Closure Countdown Banner */}
          <OwnerCloseBanner
            isOwner={isOwner}
            isClosed={currentRoom.roomStatus === 'CLOSED'}
            closingCountdown={closingCountdown}
            onCloseRoom={handleCloseRoom}
          />

          {/* Message List with Scrolling Animation */}
          <ChatMessageList
            messages={messages}
            currentUser={currentUser}
            loadingHistory={loadingHistory}
            isRoomClosed={currentRoom.roomStatus === 'CLOSED'}
          />

          {/* Real-time Typing Indicator Footer */}
          <div className="px-4 py-1 pointer-events-none min-h-[30px] flex items-center">
            <TypingIndicator typers={activeTypers} />
          </div>

          {/* Chat Input */}
          <ChatInput
            onSendMessage={handleSendMessage}
            onTyping={handleTyping}
            isRoomClosed={currentRoom.roomStatus === 'CLOSED'}
            closingCountdown={closingCountdown}
            disabled={!wsConnected || closingCountdown !== null}
          />
        </div>
      )}
    </div>
  );
};

export default ChatWindow;
