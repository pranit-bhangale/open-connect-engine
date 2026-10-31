import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FiLogIn, FiLogOut, FiLock, FiInfo } from 'react-icons/fi';
import type { ChatNotification } from './types';

interface ChatNotificationsProps {
  notifications: ChatNotification[];
}

function formatNotificationTime(timestamp?: number, timeString?: string): string {
  if (timeString) return timeString;
  if (!timestamp) return '';
  try {
    const d = new Date(timestamp);
    if (isNaN(d.getTime())) return '';
    return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  } catch {
    return '';
  }
}

export const ChatNotifications: React.FC<ChatNotificationsProps> = ({ notifications }) => {
  const visibleNotifications = notifications.filter((n) => n.type !== 'CLOSED');
  if (!visibleNotifications || visibleNotifications.length === 0) return null;

  return (
    <div className="absolute top-16 left-0 right-0 z-30 pointer-events-none flex flex-col items-center gap-1.5 px-4">
      <AnimatePresence>
        {visibleNotifications.map((n) => {
          let icon = <FiInfo className="text-blue-400" />;
          let bgClass = 'bg-blue-500/15 border-blue-500/25 text-blue-200';

          if (n.type === 'JOINED') {
            icon = <FiLogIn className="text-emerald-400" />;
            bgClass = 'bg-emerald-500/15 border-emerald-500/30 text-emerald-200';
          } else if (n.type === 'LEFT') {
            icon = <FiLogOut className="text-amber-400" />;
            bgClass = 'bg-amber-500/15 border-amber-500/30 text-amber-200';
          } else if (n.type === 'CLOSED') {
            icon = <FiLock className="text-rose-400" />;
            bgClass = 'bg-rose-500/20 border-rose-500/40 text-rose-200';
          }

          const timeStr = formatNotificationTime(n.timestamp, n.timeString);

          return (
            <motion.div
              key={n.id}
              initial={{ opacity: 0, y: -10, scale: 0.95 }}
              animate={{ opacity: 1, y: 0, scale: 1 }}
              exit={{ opacity: 0, y: -8, scale: 0.95 }}
              transition={{ type: 'spring', damping: 25, stiffness: 350 }}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-full text-xs font-medium border backdrop-blur-md shadow-lg ${bgClass}`}
            >
              <span className="text-sm">{icon}</span>
              <span className="font-medium">{n.message}</span>
              {timeStr && (
                <span className="opacity-75 text-[10px] font-mono ml-1 px-1.5 py-0.5 rounded bg-white/10">
                  {timeStr}
                </span>
              )}
            </motion.div>
          );
        })}
      </AnimatePresence>
    </div>
  );
};

export default ChatNotifications;
