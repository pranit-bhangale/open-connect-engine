import React from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { FiAlertTriangle, FiLock, FiLogOut, FiX } from 'react-icons/fi';

export interface ConfirmModalProps {
  isOpen: boolean;
  type?: 'danger' | 'warning';
  title: string;
  message: string;
  confirmText?: string;
  cancelText?: string;
  icon?: 'lock' | 'logout' | 'alert';
  loading?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export const ConfirmModal: React.FC<ConfirmModalProps> = ({
  isOpen,
  type = 'danger',
  title,
  message,
  confirmText = 'Confirm',
  cancelText = 'Cancel',
  icon = 'alert',
  loading = false,
  onConfirm,
  onCancel,
}) => {
  return (
    <AnimatePresence>
      {isOpen && (
        <div className="absolute inset-0 z-50 flex items-center justify-center p-4">
          {/* Backdrop */}
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            transition={{ duration: 0.15 }}
            onClick={onCancel}
            className="absolute inset-0 bg-black/70 backdrop-blur-sm rounded-3xl"
          />

          {/* Modal Dialog Card */}
          <motion.div
            initial={{ opacity: 0, scale: 0.92, y: 12 }}
            animate={{ opacity: 1, scale: 1, y: 0 }}
            exit={{ opacity: 0, scale: 0.92, y: 12 }}
            transition={{ type: 'spring', damping: 25, stiffness: 350 }}
            onClick={(e) => e.stopPropagation()}
            className="relative z-10 w-full max-w-[340px] p-5 rounded-2xl bg-[#121218] border border-white/15 shadow-2xl text-center select-none"
          >
            {/* Close Button */}
            <button
              type="button"
              onClick={onCancel}
              aria-label="Close"
              className="absolute top-3.5 right-3.5 p-1 rounded-lg text-zinc-400 hover:text-white hover:bg-white/10 transition-colors cursor-pointer"
            >
              <FiX className="w-4 h-4" />
            </button>

            {/* Icon Circle */}
            <div className="mx-auto mb-3.5 flex items-center justify-center">
              <div
                className={`p-3 rounded-2xl ${
                  type === 'danger'
                    ? 'bg-rose-500/15 text-rose-400 border border-rose-500/30'
                    : 'bg-amber-500/15 text-amber-400 border border-amber-500/30'
                }`}
              >
                {icon === 'lock' && <FiLock className="w-6 h-6" />}
                {icon === 'logout' && <FiLogOut className="w-6 h-6" />}
                {icon === 'alert' && <FiAlertTriangle className="w-6 h-6" />}
              </div>
            </div>

            {/* Title */}
            <h3 className="text-base font-bold text-white mb-1.5">{title}</h3>

            {/* Description */}
            <p className="text-xs text-zinc-400 leading-relaxed mb-5 px-1">{message}</p>

            {/* Action Buttons */}
            <div className="flex items-center gap-2.5">
              <button
                type="button"
                onClick={onCancel}
                disabled={loading}
                className="flex-1 py-2 px-3 rounded-xl bg-white/[0.06] hover:bg-white/[0.12] border border-white/10 text-xs font-semibold text-zinc-300 hover:text-white transition-all cursor-pointer disabled:opacity-50"
              >
                {cancelText}
              </button>

              <button
                type="button"
                onClick={onConfirm}
                disabled={loading}
                className={`flex-1 py-2 px-3 rounded-xl text-xs font-semibold text-white shadow-md transition-all cursor-pointer disabled:opacity-50 ${
                  type === 'danger'
                    ? 'bg-rose-600 hover:bg-rose-500 active:bg-rose-700'
                    : 'bg-amber-600 hover:bg-amber-500 active:bg-amber-700'
                }`}
              >
                {loading ? 'Processing...' : confirmText}
              </button>
            </div>
          </motion.div>
        </div>
      )}
    </AnimatePresence>
  );
};

export default ConfirmModal;
