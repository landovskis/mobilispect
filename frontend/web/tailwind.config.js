/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  important: true,
  theme: {
    extend: {
      colors: {
        primary: '#0b4f8a',
        accent: '#00a7c4',
        warn: '#ffd54f',
        ms: {
          blue: '#0b4f8a',
          cyan: '#00a7c4',
          navy: '#0b3558',
          yellow: '#ffd54f',
          ink: '#111827',
          muted: '#6b7280',
          border: '#d1d5db',
          bg: '#f9fafb',
        },
      },
      fontFamily: {
        ms: [
          'system-ui',
          '-apple-system',
          'BlinkMacSystemFont',
          '"SF Pro Text"',
          '"Segoe UI"',
          'sans-serif',
        ],
      },
    },
  },
  plugins: [],
};
