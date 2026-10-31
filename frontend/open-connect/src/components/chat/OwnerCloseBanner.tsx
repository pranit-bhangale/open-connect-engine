import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FiLock, FiInfo, FiX } from 'react-icons/fi';

interface OwnerCloseBannerProps {
  isOwner: boolean;
  isClosed: boolean;
  closingCountdown: number | null;
  onCloseRoom: () => void;
}

export const OwnerCloseBanner: React.FC<OwnerCloseBannerProps> = ({
  isOwner,
  isClosed,
  onCloseRoom,
}) => {
  const [dismissed, setDismissed] = useState(false);

  // If closed: keep clean (only the below bottom input banner is kept per user requirement)
  if (isClosed) {
    return null;
  }

  // Before close: optional helpful banner for the room owner
  if (isOwner && !dismissed) {
    return (
      <AnimatePresence>
        <motion.div
          initial={{ opacity: 0, y: -6, height: 'auto' }}
          animate={{ opacity: 1, y: 0, height: 'auto' }}
          exit={{ opacity: 0, y: -6, height: 0, transition: { duration: 0.2 } }}
          className="mx-3.5 sm:mx-4 mt-2 mb-1 p-3 rounded-2xl bg-gradient-to-r from-amber-500/15 via-indigo-500/10 to-transparent border border-amber-500/25 select-none relative group"
        >
          <button
            type="button"
            onClick={() => setDismissed(true)}
            className="absolute top-2.5 right-2.5 p-1 rounded-md text-zinc-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            title="Dismiss banner"
          >
            <FiX className="w-3.5 h-3.5" />
          </button>

          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2.5 pr-6 sm:pr-8">
            <div className="flex items-start gap-2.5">
              <div className="p-1.5 rounded-lg bg-amber-500/20 text-amber-300 mt-0.5">
                <FiInfo className="w-3.5 h-3.5" />
              </div>
              <div>
                <span className="text-[11px] font-bold uppercase tracking-wider text-amber-300 block">
                  Owner Control
                </span>
                <p className="text-xs text-zinc-200 font-medium">
                  Chat finished? As the owner, you can close this room once the conversation is complete.
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={onCloseRoom}
              className="self-start sm:self-auto px-3 py-1.5 rounded-xl bg-rose-500/20 hover:bg-rose-500/30 text-rose-300 border border-rose-500/30 text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer shadow-sm flex-shrink-0"
            >
              <FiLock className="w-3.5 h-3.5 text-rose-400" />
              <span>Close Room Now</span>
            </button>
          </div>
        </motion.div>
      </AnimatePresence>
    );
  }

  return null;
};

export default OwnerCloseBanner;
