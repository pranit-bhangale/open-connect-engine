import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FiCheck, FiCopy, FiX, FiSmile, FiShield, FiUsers } from 'react-icons/fi';
import type { RoomDetail, ChatUser } from './types';

interface WelcomePopupProps {
  room: RoomDetail;
  currentUser: ChatUser;
  isOpen: boolean;
  onClose: () => void;
}

export const WelcomePopup: React.FC<WelcomePopupProps> = ({
  room,
  currentUser,
  isOpen,
  onClose,
}) => {
  const [copied, setCopied] = useState(false);
  const isOwner = (room.owner || '').toLowerCase().trim() === (currentUser?.username || '').toLowerCase().trim();

  // Auto-dismiss after 6 seconds
  useEffect(() => {
    if (!isOpen) return;
    const timer = setTimeout(() => {
      onClose();
    }, 6000);
    return () => clearTimeout(timer);
  }, [isOpen, onClose]);

  const handleCopyCode = () => {
    navigator.clipboard.writeText(room.roomCode);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <AnimatePresence>
      {isOpen && (
        <div className="absolute inset-0 z-50 flex items-center justify-center p-4 pointer-events-none">
          {/* Backdrop with pointer-events-auto */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.2 }}
            onClick={onClose}
            className="absolute inset-0 bg-black/60 backdrop-blur-sm pointer-events-auto"
          />

          {/* Modal Pop-up Card */}
          <motion.div
            initial={{ opacity: 0, scale: 0.88, y: 16 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.88, y: 16 }}
            transition={{ type: 'spring', damping: 24, stiffness: 320 }}
            onClick={(e) => e.stopPropagation()}
            className="relative z-10 w-full max-w-sm p-6 rounded-3xl bg-[#13141f] border border-indigo-500/30 shadow-2xl text-center select-none pointer-events-auto ring-1 ring-white/10"
          >
            {/* Close Button */}
            <button
              type="button"
              onClick={onClose}
              className="absolute top-4 right-4 p-1.5 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            >
              <FiX className="w-4 h-4" />
            </button>

            {/* Greet Celebration Icon */}
            <div className="w-14 h-14 mx-auto mb-3.5 rounded-2xl bg-gradient-to-tr from-indigo-500/25 to-purple-500/25 border border-indigo-500/30 flex items-center justify-center text-indigo-400 shadow-inner">
              <FiSmile className="w-7 h-7 text-indigo-300 animate-bounce" />
            </div>

            {/* Welcome Greeting */}
            <h3 className="text-xl font-bold text-white tracking-tight">
              Welcome to <span className="text-indigo-400">{room.roomName}</span>!
            </h3>

            <p className="text-xs text-zinc-300 mt-1.5 leading-relaxed">
              You’re in as <span className="text-white font-semibold">{currentUser.username}</span>
              {isOwner ? ' (Host & Owner)' : ''}.
            </p>

            {room.description && (
              <p className="text-xs text-zinc-400 mt-1 italic line-clamp-2">
                "{room.description}"
              </p>
            )}

            {/* Room Code Card */}
            <div className="mt-4 p-3 rounded-2xl bg-white/[0.04] border border-white/10 flex items-center justify-between gap-2">
              <div className="text-left">
                <span className="text-[10px] text-zinc-400 uppercase tracking-wider block font-semibold">
                  Share Room Code
                </span>
                <span className="text-base font-mono font-bold tracking-widest text-indigo-300">
                  {room.roomCode}
                </span>
              </div>

              <button
                type="button"
                onClick={handleCopyCode}
                className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-indigo-600/30 hover:bg-indigo-600/50 text-indigo-200 border border-indigo-500/40 transition-all cursor-pointer"
              >
                {copied ? (
                  <>
                    <FiCheck className="w-3.5 h-3.5 text-emerald-400" />
                    <span>Copied!</span>
                  </>
                ) : (
                  <>
                    <FiCopy className="w-3.5 h-3.5" />
                    <span>Copy</span>
                  </>
                )}
              </button>
            </div>

            {/* Status pills */}
            <div className="flex items-center justify-center gap-3 mt-4 text-[11px] text-zinc-400">
              <span className="flex items-center gap-1">
                <FiShield className="text-indigo-400 w-3.5 h-3.5" />
                <span>Owner: {room.owner}</span>
              </span>
              <span>•</span>
              <span className="flex items-center gap-1">
                <FiUsers className="text-emerald-400 w-3.5 h-3.5" />
                <span>{room.roomMembers?.length || 1} online</span>
              </span>
            </div>

            {/* Enter Chat Button */}
            <button
              type="button"
              onClick={onClose}
              className="w-full mt-5 py-2.5 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white font-semibold text-xs shadow-md transition-all cursor-pointer"
            >
              Start Chatting
            </button>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};

export default WelcomePopup;
