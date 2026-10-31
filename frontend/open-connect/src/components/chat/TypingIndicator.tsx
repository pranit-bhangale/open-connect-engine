import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { UserAvatar } from './UserAvatar';
import type { TypingUser } from './types';

interface TypingIndicatorProps {
  typers: TypingUser[];
}

export const TypingIndicator: React.FC<TypingIndicatorProps> = ({ typers }) => {
  if (!typers || typers.length === 0) return null;

  const formatTypingText = (): string => {
    if (typers.length === 1) {
      return `${typers[0].username} is typing...`;
    }
    if (typers.length === 2) {
      return `${typers[0].username} and ${typers[1].username} are typing...`;
    }
    return `${typers[0].username}, ${typers[1].username} and ${typers.length - 2} others are typing...`;
  };

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0, y: 6 }}
        animate={{ opacity: 1, y: 0 }}
        exit={{ opacity: 0, y: 6 }}
        transition={{ duration: 0.2 }}
        className="flex items-center gap-2 px-3 py-1.5 bg-white/[0.04] backdrop-blur-md rounded-full w-fit border border-white/5 text-xs text-zinc-400"
      >
        {/* Overlapping User Logos/Avatars */}
        <div className="flex items-center -space-x-1.5">
          {typers.slice(0, 3).map((user) => (
            <UserAvatar
              key={user.userId}
              name={user.username}
              size="xs"
              className="ring-1 ring-[#09090b]"
            />
          ))}
        </div>

        {/* Typing Description */}
        <span className="font-medium text-white/90 truncate max-w-[200px]">
          {formatTypingText()}
        </span>

        {/* Animated Bouncing Dots */}
        <div className="flex items-center gap-1 pl-0.5">
          <motion.span
            className="w-1.5 h-1.5 bg-indigo-500 rounded-full inline-block"
            animate={{ y: [0, -3, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0 }}
          />
          <motion.span
            className="w-1.5 h-1.5 bg-indigo-500 rounded-full inline-block"
            animate={{ y: [0, -3, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0.15 }}
          />
          <motion.span
            className="w-1.5 h-1.5 bg-indigo-500 rounded-full inline-block"
            animate={{ y: [0, -3, 0] }}
            transition={{ duration: 0.6, repeat: Infinity, delay: 0.3 }}
          />
        </div>
      </motion.div>
    </AnimatePresence>
  );
};

export default TypingIndicator;
