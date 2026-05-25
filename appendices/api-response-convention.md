# API 响应规范

## 1. 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

## 2. 失败响应

```json
{
  "code": 20002,
  "message": "座位已被占用",
  "data": null
}
```

## 3. 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 10
  }
}
```

## 4. 命名规范

- 请求字段使用 camelCase；
- 响应字段使用 camelCase；
- 时间格式使用 ISO 或 `yyyy-MM-dd HH:mm:ss`；
- 金额使用 decimal，不使用浮点数。
