import React from 'react';
import { FiWifiOff, FiRefreshCw } from 'react-icons/fi';

interface ServiceUnavailableViewProps {
  onRetry: () => void;
  isRetrying: boolean;
}

export const ServiceUnavailableView: React.FC<ServiceUnavailableViewProps> = ({
  onRetry,
  isRetrying,
}) => {
  return (
    <div className="flex flex-col items-center justify-center h-full px-6 py-8 text-center select-none my-auto">
      {/* Offline Icon */}
      <div className="relative mb-6">
        <div className="p-4 rounded-2xl bg-rose-500/10 border border-rose-500/30 text-rose-400 shadow-glass">
          <FiWifiOff className="w-8 h-8" />
        </div>
      </div>

      {/* Header */}
      <h3 className="text-xl font-bold text-white mb-2">
        Service Unavailable
      </h3>
      <p className="text-xs text-zinc-400 mb-6 max-w-xs">
        Cannot connect to the live chat engine. Please ensure the backend server is running and try again.
      </p>

      {/* Retry Button */}
      <button
        type="button"
        onClick={onRetry}
        disabled={isRetrying}
        className="w-full max-w-[240px] py-3 px-4 rounded-xl bg-gradient-to-r from-indigo-600 to-indigo-700 hover:from-indigo-500 hover:to-indigo-600 text-white text-xs font-semibold shadow-glass hover:shadow-glass-hover transition-all flex items-center justify-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer"
      >
        <FiRefreshCw className={`w-3.5 h-3.5 ${isRetrying ? 'animate-spin' : ''}`} />
        <span>{isRetrying ? 'Retrying Connection...' : 'Retry Connection'}</span>
      </button>
    </div>
  );
};

export default ServiceUnavailableView;
