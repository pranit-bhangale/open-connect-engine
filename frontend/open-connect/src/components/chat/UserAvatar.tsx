import React from 'react';

interface UserAvatarProps {
  name: string;
  size?: 'xs' | 'sm' | 'md' | 'lg' | 'xl';
  showOnline?: boolean;
  className?: string;
}

const GRADIENTS = [
  'from-indigo-500 to-purple-600',
  'from-blue-500 to-cyan-500',
  'from-emerald-500 to-teal-600',
  'from-rose-500 to-pink-600',
  'from-amber-500 to-orange-600',
  'from-violet-500 to-fuchsia-600',
  'from-sky-500 to-indigo-600',
  'from-teal-400 to-emerald-600',
];

function getGradientForName(name: string): string {
  if (!name) return GRADIENTS[0];
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const index = Math.abs(hash) % GRADIENTS.length;
  return GRADIENTS[index];
}

const SIZE_CLASSES = {
  xs: 'w-5 h-5 text-[10px]',
  sm: 'w-7 h-7 text-xs font-medium',
  md: 'w-9 h-9 text-sm font-semibold',
  lg: 'w-11 h-11 text-base font-bold',
  xl: 'w-14 h-14 text-lg font-bold',
};

export const UserAvatar: React.FC<UserAvatarProps> = ({
  name,
  size = 'md',
  showOnline = false,
  className = '',
}) => {
  // Start alphabet initial letter extracted cleanly
  const initial = (name || '?').trim().charAt(0).toUpperCase();
  const gradient = getGradientForName(name);

  return (
    <div className={`relative inline-flex flex-shrink-0 items-center justify-center ${className}`}>
      <div
        className={`${SIZE_CLASSES[size]} rounded-full bg-gradient-to-br ${gradient} text-white flex items-center justify-center shadow-md select-none ring-1 ring-white/10`}
        title={name}
      >
        <span>{initial}</span>
      </div>
      {showOnline && (
        <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-emerald-500 rounded-full ring-2 ring-[#09090b]" />
      )}
    </div>
  );
};

export default UserAvatar;
