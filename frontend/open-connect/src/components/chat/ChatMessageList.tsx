import React, { useState, useEffect, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { UserAvatar } from './UserAvatar';
import type { ChatMessage, ChatUser } from './types';
import { FiMessageSquare, FiInfo, FiX, FiArrowDown } from 'react-icons/fi';

interface ChatMessageListProps {
  messages: ChatMessage[];
  currentUser: ChatUser;
  loadingHistory?: boolean;
  isRoomClosed?: boolean;
}

function formatMessageTime(isoString: string): string {
  if (!isoString) return '';
  try {
    const date = new Date(isoString);
    if (isNaN(date.getTime())) return '';
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

export const ChatMessageList: React.FC<ChatMessageListProps> = ({
  messages,
  currentUser,
  loadingHistory = false,
  isRoomClosed = false,
}) => {
  const [showNotice, setShowNotice] = useState(true);
  const [showScrollBottom, setShowScrollBottom] = useState(false);
  const bottomRef = useRef<HTMLDivElement>(null);
  const listContainerRef = useRef<HTMLDivElement>(null);

  // Auto-close room notice after 12 seconds
  useEffect(() => {
    const timer = setTimeout(() => {
      setShowNotice(false);
    }, 12000);

    return () => clearTimeout(timer);
  }, []);

  // Smooth scroll to bottom on new messages
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  // Monitor scroll position to show quick "scroll to bottom" button
  const handleScroll = () => {
    if (!listContainerRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = listContainerRef.current;
    const distanceFromBottom = scrollHeight - scrollTop - clientHeight;
    setShowScrollBottom(distanceFromBottom > 150);
  };

  const scrollToBottom = () => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  return (
    <div
      ref={listContainerRef}
      onScroll={handleScroll}
      className="flex-1 overflow-y-auto px-3.5 sm:px-4 py-3 space-y-3.5 scrollbar-thin scrollbar-thumb-white/10 scrollbar-track-transparent relative"
    >
      {/* Pinned Room Headline Notice */}
      <AnimatePresence>
        {showNotice && !isRoomClosed && (
          <motion.div
            initial={{ opacity: 0, y: -6, height: 'auto' }}
            animate={{ opacity: 1, y: 0, height: 'auto' }}
            exit={{ opacity: 0, y: -8, height: 0, marginBottom: 0, transition: { duration: 0.3 } }}
            className="overflow-hidden mb-3"
          >
            <div className="p-3 rounded-2xl bg-gradient-to-r from-indigo-500/15 via-white/[0.03] to-transparent border border-indigo-500/25 text-center select-none shadow-sm relative group">
              <button
                type="button"
                onClick={() => setShowNotice(false)}
                className="absolute top-2.5 right-2.5 p-1 rounded-md text-zinc-400 hover:text-white hover:bg-white/10 transition-colors opacity-70 group-hover:opacity-100 cursor-pointer"
                title="Dismiss notice"
              >
                <FiX className="w-3.5 h-3.5" />
              </button>
              <div className="flex items-center justify-center gap-1.5 text-[11px] font-bold text-indigo-400 uppercase tracking-wider mb-1">
                <FiInfo className="w-3.5 h-3.5" />
                <span>Room Notice</span>
              </div>
              <p className="text-xs text-zinc-200 font-medium">
                Once the conversation is finished, the room owner can close the room.
              </p>
            </div>
          </motion.div>
        )}
      </AnimatePresence>

      {loadingHistory && (
        <div className="flex justify-center py-2 text-xs text-zinc-400">
          <span>Loading chat history...</span>
        </div>
      )}

      {messages.length === 0 && !loadingHistory ? (
        <div className="flex flex-col items-center justify-center py-12 opacity-75 select-none">
          <div className="p-3.5 rounded-full bg-white/[0.04] border border-white/[0.08] text-indigo-400 mb-3">
            <FiMessageSquare className="w-6 h-6" />
          </div>
          <p className="text-sm font-semibold text-white">No messages yet</p>
          <p className="text-xs text-zinc-400 mt-1 max-w-[220px] text-center">
            Say hello to everyone in this room to start the conversation!
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {messages.map((msg, index) => {
            if (!msg.content || !msg.content.trim()) return null;
            const isMe = msg.senderId === currentUser.id;
            const prevMsg = messages[index - 1];
            const isSameSenderAsPrev = prevMsg && prevMsg.senderId === msg.senderId;

            return (
              <motion.div
                key={msg.id || `${msg.senderId}-${msg.sentAt}-${index}`}
                initial={{ opacity: 0, y: 10, scale: 0.96 }}
                animate={{ opacity: 1, y: 0, scale: 1 }}
                transition={{ type: 'spring', damping: 25, stiffness: 350 }}
                className={`flex gap-2 items-end ${isMe ? 'justify-end' : 'justify-start'}`}
              >
                {/* Other User Avatar (initial letter) */}
                {!isMe && (
                  <div className="w-7 flex-shrink-0 mb-1">
                    {!isSameSenderAsPrev ? (
                      <UserAvatar name={msg.senderName} size="sm" />
                    ) : (
                      <div className="w-7" />
                    )}
                  </div>
                )}

                {/* Message Bubble Container */}
                <div
                  className={`flex flex-col max-w-[78%] ${
                    isMe ? 'items-end' : 'items-start'
                  }`}
                >
                  {/* Sender Name */}
                  {!isMe && !isSameSenderAsPrev && (
                    <span className="text-[11px] font-semibold text-indigo-400 mb-1 ml-1 tracking-wide">
                      {msg.senderName}
                    </span>
                  )}

                  {/* Message Bubble */}
                  <div
                    className={`px-3.5 py-2 rounded-2xl text-[13px] leading-relaxed break-words shadow-sm font-sans ${
                      isMe
                        ? 'bg-gradient-to-r from-indigo-600 to-indigo-700 text-white rounded-br-sm'
                        : 'bg-[#181824] border border-white/10 text-zinc-100 rounded-bl-sm'
                    }`}
                  >
                    <p className="whitespace-pre-wrap">{msg.content}</p>

                    {/* Chat Timestamp */}
                    <div
                      className={`text-[10px] mt-0.5 text-right select-none font-mono ${
                        isMe ? 'text-white/70' : 'text-zinc-400'
                      }`}
                    >
                      {formatMessageTime(msg.sentAt)}
                    </div>
                  </div>
                </div>

                {/* Current User Avatar (initial letter) */}
                {isMe && (
                  <div className="w-7 flex-shrink-0 mb-1">
                    {!isSameSenderAsPrev ? (
                      <UserAvatar name={currentUser.username} size="sm" />
                    ) : (
                      <div className="w-7" />
                    )}
                  </div>
                )}
              </motion.div>
            );
          })}
        </div>
      )}

      {/* Scroll to Bottom Quick Floating Pill */}
      <AnimatePresence>
        {showScrollBottom && (
          <motion.button
            initial={{ opacity: 0, scale: 0.8, y: 10 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.8, y: 10 }}
            onClick={scrollToBottom}
            className="fixed bottom-24 right-8 z-30 p-2 rounded-full bg-indigo-600 text-white shadow-xl hover:bg-indigo-500 transition-all cursor-pointer border border-white/20"
            title="Scroll to bottom"
          >
            <FiArrowDown className="w-4 h-4" />
          </motion.button>
        )}
      </AnimatePresence>

      <div ref={bottomRef} />
    </div>
  );
};

export default ChatMessageList;
