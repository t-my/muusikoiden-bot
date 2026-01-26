# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A Clojure bot that scrapes muusikoiden.net marketplace (Finnish musician gear marketplace) for new listings. Runs as a one-shot process designed to be scheduled via Docker/cron.

## Commands

```bash
# Run the bot
clojure -M:run

# Download dependencies
clojure -P

# Docker build and run
docker build -t muusikoidennet-bot .
docker run --rm -v $(pwd)/data:/app/data muusikoidennet-bot
```

## Architecture

Single-file application (`src/muusikoidennet_bot/core.clj`) with this flow:
1. Load previously seen item IDs from `data/seen-items.json`
2. Fetch search results page using clj-http
3. Parse HTML with Enlive to extract listing IDs and titles
4. Compare against seen IDs, print only new listings
5. Save updated seen IDs to JSON

**Key dependencies:**
- `clj-http` - HTTP client
- `enlive` - HTML parsing/selection
- `data.json` - JSON serialization

**Data persistence:** `data/seen-items.json` stores seen listing IDs and last update timestamp.
