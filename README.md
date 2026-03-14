# Panx

基于 Kotlin + Spring Boot 的文件网盘系统（MVP）。

当前仓库目标与协作规范：

- 产品范围与验收：`desc.md`
- 代理协作规范：`AGENTS.md`
- 自动编码执行模板：`codex-prompt.md`

## 技术栈

- Kotlin 2.2.x
- Java 21
- Spring Boot 4.x
- Spring Web MVC
- Thymeleaf
- Spring Data JDBC
- MySQL
- 本地文件系统（保存文件内容）

## MVP 功能范围

首版目标：

- 基础登录
- 文件夹管理
- 文件上传 / 下载 / 删除 / 重命名 / 移动
- 文件列表与搜索
- 分享链接
- 图片 / 文本基础预览
- Thymeleaf 页面
- 基础测试

非首版（仅预留扩展点）：标签分类、评论讨论、版本历史、回收站、RBAC、多租户、云存储等。

## 运行前准备

请先准备：

- JDK 21
- 可用的 MySQL 实例
- 可写的本地文件存储目录

> 当前仓库 `application.yaml` 仅包含应用名。数据库与存储相关配置建议在后续实现中补齐（见 `desc.md` 和 `AGENTS.md`）。

## 本地启动

```bash
cd /Users/read/IdeaProjects/panx
./gradlew bootRun
```

## 运行测试

```bash
cd /Users/read/IdeaProjects/panx
./gradlew test
```

## 建议配置项（待实现时补齐）

建议在 `application.yaml` 中逐步补充以下配置：

- `spring.datasource.url`
- `spring.datasource.username`
- `spring.datasource.password`
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`
- `panx.storage.root-dir`
- `panx.share.default-expire-hours`
- `panx.security.init-admin.enabled`
- `panx.security.init-admin.username`
- `panx.security.init-admin.password`

## 目录建议

建议按以下分层组织代码：

- `config`
- `controller`
- `service`
- `repository`
- `domain`
- `dto`
- `exception`
- `support` / `common`
- `templates`
- `static`

## 开发流程建议

1. 先冻结共享契约（实体字段、表结构、API、DTO、配置项）
2. 再并行开发（认证 / 文件 / 分享 / 页面）
3. 最后统一联调、补测试、更新 README

详细规则见：`AGENTS.md`。

## API 范围（MVP 参考）

认证：

- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`

文件：

- `GET /api/files?parentId=`
- `POST /api/files/folders`
- `POST /api/files/upload`
- `GET /api/files/{id}/download`
- `GET /api/files/{id}/preview`
- `PATCH /api/files/{id}/rename`
- `PATCH /api/files/{id}/move`
- `DELETE /api/files/{id}`
- `GET /api/files/search?keyword=`

分享：

- `POST /api/shares`
- `GET /api/shares/{token}`
- `POST /api/shares/{token}/verify`
- `GET /api/shares/{token}/download`
- `DELETE /api/shares/{id}`

## 测试建议

至少覆盖：

- 启动测试
- 认证流程
- 创建文件夹
- 上传 / 下载
- 重命名 / 移动 / 删除
- 分享创建与访问

## 验收检查（MVP）

- [ ] 可编译
- [ ] 可启动
- [ ] 关键测试通过
- [ ] 页面可访问
- [ ] 核心 API 可用
- [ ] 未实现高级能力已标注为扩展点

