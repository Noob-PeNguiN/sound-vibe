# Frontend Project Structure

We follow a modular, feature-based architecture within a Monorepo context.

## Directory Layout
- `src/api/`: API integration layer. One file per controller (e.g., `auth.ts`, `asset.ts`).
- `src/assets/`: Static assets (images, global css).
- `src/components/`: Shared, dumb components (Buttons, Inputs, Cards).
- `src/composables/`: Shared logic hooks (e.g., `useTheme`, `useAuth`, `useAudio`).
- `src/layout/`: App shell layouts (Sidebar, Header).
- `src/router/`: Vue Router configuration.
- `src/stores/`: Pinia stores (Global State).
- `src/types/`: TypeScript interfaces shared across the app.
- `src/utils/`: Pure utility functions (Dates, Formatters).
- `src/views/`: Page-level components (Smart components).

## Module Rules
- **Views** should coordinate data fetching and pass data to **Components**.
- **Components** should remain pure and stateless where possible.