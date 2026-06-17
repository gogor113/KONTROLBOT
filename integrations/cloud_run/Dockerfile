# Use official Node.js runtime
FROM node:20-slim
WORKDIR /app
COPY package*.json ./
RUN npm ci --production
COPY . .
ENV PORT 8080
CMD ["npm","start"]
