import React, { useState, useRef, useEffect } from 'react';
import { FiSend, FiLock } from 'react-icons/fi';

interface ChatInputProps {
  onSendMessage: (content: string) => void;
  onTyping: () => void;
  isRoomClosed: boolean;
  closingCountdown?: number | null;
  disabled?: boolean;
}

export const ChatInput: React.FC<ChatInputProps> = ({
  onSendMessage,
  onTyping,
  isRoomClosed,
  closingCountdown,
  disabled = false,
}) => {
  const [content, setContent] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (!isRoomClosed && !disabled) {
      inputRef.current?.focus();
    }
  }, [isRoomClosed, disabled]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!content.trim() || isRoomClosed || disabled) return;

    onSendMessage(content.trim());
    setContent('');
  };

  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    setContent(e.target.value);
    onTyping();
  };

  if (isRoomClosed) {
    return (
      <div className="p-3 bg-[#121218] border-t border-rose-500/20 rounded-b-3xl">
        <div className="flex items-center justify-center gap-2 py-2 px-3 rounded-xl bg-rose-500/15 border border-rose-500/30 text-rose-300 text-xs font-medium text-center">
          <FiLock className="w-4 h-4 text-rose-400 flex-shrink-0" />
          <span>
            {closingCountdown !== undefined && closingCountdown !== null
              ? `Room closed by owner. Closing and redirecting to Home in ${closingCountdown}s...`
              : 'This room has been closed by the owner.'}
          </span>
        </div>
      </div>
    );
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="p-3 bg-[#121218] border-t border-white/[0.08] rounded-b-3xl"
    >
      <div className="flex items-center gap-2">
        <input
          ref={inputRef}
          type="text"
          value={content}
          onChange={handleChange}
          disabled={disabled}
          placeholder="Type a message..."
          className="flex-1 px-4 py-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all disabled:opacity-50"
        />

        <button
          type="submit"
          disabled={!content.trim() || disabled}
          aria-label="Send message"
          className="p-2.5 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 text-white hover:opacity-90 active:scale-95 transition-all shadow-glass disabled:opacity-40 disabled:cursor-not-allowed disabled:active:scale-100 flex-shrink-0 cursor-pointer"
        >
          <FiSend className="w-4 h-4" />
        </button>
      </div>
    </form>
  );
};

export default ChatInput;
