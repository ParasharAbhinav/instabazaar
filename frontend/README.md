# Frontend

React, TypeScript, React Router, TanStack Query, and Vite. Requires Node.js 22.12 or later.

```sh
npm ci
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` to http://localhost:8080. Start the backend separately; see the root README.

Copy `.env.example` to `.env.local` to change public frontend configuration. `VITE_` variables are included in browser assets; keep secrets out of them. Demo fixtures are empty and demo mode is disabled in the current source.

```sh
npm test
npm run build
npm run preview
```

Production output goes to `dist/`. The build also checks generated JavaScript for known demo fixture strings. The root Compose stack serves the frontend and API on the same HTTPS origin through `deploy/nginx-tls.conf`.

## Structure

- `src/api/`: DTO interfaces, cookie/CSRF transport, and empty demo compatibility exports.
- `src/features/`: authentication, catalogue, buyer, seller, and moderation screens.
- `src/components/`: shared UI.
- `src/utils/`: validation helpers.
- `src/test/`: transport, feature, and validation tests.

The API enforces authorization, ownership, validation, and concurrency rules. Registration and login are separate operations. Stock is aggregate rather than per-size inventory. Payments and checkout are outside the application scope.

The store editor currently requires a logo upload when saving an existing store with a logo because the public store response does not include its managed media asset ID.

Bundled SVG illustrations are placeholder visuals rather than real seller inventory. Google Fonts are optional; system-font fallbacks apply offline.

See the root `VERIFICATION.md` for checks and verification limits.

## Reel and sample support

The `/sample` route contains an isolated sample seller editor and six searchable fictional listings. It does not write to the live API. Use `npm run sample` to open it. The real seller editor accepts a Reel URL and optional caption, and selected text-derived keywords are saved through the normal product API. See the root `SAMPLE-GUIDE.md` for complete steps.
