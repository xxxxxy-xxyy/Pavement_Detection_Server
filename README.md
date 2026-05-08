# 路面病害检测系统 — 服务端管理平台

> 大学生创新创业训练计划项目（大创）配套后端
> Android APP 端（Kotlin + CameraX + YOLOv8 双通道）已独立完成，本文档记录服务端完整实现。

---

## 技术栈

| 层次 | 技术选型 |
|------|----------|
| 后端框架 | Spring Boot 3.5.13 + Maven |
| 运行环境 | JDK 21，端口 8080 |
| 数据库 | MySQL 8.0（库：collectdata_db，表：detections / users） |
| ORM | Spring Data JPA + Hibernate |
| 密码安全 | spring-security-crypto（BCrypt，仅引入加密模块） |
| 登录态 | HttpSession（8小时免登录） |
| 前端模板 | Thymeleaf |
| 地图组件 | Leaflet.js + OpenStreetMap |
| 图表渲染 | 原生 Canvas API（无第三方图表库） |

---

## 项目结构

```
pavement_detection_server/
├── src/main/java/com/example/pavementdetection/server/
│   ├── entity/
│   │   ├── Detection.java           # 检测记录实体
│   │   └── User.java                # 用户实体
│   ├── repository/
│   │   ├── DetectionRepository.java # JPA + 统计查询
│   │   └── UserRepository.java      # 用户查询
│   ├── service/
│   │   ├── DetectionService.java    # 检测业务 + 置信度过滤 + 动态阈值刷新
│   │   └── UserService.java         # 注册 / 登录（BCrypt）
│   ├── controller/
│   │   ├── DetectionController.java # 检测 REST API
│   │   ├── ThresholdController.java # 阈值动态管理 API
│   │   ├── AuthController.java      # 登录 / 注册 / 登出
│   │   └── WebController.java       # 页面路由 + 图片访问
│   ├── interceptor/
│   │   └── LoginInterceptor.java    # 登录拦截（未登录跳 /login）
│   ├── config/
│   │   └── WebConfig.java           # 注册拦截器 + 排除上传接口
│   └── ServerApplication.java
├── src/main/resources/
│   ├── templates/
│   │   ├── index.html               # 管理平台主页
│   │   └── login.html               # 登录 / 注册页（粒子背景动效）
│   └── application.properties
```

---

## 环境配置

### application.properties

```properties
server.port=8080
server.address=0.0.0.0

spring.datasource.url=jdbc:mysql://localhost:3306/collectdata_db?\
  useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=你的密码
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

upload.path=D:/collectdata/collectdata_uploads/

# 置信度过滤阈值（可通过 Web 界面运行时修改，无需重启）
confidence.threshold.crack=0.50
confidence.threshold.patched_crack=0.50
confidence.threshold.pothole=0.50
confidence.threshold.patched_pothole=0.50
confidence.threshold.alligator_crack=0.50
confidence.threshold.patched_alligator_crack=0.50
confidence.threshold.manhole=0.45
confidence.threshold.street_waste=0.35
confidence.threshold.default=0.50
```

### 数据库初始化

```sql
CREATE DATABASE collectdata_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- 已有 detections 表时手动补字段（新建表 JPA 自动创建）
ALTER TABLE detections ADD COLUMN confidence_status VARCHAR(10)  DEFAULT 'normal';
ALTER TABLE detections ADD COLUMN handle_status     VARCHAR(20)  DEFAULT 'pending';
ALTER TABLE detections ADD COLUMN handle_by         VARCHAR(50)  DEFAULT NULL;
ALTER TABLE detections ADD COLUMN handle_time       DATETIME     DEFAULT NULL;
```

---

## REST API 接口

### 检测记录

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/detection/upload` | APP 上传检测记录（含图片） |
| GET | `/api/detection/list?page=0&size=20` | 分页查询 |
| GET | `/api/detection/stats` | 统计（类型分布 / 30天趋势 / 总数） |
| DELETE | `/api/detection/{id}` | 删除单条记录 |
| PATCH | `/api/detection/{id}/status` | 更新处理状态 |
| GET | `/api/detection/export?defectType=&channel=` | 导出 CSV（支持筛选） |
| GET | `/images/{filename}` | 访问检测图片 |

### 阈值管理

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/threshold/list` | 获取当前各类型阈值 |
| POST | `/api/threshold/update` | 运行时更新阈值（立即生效，无需重启） |

### 认证

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/login` | 登录/注册页面 |
| POST | `/login` | 登录表单提交 |
| POST | `/register` | 注册表单提交 |
| GET | `/logout` | 登出，清除 Session |

### upload 接口参数

| 参数 | 类型 | 说明 |
|------|------|------|
| latitude / longitude | Double | GPS 坐标 |
| defectType | String | 病害类型英文标识 |
| confidence | Float | 置信度 0～1 |
| bboxX1/Y1/X2/Y2 | Float | 检测框归一化坐标 |
| channel | String | A=IMU触发，B=实时检测 |
| deviceId | String | 设备唯一 ID |
| image | File | 检测帧图片（可选） |

响应示例：
```json
{ "success": true, "id": 42, "message": "上传成功", "confidenceStatus": "normal" }
```

---

## 数据库字段说明

### detections 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| latitude / longitude | DOUBLE | GPS 坐标 |
| defect_type | VARCHAR | 病害类型 |
| confidence | FLOAT | 置信度 0～1 |
| bbox_x1/y1/x2/y2 | FLOAT | 检测框坐标 |
| channel | VARCHAR | A / B |
| device_id | VARCHAR | 设备标识 |
| image_name | VARCHAR | 图片文件名 |
| upload_time | DATETIME | 上传时间（自动填充） |
| confidence_status | VARCHAR | normal / low |
| handle_status | VARCHAR | pending / processing / resolved / ignored |
| handle_by | VARCHAR | 处理人（登录用户名） |
| handle_time | DATETIME | 处理时间 |

### users 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| username | VARCHAR | 唯一，至少3位 |
| password | VARCHAR | BCrypt 哈希 |
| created_at | DATETIME | 注册时间 |

---

## 病害类型对照表

| 英文标识 | 中文名称 | 默认阈值 |
|----------|----------|----------|
| crack | 裂缝 | 0.50 |
| patched_crack | 修补后裂缝 | 0.50 |
| pothole | 坑槽 | 0.50 |
| patched_pothole | 修补后坑槽 | 0.50 |
| alligator_crack | 网裂 | 0.50 |
| patched_alligator_crack | 修补后网裂 | 0.50 |
| manhole | 检查井 | 0.45 |
| street_waste | 路面垃圾 | 0.35 |

---

## Web 管理平台功能

访问 `http://localhost:8080`（未登录自动跳转登录页）。

### 登录 / 注册页
- 粒子连线动态背景 + 毛玻璃卡片，与主平台深色主题统一
- 支持多用户注册，密码 BCrypt 哈希存储
- Session 8小时有效，顶部显示当前登录用户名 + 退出按钮

### 统计面板
- 病害类型分布**饼图** + 近30天**趋势折线图**（原生 Canvas，兼容 Edge 跟踪防护）
- 顶部实时显示总记录数

### 地图可视化
- Leaflet + OpenStreetMap，标记按病害类型颜色区分
- 点击标记定位卡片，切换页面自动同步

### 记录列表
- 每页20条分页，按病害类型 / 触发通道筛选
- 低于阈值记录置灰 +「低置信度」标签，排列末尾
- 卡片显示处理状态彩色标签（待处理 / 处理中 / 已解决 / 已忽略）
- 悬停显示删除按钮，二次确认后删除并同步地图和统计

### 详情弹窗
- 检测图片、GPS 坐标、置信度、通道、设备ID、上传时间
- **处理状态一键切换**：四个状态按钮，点击即保存，记录处理人和处理时间

### 阈值设置
- 顶部「⚙ 阈值设置」按钮，8种类型独立滑块
- 保存后**运行时立即生效**，无需重启服务

### 数据导出
- 按筛选条件全量导出 CSV，UTF-8 BOM，Excel 中文不乱码

---

## 置信度过滤机制

```
APP 上传
  └→ DetectionService 判断 confidence 与该类型阈值
       ├→ >= 阈值：confidence_status = "normal"
       └→ <  阈值：confidence_status = "low"
  └→ 全部入库（不丢弃数据）

前端列表渲染
  ├→ normal：正常显示，排前面
  └→ low：置灰 60% + 「低置信度」标签，排后面
```

---

## 任务处理闭环

```
检测上传 → handle_status = "pending"（默认）
  └→ 管理员打开详情弹窗
       └→ 点击状态按钮 → PATCH /api/detection/{id}/status
            └→ 记录 handle_by（操作人）+ handle_time
                 └→ 列表卡片状态标签实时更新
```

---

## Android APP 端接入

```kotlin
// DetectionUploader.kt
private const val SERVER_URL = "http://192.168.x.x:8080/api/detection/upload"
```

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<application android:usesCleartextTraffic="true" ...>
```

```kotlin
// build.gradle.kts
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

> `/api/detection/upload` 已在拦截器白名单中排除，APP 上传无需登录态。

---

## 关键踩坑记录

| 问题现象 | 原因 | 解决方案 |
|----------|------|----------|
| `Public Key Retrieval is not allowed` | MySQL 8 新认证 | JDBC URL 加 `allowPublicKeyRetrieval=true` |
| `CLEARTEXT communication not permitted` | Android 9+ 禁 HTTP | Manifest 加 `usesCleartextTraffic="true"` |
| 手机无法访问服务器 | Boot 默认监听 localhost | `server.address=0.0.0.0` |
| 统计图表空白 | Edge 拦截 Chart.js CDN | 改用原生 Canvas 自绘 |
| `response already committed` | 内联大体积 JS 超缓冲区 | 改静态文件或原生实现 |
| IP 变动上传失败 | 代理工具改变路由 | 关代理或设静态 IP |
| 改 HTML 不生效 | Thymeleaf 模板缓存 | 修改后重启 Spring Boot |
| `confidence_status` 列不存在 | `ddl-auto=update` 不对已有表加列 | 手动执行 ALTER TABLE |
| 阈值改了不生效 | `@Value` 仅初始化时注入 | 新增 `refreshThresholds()` 手动重读 Environment |
| APP 上传被拦截返回 302 | 拦截器未排除上传接口 | WebConfig 排除 `/api/detection/upload` |

---

## 开发成果

| 功能模块 | 状态 |
|----------|------|
| 基础 API（上传/列表/统计/删除/导出） | ✅ |
| Web 管理平台（地图/图表/列表/弹窗） | ✅ |
| 置信度按类型分阈值过滤 | ✅ |
| Web 界面动态调整阈值（运行时生效） | ✅ |
| 登录注册系统（BCrypt + Session） | ✅ |
| 任务处理状态管理（检测-管理闭环） | ✅ |
| 系统架构图 | ✅ |
| 云服务器部署 | ⬜ 待完成 |