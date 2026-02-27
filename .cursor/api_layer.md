# API Integration Strategy

## Backend Response Standard
The backend (`sound-vibe-backend`) returns data in the following strict JSON format.
All Frontend API calls MUST type their response against this interface.

```typescript
// src/types/api.ts

// The standard wrapper from Spring Boot
export interface Result<T = any> {
  code: number;    // 200 = Success, others = Error
  message: string; // Error message or "success"
  data: T;         // The actual payload
}

// Pagination wrapper (MyBatis-Plus IPage)
export interface PageResult<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}