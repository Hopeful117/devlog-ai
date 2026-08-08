FROM node:22-alpine AS build

WORKDIR /workspace

COPY package.json package-lock.json ./
RUN npm ci

COPY angular.json tsconfig.json tsconfig.app.json tsconfig.spec.json ./
COPY public/ public/
COPY src/ src/

RUN npm run build


FROM nginxinc/nginx-unprivileged:1.29-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/frontend/browser/ /usr/share/nginx/html/

USER 101

EXPOSE 8080

HEALTHCHECK --interval=5s --timeout=3s --retries=10 --start-period=5s \
    CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/health || exit 1
