import React, { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import {
  FiPlusCircle,
  FiLogIn,
  FiAlertCircle,
  FiLoader,
  FiMessageSquare,
} from 'react-icons/fi';
import { createRoomApi, joinRoomApi, ServiceUnavailableError } from './chatApi';
import type { RoomResponse } from './types';

interface RoomEntryFormProps {
  onSuccess: (data: RoomResponse) => void;
  onError?: (err: unknown) => void;
}

export const RoomEntryForm: React.FC<RoomEntryFormProps> = ({
  onSuccess,
  onError,
}) => {
  const [mode, setMode] = useState<'create' | 'join'>(() => {
    try {
      const params = new URLSearchParams(window.location.search);
      const hashParams = new URLSearchParams(window.location.hash.split('?')[1] || '');
      if (params.get('code') || hashParams.get('code')) {
        return 'join';
      }
    } catch {
      // ignore
    }
    return 'create';
  });

  // Shared user nickname
  const [username, setUsername] = useState<string>('');

  // Clean up any legacy saved credentials and listen for invite links
  useEffect(() => {
    try {
      localStorage.removeItem('open_connect_username');
      localStorage.removeItem('open_connect_email');
    } catch {
      // ignore
    }

    const checkInviteCode = () => {
      try {
        const params = new URLSearchParams(window.location.search);
        const hashParams = new URLSearchParams(window.location.hash.split('?')[1] || '');
        const code = (params.get('code') || hashParams.get('code') || '').toUpperCase().trim();
        if (code) {
          setRoomCode(code);
          setMode('join');
        }
      } catch {
        // ignore
      }
    };

    checkInviteCode();
    window.addEventListener('hashchange', checkInviteCode);
    return () => window.removeEventListener('hashchange', checkInviteCode);
  }, []);

  // Create room fields
  const [roomName, setRoomName] = useState('');
  const [description, setDescription] = useState('');

  // Join room fields
  const [roomCode, setRoomCode] = useState<string>(() => {
    try {
      const params = new URLSearchParams(window.location.search);
      const hashParams = new URLSearchParams(window.location.hash.split('?')[1] || '');
      return (params.get('code') || hashParams.get('code') || '').toUpperCase();
    } catch {
      return '';
    }
  });

  // State
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCreateRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!username.trim()) {
      setError('Please enter your name.');
      return;
    }
    if (!roomName.trim()) {
      setError('Please provide a room name.');
      return;
    }

    setLoading(true);
    try {
      const data = await createRoomApi(username, roomName, description);
      onSuccess(data);
    } catch (err) {
      if (err instanceof ServiceUnavailableError && onError) {
        onError(err);
      } else {
        setError(err instanceof Error ? err.message : 'Failed to create room');
      }
    } finally {
      setLoading(false);
    }
  };

  const handleJoinRoom = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!username.trim()) {
      setError('Please enter your name.');
      return;
    }
    if (!roomCode.trim()) {
      setError('Please enter the 6-digit room code.');
      return;
    }

    setLoading(true);
    try {
      const data = await joinRoomApi(username, roomCode);
      onSuccess(data);
    } catch (err) {
      if (err instanceof ServiceUnavailableError && onError) {
        onError(err);
      } else {
        setError(err instanceof Error ? err.message : 'Failed to join room');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="flex flex-col h-full overflow-y-auto px-5 sm:px-8 py-6 relative scrollbar-thin scrollbar-thumb-white/10">
      <div className="w-full max-w-sm sm:max-w-md mx-auto my-auto py-2">
        {/* Brand Header */}
        <div className="text-center mb-6">
          <div className="inline-flex p-3.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 mb-3 shadow-glass">
            <FiMessageSquare className="w-7 h-7" />
          </div>
          <h2 className="text-2xl font-bold text-white tracking-tight">Open Connect Live</h2>
          <p className="text-xs text-zinc-400 mt-1.5">
            Create or join real-time chat rooms in the open universe
          </p>
        </div>

        {/* Feature Tabs: Create Room / Join Room */}
        <div className="flex p-1 bg-white/[0.04] border border-white/[0.08] rounded-xl mb-6">
          <button
            type="button"
            onClick={() => {
              setMode('create');
              setError(null);
            }}
            className={`flex-1 flex items-center justify-center gap-2 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
              mode === 'create'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'text-zinc-400 hover:text-white hover:bg-white/[0.02]'
            }`}
          >
            <FiPlusCircle className="text-sm" />
            <span>Create Room</span>
          </button>
          <button
            type="button"
            onClick={() => {
              setMode('join');
              setError(null);
            }}
            className={`flex-1 flex items-center justify-center gap-2 py-2 text-xs font-semibold rounded-lg transition-all cursor-pointer ${
              mode === 'join'
                ? 'bg-indigo-600 text-white shadow-md'
                : 'text-zinc-400 hover:text-white hover:bg-white/[0.02]'
            }`}
          >
            <FiLogIn className="text-sm" />
            <span>Join Room</span>
          </button>
        </div>

        {/* Error Alert */}
        <AnimatePresence mode="wait">
          {error && (
            <motion.div
              initial={{ opacity: 0, y: -6 }}
              animate={{ opacity: 1, y: 0 }}
              exit={{ opacity: 0, y: -6 }}
              className="flex items-start gap-2.5 p-3 rounded-xl bg-rose-500/10 border border-rose-500/30 text-rose-300 text-xs mb-4"
            >
              <FiAlertCircle className="w-4 h-4 flex-shrink-0 mt-0.5 text-rose-400" />
              <span className="flex-1">{error}</span>
            </motion.div>
          )}
        </AnimatePresence>

        {/* Forms */}
        <AnimatePresence mode="wait">
          {mode === 'create' ? (
            <motion.form
              key="create-form"
              initial={{ opacity: 0, x: -10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: 10 }}
              transition={{ duration: 0.2 }}
              onSubmit={handleCreateRoom}
              className="space-y-4"
            >
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1.5">
                  Your Nickname <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  autoComplete="off"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter your name"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1.5">
                  Room Name <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  value={roomName}
                  onChange={(e) => setRoomName(e.target.value)}
                  placeholder="Enter a name for your room"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1.5">
                  Description (Optional)
                </label>
                <textarea
                  rows={2}
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="What is this chat about?"
                  className="w-full px-3.5 py-2 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all resize-none"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full mt-2 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white font-semibold text-sm shadow-glass hover:shadow-glass-hover transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {loading ? (
                  <>
                    <FiLoader className="w-4 h-4 animate-spin" />
                    <span>Creating Room...</span>
                  </>
                ) : (
                  <>
                    <FiPlusCircle className="w-4 h-4" />
                    <span>Create & Enter Room</span>
                  </>
                )}
              </button>
            </motion.form>
          ) : (
            <motion.form
              key="join-form"
              initial={{ opacity: 0, x: 10 }}
              animate={{ opacity: 1, x: 0 }}
              exit={{ opacity: 0, x: -10 }}
              transition={{ duration: 0.2 }}
              onSubmit={handleJoinRoom}
              className="space-y-4"
            >
              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1.5">
                  Your Nickname <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  autoComplete="off"
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  placeholder="Enter your name"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all"
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-zinc-300 mb-1.5">
                  6-Digit Room Code <span className="text-rose-400">*</span>
                </label>
                <input
                  type="text"
                  required
                  maxLength={6}
                  value={roomCode}
                  onChange={(e) => setRoomCode(e.target.value.toUpperCase())}
                  placeholder="Enter the room code"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-white/[0.04] border border-white/[0.08] text-sm text-white tracking-widest text-center font-mono placeholder:tracking-normal placeholder:font-sans placeholder:text-zinc-500 focus:outline-none focus:border-indigo-500/60 focus:ring-1 focus:ring-indigo-500/40 transition-all"
                />
              </div>

              <button
                type="submit"
                disabled={loading}
                className="w-full mt-2 py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white font-semibold text-sm shadow-glass hover:shadow-glass-hover transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
              >
                {loading ? (
                  <>
                    <FiLoader className="w-4 h-4 animate-spin" />
                    <span>Joining Room...</span>
                  </>
                ) : (
                  <>
                    <FiLogIn className="w-4 h-4" />
                    <span>Join Chat Room</span>
                  </>
                )}
              </button>
            </motion.form>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
};

export default RoomEntryForm;
