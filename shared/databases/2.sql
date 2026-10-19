CREATE TABLE history_entry (
    comic_id TEXT NOT NULL PRIMARY KEY,
    comic_json TEXT NOT NULL,
    episode_id TEXT NOT NULL,
    episode_title TEXT NOT NULL,
    page_index INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

CREATE TABLE reading_progress (
    comic_id TEXT NOT NULL,
    episode_id TEXT NOT NULL,
    page_index INTEGER NOT NULL,
    reader_mode TEXT NOT NULL,
    updated_at INTEGER NOT NULL,
    PRIMARY KEY (comic_id, episode_id)
);

CREATE TABLE download_task (
    id TEXT NOT NULL PRIMARY KEY,
    comic_json TEXT NOT NULL,
    episode_json TEXT NOT NULL,
    status TEXT NOT NULL,
    completed_pages INTEGER NOT NULL,
    total_pages INTEGER NOT NULL,
    retry_count INTEGER NOT NULL,
    target_location TEXT NOT NULL,
    error TEXT
);

CREATE TABLE app_setting (
    key TEXT NOT NULL PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE title_translation_cache (
    source_text TEXT NOT NULL,
    target_language TEXT NOT NULL,
    model_id TEXT NOT NULL,
    prompt_version INTEGER NOT NULL,
    translated_text TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    last_accessed_at INTEGER NOT NULL,
    size_bytes INTEGER NOT NULL,
    PRIMARY KEY (source_text, target_language, model_id, prompt_version)
);
