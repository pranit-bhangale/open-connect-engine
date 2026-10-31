import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  FiCopy,
  FiCheck,
  FiX,
  FiUsers,
  FiLock,
  FiLogOut,
  FiChevronDown,
} from 'react-icons/fi';
import type { RoomDetail, ChatUser } from './types';
import { UserAvatar } from './UserAvatar';

interface ChatHeaderProps {
  room: RoomDetail;
  currentUser: ChatUser;
  onCloseRoom: () => void;
  onLeaveRoom: () => void;
}

export const ChatHeader: React.FC<ChatHeaderProps> = ({
  room,
  currentUser,
  onCloseRoom,
  onLeaveRoom,
}) => {
  const [copied, setCopied] = useState(false);
  const [showMemberList, setShowMemberList] = useState(false);

  const isOwner = (room.owner || '').toLowerCase().trim() === (currentUser?.username || '').toLowerCase().trim();
  const isClosed = room.roomStatus === 'CLOSED';

  const handleCopyCode = () => {
    navigator.clipboard.writeText(room.roomCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="relative z-30 px-4 sm:px-6 py-3.5 bg-[#121218]/90 backdrop-blur-md border-b border-white/[0.08] flex items-center justify-between gap-3 select-none flex-shrink-0">
      {/* Left: Room Info */}
      <div className="flex items-center gap-3 min-w-0">
        <UserAvatar name={room.roomName} size="md" />
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h3 className="text-base font-semibold text-white truncate max-w-[160px] sm:max-w-[280px] md:max-w-[400px]">
              {room.roomName}
            </h3>
            {isClosed ? (
              <span className="px-2 py-0.5 rounded text-[10px] font-bold bg-rose-500/20 text-rose-300 border border-rose-500/30">
                CLOSED
              </span>
            ) : (
              <button
                type="button"
                onClick={handleCopyCode}
                title="Click to copy Room Code"
                className="flex items-center gap-1.5 px-2 py-0.5 rounded text-xs font-mono font-medium bg-white/[0.06] hover:bg-white/[0.1] text-indigo-300 border border-white/[0.08] transition-all cursor-pointer"
              >
                <span>{room.roomCode}</span>
                {copied ? <FiCheck className="text-emerald-400 w-3.5 h-3.5" /> : <FiCopy className="w-3.5 h-3.5 text-zinc-400" />}
              </button>
            )}
          </div>
          <div className="flex items-center gap-2.5 mt-0.5">
            <button
              type="button"
              onClick={() => setShowMemberList(!showMemberList)}
              className="flex items-center gap-1 text-xs text-zinc-400 hover:text-white transition-colors cursor-pointer"
            >
              <FiUsers className="w-3.5 h-3.5 text-indigo-400" />
              <span>{room.roomMembers?.length || 1} members</span>
              <FiChevronDown className={`w-3.5 h-3.5 transition-transform duration-200 ${showMemberList ? 'rotate-180' : ''}`} />
            </button>
            <span className="text-[10px] text-zinc-600">•</span>
            {isOwner ? (
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-amber-500/15 text-amber-300 border border-amber-500/30">
                Owner (You)
              </span>
            ) : (
              <span className="text-xs text-zinc-400 truncate max-w-[150px]" title={`Room Owner: ${room.owner}`}>
                Owner: <span className="text-zinc-300 font-medium">{room.owner}</span>
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Right: Actions */}
      <div className="flex items-center gap-2 flex-shrink-0">
        {/* Close Room (Room Owner Only) */}
        {isOwner && !isClosed && (
          <button
            type="button"
            onClick={onCloseRoom}
            title="Close room for all participants"
            className="px-3 py-1.5 rounded-xl text-rose-300 bg-rose-500/10 hover:bg-rose-500/20 border border-rose-500/25 transition-all text-xs flex items-center gap-1.5 cursor-pointer font-medium shadow-sm"
          >
            <FiLock className="w-3.5 h-3.5 text-rose-400" />
            <span className="hidden sm:inline">Close Room</span>
          </button>
        )}

        {/* Leave Room */}
        {!isClosed && (
          <button
            type="button"
            onClick={onLeaveRoom}
            title="Leave Room"
            className="px-3 py-1.5 rounded-xl text-zinc-300 bg-white/[0.04] hover:text-amber-300 hover:bg-amber-500/15 border border-white/10 hover:border-amber-500/20 transition-all text-xs flex items-center gap-1.5 cursor-pointer font-medium"
          >
            <FiLogOut className="w-3.5 h-3.5" />
            <span className="hidden sm:inline">Leave Room</span>
          </button>
        )}
      </div>

      {/* Dropdown Member List */}
      <AnimatePresence>
        {showMemberList && (
          <>
            {/* Click-catcher backdrop */}
            <div
              className="fixed inset-0 z-40"
              onClick={() => setShowMemberList(false)}
            />

            <motion.div
              initial={{ opacity: 0, y: -8, scale: 0.97 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.97 }}
              transition={{ duration: 0.16, ease: 'easeOut' }}
              className="absolute top-full left-4 sm:left-6 right-4 sm:right-auto sm:w-80 mt-2 p-3.5 rounded-2xl bg-[#14141c] border border-white/15 shadow-2xl z-50 max-h-64 overflow-y-auto space-y-2 ring-1 ring-white/10"
            >
              <div className="flex items-center justify-between border-b border-white/10 pb-2 mb-2">
                <span className="text-[11px] font-semibold text-zinc-200 uppercase tracking-wider flex items-center gap-1.5">
                  <FiUsers className="w-3.5 h-3.5 text-indigo-400" />
                  <span>Room Members ({room.roomMembers?.length || 1})</span>
                </span>
                <button
                  type="button"
                  onClick={() => setShowMemberList(false)}
                  className="p-1 rounded-md text-zinc-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
                >
                  <FiX className="w-3.5 h-3.5" />
                </button>
              </div>

              {room.roomMembers && room.roomMembers.length > 0 ? (
                room.roomMembers.map((m) => {
                  const isMemberOwner = m.userName.toLowerCase() === room.owner.toLowerCase();
                  const isCurrent = m.userId === currentUser.id;

                  return (
                    <div
                      key={m.userId}
                      className="flex items-center justify-between gap-2 p-2 rounded-xl bg-white/[0.03] hover:bg-white/[0.06] border border-white/5 transition-colors"
                    >
                      <div className="flex items-center gap-2.5 min-w-0">
                        <UserAvatar name={m.userName} size="xs" />
                        <span className="text-xs text-white font-medium truncate">
                          {m.userName}
                        </span>
                      </div>
                      <div className="flex items-center gap-1 flex-shrink-0">
                        {isMemberOwner && (
                          <span className="px-2 py-0.5 rounded text-[10px] bg-amber-500/20 text-amber-300 border border-amber-500/30 font-medium">
                            Owner
                          </span>
                        )}
                        {isCurrent && (
                          <span className="px-2 py-0.5 rounded text-[10px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-medium">
                            You
                          </span>
                        )}
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="text-xs text-zinc-400 text-center py-2">No members found.</div>
              )}
            </motion.div>
          </>
        )}
      </AnimatePresence>
    </div>
  );
};

export default ChatHeader;
