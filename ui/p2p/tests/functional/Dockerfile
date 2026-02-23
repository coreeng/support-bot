FROM mcr.microsoft.com/playwright:v1.58.0-noble

WORKDIR /app

# Install dependencies
COPY package.json yarn.lock* ./
RUN yarn --frozen-lockfile;

# Copy only the needed files and folders individually
COPY tsconfig.json cucumber.json ./
COPY tests ./tests

# Cucumber writes reports under /app; ensure runtime user can write there.
RUN chown -R pwuser:pwuser /app

USER pwuser

CMD ["yarn", "test:cucumber:ci"]
