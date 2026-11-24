# FirstDay AI frontend

The original React and TypeScript interface connected to the Java edition.

Run npm ci, then npm run dev in this directory. Vite binds to 127.0.0.1:5174 and proxies /api to the Java backend on 127.0.0.1:8002.

Run npm run build for the TypeScript check and production bundle. Serve frontend and backend behind the same origin in deployment; the development proxy is not included in the production bundle.

See the root README for database and Java backend setup.
