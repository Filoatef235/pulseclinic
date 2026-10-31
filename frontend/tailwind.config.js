/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        clinic: {
          50: "#f0f7f6",
          600: "#0f766e",
          800: "#115e59",
        },
      },
    },
  },
  plugins: [],
};
