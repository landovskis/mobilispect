/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./src/**/*.{html,ts}'],
  important: true,
  theme: {
    extend: {
      colors: {
        primary: '#0b4f8a',
        accent: '#00a7c4',
        warn: '#f44336',
      },
    },
  },
  plugins: [],
};
