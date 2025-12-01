# 1. Сборка React
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install
COPY . .
# Принимаем аргумент для сборки
ARG VITE_API_BASE
ENV VITE_API_BASE=$VITE_API_BASE
RUN npm run build

# 2. Nginx (Debian-based, более стабильный чем alpine на Windows)
FROM nginx:latest
# Копируем собранный фронтенд
COPY --from=build /app/dist /usr/share/nginx/html
# Копируем конфиг
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
# CMD указывать не нужно, он есть в базовом образе