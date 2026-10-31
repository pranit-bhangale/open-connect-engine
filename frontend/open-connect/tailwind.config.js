/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        primary: '#6366F1', // Indigo
        'primary-dark': '#4F46E5',
        background: '#09090b',
        text: '#f4f4f5',
        muted: '#a1a1aa',
        'glass-white': 'rgba(255, 255, 255, 0.03)',
        'glass-border': 'rgba(255, 255, 255, 0.08)',
      },
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        '3xl': '18px',
        '4xl': '24px',
      },
      boxShadow: {
        'glass': '0 8px 32px rgba(99, 102, 241, 0.12)',
        'glass-hover': '0 8px 32px rgba(99, 102, 241, 0.20)',
      },
    },
  },
  plugins: [],
};
