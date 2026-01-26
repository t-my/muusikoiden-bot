FROM clojure:temurin-21-tools-deps-alpine

WORKDIR /app

# Copy dependency file first for better caching
COPY deps.edn .

# Download dependencies
RUN clojure -P

# Copy source
COPY src src

# Create data directory
RUN mkdir -p data

# Run the application
CMD ["clojure", "-M:run"]
