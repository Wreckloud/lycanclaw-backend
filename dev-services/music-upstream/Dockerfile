FROM node:22-alpine

WORKDIR /app
RUN corepack enable && corepack prepare pnpm@10.11.0 --activate

COPY package.json pnpm-lock.yaml ./
RUN pnpm install --frozen-lockfile --prod

ENV PORT=3000
EXPOSE 3000
CMD ["node", "node_modules/NeteaseCloudMusicApi/app.js"]
