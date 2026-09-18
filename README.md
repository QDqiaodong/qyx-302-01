# qyx-302 文创投稿作品综合分值自动分级评审系统

## 项目简介
文创投稿作品综合分值自动分级评审系统，包含 Spring Boot 后端、Vue/Vite 前端、MySQL 和 Redis。

## 前端访问地址
- 默认地址：http://localhost:8202
- 127.0.0.1：http://127.0.0.1:8202

## 端口
- 前端：8202
- 后端 API：8302
- MySQL：3402
- Redis：6502

## 启动命令
```bash
sh start.sh
```

## 验证命令
```bash
cd backend && mvn compile -q
cd ../frontend && npm ci && npm run build
cd .. && docker compose up -d --build
curl -sS http://localhost:8202
curl -sS http://127.0.0.1:8202
```
