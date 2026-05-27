# Git 提交规范

## 1. 分支建议

```text
main         正式稳定分支，不直接开发，禁止直接 push
dev          开发集成分支，日常功能通过 PR 合并到这里
feature/*    功能分支
bugfix/*     常规修复分支
hotfix/*     线上或演示前紧急修复分支
```

团队合作时，一般不要直接 push 到 `main`。推荐使用“分支开发 + Pull Request + Review + 合并”的方式协作。

日常开发流程：

```text
feature/* 或 bugfix/* -> PR 到 dev -> 测试通过 -> PR 到 main
```

## 2. 推荐开发流程

```bash
# 1. 从主分支拉最新代码
git checkout main
git pull origin main

# 2. 新建功能分支
git checkout -b feature/login-page

# 3. 开发、提交
git add .
git commit -m "feat: add login page"

# 4. 推送自己的分支
git push origin feature/login-page
```

然后在 GitHub 上创建 Pull Request，等待队友 review。确认没问题后，再合并到 `dev` 或 `main`。

## 3. 什么时候可以直接 push

直接 push 只适合个人项目、临时实验仓库、团队明确允许直接推 `dev` 的场景，或 README typo 这类极小文档修改。

即使是小团队，也建议至少保护 `main` 分支，不允许直接 push。

## 4. 提交格式

```text
<type>: <description>
```

## 5. type 类型

| 类型 | 说明 |
|---|---|
| feat | 新功能 |
| fix | 修复 Bug |
| docs | 文档 |
| style | 样式调整 |
| refactor | 重构 |
| test | 测试 |
| chore | 构建或工具配置 |

## 6. 示例

```text
feat: 新增航班查询接口
feat: 实现 AI 智能购票助手页面
fix: 修复座位并发锁定问题
docs: 更新部署指南
refactor: 重构订单服务
```
