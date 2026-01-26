# Muusikoiden.net Bot

A Clojure bot that scrapes muusikoiden.net marketplace (Finnish musician gear marketplace) for new listings. Sends notifications via Telegram. Runs as a one-shot process designed to be scheduled via Docker/cron.

## Quick Start

```bash
# Download dependencies
clojure -P

# Run the bot
clojure -M:run
```

## Configuration

### Telegram Setup

1. Create a bot via [@BotFather](https://t.me/botfather) and get the API key
2. Create a group/chat and add your bot
3. Send a message to the bot to initialize
4. Configure via `data/config.json` or environment variables

**Option 1: Config file** (`data/config.json`):
```json
{
  "telegram": {
    "enabled": true,
    "api_key": "YOUR_BOT_API_KEY",
    "chat_id": "YOUR_CHAT_ID"
  }
}
```

**Option 2: Environment variables** (override config file):
```bash
export TELEGRAM_API_KEY=your-api-key
export TELEGRAM_CHAT_ID=your-chat-id
export TELEGRAM_ENABLED=true
```

### Searches

Searches are stored in `data/searches.json` as a list of URLs:
```json
{
  "searches": [
    "https://muusikoiden.net/tori/haku.php?keyword=strandberg",
    "https://muusikoiden.net/tori/?province=Keski-Suomi&type=sell&category=8"
  ]
}
```

## Telegram Commands

Manage searches via Telegram (processed on each bot run):

| Command | Description |
|---------|-------------|
| `/add <url>` | Add a search URL |
| `/remove <number>` | Remove search by number |
| `/list` | List all searches |
| `/help` | Show help |

Example:
```
/add https://muusikoiden.net/tori/haku.php?keyword=Harley+Benton
```

## Docker

```bash
# Build
docker build -t muusikoidennet-bot .

# Run with config file
docker run --rm -v $(pwd)/data:/app/data muusikoidennet-bot

# Run with environment variables
docker run --rm \
  -e TELEGRAM_API_KEY=xxx \
  -e TELEGRAM_CHAT_ID=123 \
  -e TELEGRAM_ENABLED=true \
  -v $(pwd)/data:/app/data \
  muusikoidennet-bot
```

## Architecture

Single-file application (`src/muusikoidennet_bot/core.clj`) with this flow:
1. Process pending Telegram commands
2. Load search URLs from `data/searches.json`
3. Fetch and parse each search page
4. Deduplicate listings across all searches
5. Compare against seen IDs, notify only new listings
6. Save updated seen IDs to `data/seen-items.json`

**Dependencies:**
- `clj-http` - HTTP client
- `enlive` - HTML parsing
- `data.json` - JSON serialization

**Data files** (in `data/`, gitignored):
- `config.json` - Telegram configuration
- `searches.json` - Search URLs
- `seen-items.json` - Tracked listing IDs
