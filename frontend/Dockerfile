# Multistage build: compila a SPA Vue e serve o output estático via nginx.
FROM node:24-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /app/dist /usr/share/nginx/html
# Fallback de SPA para o Vue Router (history mode): qualquer rota desconhecida
# cai em index.html e o router client-side decide.
RUN printf 'server {\n    listen 80;\n    location / {\n        root /usr/share/nginx/html;\n        try_files $uri $uri/ /index.html;\n    }\n}\n' \
  > /etc/nginx/conf.d/default.conf
