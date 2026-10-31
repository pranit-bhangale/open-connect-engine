import React, { useState, useEffect } from 'react';
import ChatWindow from './components/chat/ChatWindow';
import SpaceBackground from './components/common/SpaceBackground';
import { checkBackendHealth } from './components/chat/chatApi';
import { FiMessageSquare } from 'react-icons/fi';

const App: React.FC = () => {
  const [isBackendOnline, setIsBackendOnline] = useState<boolean>(false);

  useEffect(() => {
    let isMounted = true;

    const check = () => {
      checkBackendHealth().then((res) => {
        if (isMounted) {
          setIsBackendOnline(Boolean(res.isOnline));
        }
      });
    };

    check();
    const interval = setInterval(check, 5000);

    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  return (
    <div className="h-screen w-screen overflow-hidden text-[#f4f4f5] flex flex-col font-sans selection:bg-indigo-500 selection:text-white relative bg-[#06070c]">
      {/* Living Space Universe Background (Moving Stars & Constellations) */}
      <SpaceBackground />

      {/* Top Navbar */}
      <header className="border-b border-white/[0.08] bg-[#090a12]/80 backdrop-blur-xl sticky top-0 z-40 px-4 sm:px-6 py-3 flex items-center justify-between flex-shrink-0">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-indigo-500/20 to-purple-600/20 border border-indigo-500/30 flex items-center justify-center text-indigo-400 font-bold text-lg shadow-inner">
            <FiMessageSquare className="w-5 h-5" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <span className="font-bold text-base sm:text-lg tracking-tight text-white">
                Open Connect
              </span>
              <span className="px-2 py-0.5 rounded-full text-[10px] font-semibold bg-indigo-500/10 text-indigo-400 border border-indigo-500/20">
                Live
              </span>
            </div>
            <p className="text-xs text-zinc-400 hidden sm:block">
              Real-time room-based messaging engine
            </p>
          </div>
        </div>

        {/* Right Status Indicator: Only Green dot for connect, Red dot for not connect */}
        <div
          className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-white/[0.04] border border-white/10 text-xs backdrop-blur-md"
          title={isBackendOnline ? 'Backend Connected' : 'Backend Disconnected'}
        >
          <span className="relative flex h-2.5 w-2.5">
            <span
              className={`animate-ping absolute inline-flex h-full w-full rounded-full opacity-75 ${
                isBackendOnline ? 'bg-emerald-400' : 'bg-rose-400'
              }`}
            />
            <span
              className={`relative inline-flex rounded-full h-2.5 w-2.5 ${
                isBackendOnline ? 'bg-emerald-500' : 'bg-rose-500'
              }`}
            />
          </span>
          <span className="text-zinc-300 font-medium text-xs">
            {isBackendOnline ? 'Connected' : 'Disconnected'}
          </span>
        </div>
      </header>

      {/* Main Fullscreen Chat Area */}
      <main className="flex-1 w-full max-w-7xl mx-auto p-2 sm:p-4 md:p-5 flex flex-col min-h-0 relative z-10">
        <ChatWindow />
      </main>
    </div>
  );
};

export default App;
