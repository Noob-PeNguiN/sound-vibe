# Database Schema Design (MySQL 8)

## 1. Auth Module (`users`)
- `id` (BIGINT, PK): Auto increment.
- `username` (VARCHAR): Unique.
- `password_hash` (VARCHAR): BCrypt.
- `role` (VARCHAR): 'PRODUCER', 'ADMIN'.
- `created_at` (DATETIME).

## 2. Asset Module
### Table: `assets`
Core table for file management.
- `id` (BIGINT, PK).
- `asset_code` (VARCHAR): UUID, unique external identifier.
- `user_id` (BIGINT): Ref `users.id`.
- `original_name` (VARCHAR): Original filename.
- `storage_name` (VARCHAR): MinIO object key.
- `url` (VARCHAR): Presigned or public URL.
- `size` (BIGINT): File size in bytes.
- `extension` (VARCHAR): File extension.
- `type` (VARCHAR): 'AUDIO', 'IMAGE', 'STEM'.
- `status` (TINYINT): 0=Uploading, 1=Normal, 2=Deleted.
- `deleted` (TINYINT): Logical delete.
- `create_time` (DATETIME).
- `update_time` (DATETIME).

### Table: `audio_meta`
Technical details for the audio.
- `asset_id` (BIGINT, PK): FK `assets.id`.
- `bpm` (INT): Detected beats per minute.
- `musical_key` (VARCHAR): e.g., "C Major", "F# Minor".
- `waveform_url` (VARCHAR): URL to generated PNG waveform.
- `feature_vector` (JSON): Stored as JSON array for backup (Primary usage is in ES).

## 3. Catalog Module
### Table: `tracks`
Music works (Beat/Track) with metadata and asset links.
- `id` (BIGINT, PK): Auto increment.
- `title` (VARCHAR 200): Track title, NOT NULL.
- `producer_id` (BIGINT): Ref `users.id` (Logical FK).
- `cover_id` (BIGINT): Ref `assets.id` (IMAGE type, Logical FK).
- `audio_id` (BIGINT): Ref `assets.id` (AUDIO type, Logical FK).
- `bpm` (INT): Beats per minute (e.g., 140).
- `musical_key` (VARCHAR 20): e.g., "C Minor", "F# Major".
- `genre` (VARCHAR 50): e.g., "Trap", "Lo-Fi", "R&B".
- `price` (DECIMAL 10,2): Future marketplace.
- `status` (TINYINT): 0=Draft, 1=Published.
- `tags` (VARCHAR 500): Comma-separated (e.g., "dark,hard,808").
- `deleted` (TINYINT): Logical delete.
- `create_time` (DATETIME).
- `update_time` (DATETIME).

## 4. Market Module
### Table: `asset_license`
- `id` (BIGINT, PK).
- `asset_id` (BIGINT): FK `assets.id`.
- `license_type` (VARCHAR): 'ROYALTY_FREE', 'EXCLUSIVE'.
- `price` (DECIMAL).
- `stock` (INT): -1 for unlimited.

## 5. Search Module (Elasticsearch Index)
### Index: `assets`
- `id`: keyword.
- `title`: text (analyzer = standard).
- `bpm`: integer.
- `musical_key`: keyword.
- `tags`: keyword[].
- `feature_vector`: dense_vector (dims=512, similarity=cosine).
