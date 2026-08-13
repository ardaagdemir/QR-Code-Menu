# --- Dependencies ---
FROM node:20-alpine AS deps
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci

# --- Build ---
FROM node:20-alpine AS build
WORKDIR /app
COPY --from=deps /app/node_modules ./node_modules
COPY . .
# NEXT_PUBLIC_* vars are inlined into the JS bundle at build time (Next.js standalone
# output), not read at container start. Must be passed as --build-arg with the real
# backend origin for any non-local build. No default here on purpose: local dev's
# docker-compose.yml supplies http://localhost:8080 as a build arg default, while
# docker-compose.prod.yml requires the caller to set it (build fails otherwise) - see
# infra/.env.prod.example. lib/api.ts's own fallback only covers `next dev` without Docker.
ARG NEXT_PUBLIC_API_BASE_URL
ENV NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL}
RUN npm run build

# --- Runtime (Next.js standalone output) ---
FROM node:20-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
RUN addgroup --system --gid 1001 nodejs && adduser --system --uid 1001 nextjs

COPY --from=build /app/public ./public
COPY --from=build --chown=nextjs:nodejs /app/.next/standalone ./
COPY --from=build --chown=nextjs:nodejs /app/.next/static ./.next/static

USER nextjs
EXPOSE 3000
ENV PORT=3000
# Next.js standalone's server.js binds to $HOSTNAME, which Docker sets to the
# container ID by default - without this override the server only listens on the
# container's interface IP, not localhost/127.0.0.1 (breaks in-container healthchecks).
ENV HOSTNAME=0.0.0.0
ENTRYPOINT ["node", "server.js"]
