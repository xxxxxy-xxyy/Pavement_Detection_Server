# 路面病害检测系统 — 服务端管理平台

> 大学生创新创业训练计划项目（大创）
> Android APP 端（Kotlin + CameraX + YOLOv8 双通道检测）已独立完成，本文档记录服务端完整实现与设计决策。

---

## 项目简介

本系统为路面病害智能检测项目的服务端与 Web 管理平台，配合 Android 采集端实现**检测—上报—管理—验收**的完整闭环。

**核心能力：**
- APP 实时上传检测结果（图片 + GPS + 置信度 + 病害类型）
- 服务端自动完成置信度过滤、严重程度评级
- Web 管理平台可视化展示：地图标记、热力图、统计图表、记录管理
- 处理状态全流程追踪：待处理 → 处理中 → 已解决（附处理后图片对比验收）

---

## 技术栈

| 层次 | 技术选型 | 说明 |
|------|----------|------|
| 后端框架 | Spring Boot 3.5.13 + Maven | JDK 21，端口 8080 |
| 数据库 | MySQL 8.0 | 库：collectdata_db，表：detections / users |
| ORM | Spring Data JPA + Hibernate | ddl-auto=update |
| 密码安全 | spring-security-crypto（BCrypt） | 仅引入加密模块，未使用完整 Security |
| Web 登录态 | HttpSession | 8 小时免登录 |
| APP 登录态 | JWT（jjwt 0.11.5） | 30 天有效期，Bearer Token |
| 前端模板 | Thymeleaf | 单页多视图路由（纯前端 JS 切换） |
| 地图组件 | Leaflet.js + 高德瓦片 + Leaflet.heat | 标记图 / 热力图双模式 |
| 图表渲染 | Chart.js 4.4（统计页）+ 原生 Canvas（概览页） | 两页差异化方案 |

---

## 项目结构

```
pavement_detection_server/
├── src/main/java/com/example/pavementdetection/server/
│   ├── entity/
│   │   ├── Detection.java          # 检测记录实体
│   │   └── User.java               # 用户实体
│   ├── repository/
│   │   ├── DetectionRepository.java
│   │   └── UserRepository.java
│   ├── service/
│   │   ├── DetectionService.java   # 业务核心：置信度过滤 + 严重程度评级 + 热力图数据
│   │   └── UserService.java
│   ├── controller/
│   │   ├── DetectionController.java  # 检测 REST API（含热力图接口）
│   │   ├── AppAuthController.java    # APP 注册/登录/改密/注销（JWT）
│   │   ├── ThresholdController.java  # 阈值动态管理 API
│   │   ├── AuthController.java       # Web 登录/注册/登出（Session）
│   │   └── WebController.java        # 页面路由 + 图片访问
│   ├── interceptor/
│   │   ├── LoginInterceptor.java     # Web Session 拦截（@Component 必填）
│   │   └── AppTokenInterceptor.java  # APP JWT 拦截
│   ├── config/
│   │   └── WebConfig.java            # 双拦截器注册（作用域严格分离）
│   ├── util/
│   │   └── JwtUtil.java              # JWT 生成/校验
│   └── ServerApplication.java
├── src/main/resources/
│   ├── templates/
│   │   ├── index.html               # 管理平台主页（五页单页应用）
│   │   └── login.html               # 登录/注册页
│   └── application.properties
```

---

## 环境配置

### application.properties

```properties
server.port=8080
server.address=0.0.0.0          # 允许局域网设备访问

spring.datasource.url=jdbc:mysql://localhost:3306/collectdata_db?\
  useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=你的密码
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.thymeleaf.cache=false

spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

upload.path=D:/collectdata/collectdata_uploads/

# 置信度过滤阈值（运行时可通过 Web 界面修改，无需重启）
confidence.threshold.crack=0.50
confidence.threshold.patched_crack=0.50
confidence.threshold.pothole=0.50
confidence.threshold.patched_pothole=0.50
confidence.threshold.alligator_crack=0.50
confidence.threshold.patched_alligator_crack=0.50
confidence.threshold.manhole=0.45
confidence.threshold.street_waste=0.35
confidence.threshold.default=0.50

# JWT
jwt.secret=pavement-detection-secret-key-2026
jwt.expiration=2592000000        # 30天，单位毫秒
```

### 数据库初始化

```sql
CREATE DATABASE collectdata_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

> **注意：** `ddl-auto=update` 不会对已有表自动加列，如果是已有旧表需手动执行：

```sql
ALTER TABLE detections ADD COLUMN confidence_status  VARCHAR(10)  DEFAULT 'normal';
ALTER TABLE detections ADD COLUMN handle_status      VARCHAR(20)  DEFAULT 'pending';
ALTER TABLE detections ADD COLUMN handle_by          VARCHAR(50)  DEFAULT NULL;
ALTER TABLE detections ADD COLUMN handle_time        DATETIME     DEFAULT NULL;
ALTER TABLE detections ADD COLUMN severity_score     FLOAT        DEFAULT NULL;
ALTER TABLE detections ADD COLUMN severity_level     VARCHAR(10)  DEFAULT NULL;
ALTER TABLE detections ADD COLUMN after_image_name   VARCHAR(255) DEFAULT NULL;
```

---

## 数据库字段说明

### detections 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| latitude / longitude | DOUBLE | GPS 坐标 |
| defect_type | VARCHAR | 病害类型英文标识 |
| confidence | FLOAT | 置信度 0～1 |
| bboxx1/y1/x2/y2 | FLOAT | 检测框归一化坐标（JPA驼峰→无下划线） |
| channel | VARCHAR | A（IMU触发）/ B（实时检测） |
| device_id | VARCHAR | 设备唯一标识 |
| image_name | VARCHAR | 检测帧图片文件名 |
| after_image_name | VARCHAR | 处理后图片文件名 |
| upload_time | DATETIME | 上传时间（自动填充） |
| confidence_status | VARCHAR | normal / low |
| handle_status | VARCHAR | pending / processing / resolved / ignored |
| handle_by | VARCHAR | 处理人用户名 |
| handle_time | DATETIME | 处理时间 |
| severity_score | FLOAT | 严重程度评分（0～15+） |
| severity_level | VARCHAR | low / medium / high / critical |

### users 表

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 自增主键 |
| username | VARCHAR | 唯一，至少3位 |
| password | VARCHAR | BCrypt 哈希 |
| created_at | DATETIME | 注册时间 |

---

## REST API 接口

### 检测记录（`/api/detection/**`）

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/detection/upload` | APP Token | 上传检测记录（图片 + 元数据） |
| GET | `/api/detection/list?page=0&size=20` | 无 | 分页查询（按严重程度降序） |
| GET | `/api/detection/stats` | 无 | 统计：类型分布 / 30天趋势 / 严重程度分布 |
| GET | `/api/detection/heatmap` | 无 | 热力图坐标+权重（全量） |
| DELETE | `/api/detection/{id}` | 无 | 删除单条记录 |
| PATCH | `/api/detection/{id}/status` | 无 | 更新处理状态 |
| POST | `/api/detection/{id}/resolve-with-image` | 无 | 上传处理后图片并标记已解决 |
| GET | `/api/detection/export` | 无 | 导出 CSV（支持类型/通道筛选） |
| GET | `/images/{filename}` | 无 | 访问上传图片文件 |

**upload 接口参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| latitude / longitude | Double | GPS 坐标 |
| defectType | String | 病害类型英文标识 |
| confidence | Float | 置信度 0～1 |
| bboxX1/Y1/X2/Y2 | Float | 检测框归一化坐标 |
| channel | String | A=IMU触发，B=实时检测 |
| deviceId | String | 设备唯一 ID |
| image | File | 检测帧图片（可选） |

**upload 响应示例：**
```json
{
  "success": true,
  "id": 42,
  "message": "上传成功",
  "confidenceStatus": "normal",
  "severityLevel": "high",
  "severityScore": 6.38
}
```

### APP 用户认证（`/api/auth/**`）

| 方法 | 路径 | 需 Token | 说明 |
|------|------|----------|------|
| POST | `/api/auth/register` | 否 | 注册，返回 JWT |
| POST | `/api/auth/login` | 否 | 登录，返回 JWT |
| POST | `/api/auth/changePassword` | 是 | 修改密码（验证旧密码） |
| DELETE | `/api/auth/account` | 是 | 注销账号（保留检测数据） |

### 阈值管理（`/api/threshold/**`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/threshold/list` | 获取当前各类型阈值 |
| POST | `/api/threshold/update` | 运行时更新，立即生效，无需重启 |

### Web 认证（Session）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET/POST | `/login` | 登录页 / 登录表单提交 |
| POST | `/register` | 注册表单提交 |
| GET | `/logout` | 登出，清除 Session |

---

## 核心业务逻辑

### 严重程度评级机制

```
severityScore = 类型基础分 × confidence × bbox面积系数

bbox面积系数（归一化面积 = (x2-x1)×(y2-y1)）：
  面积 > 0.10  → × 1.5
  面积 > 0.04  → × 1.2
  其他         → × 1.0

分级规则：
  score ≥ 7.0  → critical（紧急）
  score ≥ 4.5  → high    （高危）
  score ≥ 2.5  → medium  （中危）
  其他         → low     （低危）
```

### 病害类型参数表

| 英文标识 | 中文名称 | 默认阈值 | 严重程度基础分 |
|----------|----------|----------|----------------|
| crack | 裂缝 | 0.50 | 7 |
| patched_crack | 修补后裂缝 | 0.50 | 4 |
| pothole | 坑槽 | 0.50 | 10 |
| patched_pothole | 修补后坑槽 | 0.50 | 7 |
| alligator_crack | 网裂 | 0.50 | 10 |
| patched_alligator_crack | 修补后网裂 | 0.50 | 4 |
| manhole | 检查井 | 0.45 | 6 |
| street_waste | 路面垃圾 | 0.35 | 3 |

### 置信度过滤机制

所有记录**全部入库，不丢弃**。上传时与该类型阈值对比：
- `confidence ≥ 阈值` → `confidence_status = "normal"`，正常显示
- `confidence < 阈值` → `confidence_status = "low"`，前端置灰 + 标签提示，排列末尾

阈值可通过 Web 界面滑块实时调整，调用 `/api/threshold/update` 后无需重启即时生效（`@Value` 注入在初始化时完成，运行时通过手动重读 Environment 实现刷新）。

### JWT 鉴权架构

```
双拦截器严格分离作用域：

LoginInterceptor    → 拦截 /**
                      排除 /api/**（全部）、/login、/register、/images/**
                      → 只管 Web 页面的 Session 校验

AppTokenInterceptor → 精确拦截：
                        /api/detection/upload
                        /api/auth/changePassword
                        /api/auth/account
                      排除：/api/auth/register、/api/auth/login
                      → 只管 APP 的 Token 校验
```

> **关键：** list、stats、heatmap、export 等 Web 页面也会调用的接口**不在** AppTokenInterceptor 拦截范围内，否则 Web 管理台无法加载数据。

### 处理前后图片对比机制

```
点击「已解决」
  └→ 若无处理后图片 → 显示上传区域
       └→ 选择图片 → 本地预览（dataset.loaded = 'preview'）
            └→ 点「确认已解决」→ POST /resolve-with-image
                 └→ 图片保存为 after_{uuid}.jpg
                      └→ 详情弹窗左右对比展示

切换到其他状态（pending / processing / ignored）
  └→ 后端自动删除磁盘文件，清空 after_image_name
       └→ 前端同步隐藏处理后图片

切换记录时强制重置上传区：
  type=text → type=file 清空 file input（value='' 在部分浏览器无效）
```

---

## Web 管理平台

访问 `http://localhost:8080`，未登录自动跳转登录页。

### 整体架构

单页应用（SPA）设计，左侧固定导航栏，纯 JS 前端路由切换五个页面，无需刷新。Thymeleaf 仅用于注入登录用户名和 Session 校验重定向。

### 页面功能一览

**① 概览 Dashboard**（运营态势）
- 四个大数字指标卡：总记录数 / 紧急数 / 待处理 / 已解决
- 任务处理进度环形图（Canvas 手绘）：四种状态分段显示解决率
- 通道来源分布：A（IMU触发）/ B（实时检测）横向进度条
- 严重程度快览：四级彩色条形图
- 紧急/高危告警 Feed：可点击直接打开详情
- 近30天检测趋势折线图（Canvas，渐变填充）

**② 地图 Map**
- 高德卫星+路网瓦片（国内访问稳定，中文标注清晰）
- 标记图：按病害类型彩色圆点，点击展开右侧抽屉详情
- 热力图：按 severityScore 加权，Leaflet.heat 渲染，懒加载
- 顶部筛选条：按类型/通道过滤标记点

**③ 记录列表 Records**
- 全宽表格，每页 20 条分页
- 多维筛选：类型 / 通道 / 状态
- 自定义排序：严重程度 / 时间（新→旧/旧→新）/ 置信度（高→低/低→高）
- 时间区间筛选：起止日期选择器 + 一键清除
- 低置信度记录置灰排末尾
- 行内操作：详情弹窗 / 删除（二次确认）
- 底部导出 CSV（UTF-8 BOM，Excel 中文不乱码）

**④ 统计分析 Analytics**（数据分布）
- 顶部比例指标：紧急占比 / 高危占比 / 解决率（与概览页数字维度不同）
- 病害类型甜甜圈图（Chart.js，含图例和百分比悬浮提示）
- 严重程度横向条形图（Chart.js，四色）
- 近30天趋势折线图（Chart.js，平滑曲线+渐变填充）

**⑤ 系统设置 Settings**
- 8 种病害类型置信度阈值独立滑块，实时预览数值
- 保存后立即生效，无需重启服务
- 系统信息展示

### 详情弹窗（全局）

点击任意记录（列表行 / 地图告警 / 告警Feed）均可触发：
- 检测图片与处理后图片左右对比
- 完整字段：类型 / 置信度 / 通道 / GPS / 设备ID / 时间
- 处理状态四按钮切换（标记已解决时必须上传处理后图片）
- 记录处理人 + 处理时间

---

## Android APP 端接入

```kotlin
private const val BASE_URL   = "http://192.168.x.x:8080"
private const val UPLOAD_URL = "$BASE_URL/api/detection/upload"
private const val LOGIN_URL  = "$BASE_URL/api/auth/login"
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

所有检测接口请求头携带：
```
Authorization: Bearer <JWT Token>
```

---

## 关键踩坑记录

| 问题现象 | 根本原因 | 解决方案 |
|----------|----------|----------|
| `Public Key Retrieval is not allowed` | MySQL 8 新认证协议 | JDBC URL 加 `allowPublicKeyRetrieval=true` |
| `CLEARTEXT communication not permitted` | Android 9+ 禁 HTTP | Manifest 加 `usesCleartextTraffic="true"` |
| 手机无法访问服务器 | Spring Boot 默认监听 localhost | `server.address=0.0.0.0` |
| Web 管理台数据全空（401） | AppTokenInterceptor 拦截了 list/stats 等 Web 接口 | Token 拦截器只精确拦截 upload/changePassword/account |
| 统计图表空白（CDN 访问失败） | 企业/校园网拦截境外 CDN | 将 Chart.js 下载至 `static/js/` 本地引入 |
| `confidence_status` 列不存在 | ddl-auto=update 不对已有表加列 | 手动执行 ALTER TABLE |
| 阈值修改后不生效 | `@Value` 仅在初始化时注入 | refreshThresholds() 手动重读 Environment |
| bbox 列名不含下划线 | JPA 驼峰转换：bboxX1 → bboxx1 | SQL 直接用 bboxx1 而非 bbox_x1 |
| 处理后图片切换记录残留 | `file input` 无法用 `value=''` 清空 | type=text → type=file 强制清空 |
| 热力图接口 405 | 控制器类上已有路径前缀，方法上路径重复 | 方法注解改为 `@GetMapping("/heatmap")` |
| 地图切换后黑屏 | Leaflet 在 display:none 容器内初始化导致尺寸失效 | 切换到地图页后调用 `map.invalidateSize()` |
| `LoginInterceptor` 启动报错 | 缺少 `@Component` 注解，无法被 @Autowired 注入 | 类上加 `@Component` |
| `findByUsername` 永远不为 null | 返回类型是 Optional，直接 != null 永远 true | 改用 `.orElse(null)` 或 `.isPresent()` |
| 切页后 switchPage 报错 | header 中 btn-export 元素被删除但 JS 仍在访问 | 统一用 `getElementById` 前判空，或只保留一处按钮 |

---

## 开发成果

| 功能模块 | 状态 |
|----------|------|
| 基础 REST API（上传/列表/统计/删除/导出） | ✅ 完成 |
| Web 管理平台五页 SPA（概览/地图/记录/统计/设置） | ✅ 完成 |
| 置信度按类型分阈值过滤（全量入库，低置信度标记） | ✅ 完成 |
| Web 界面动态调整阈值（运行时生效，无需重启） | ✅ 完成 |
| Web 登录注册系统（BCrypt + Session 8小时） | ✅ 完成 |
| APP JWT 登录注册（注册/登录/改密/注销） | ✅ 完成 |
| upload 接口 JWT Token 鉴权 | ✅ 完成 |
| 严重程度自动评级（多维度评分+分级+排序） | ✅ 完成 |
| 任务处理状态管理（待处理→处理中→已解决→已忽略） | ✅ 完成 |
| 处理前后图片对比验收（上传验收闭环） | ✅ 完成 |
| 地图可视化（高德瓦片 + 标记图 + 热力图切换） | ✅ 完成 |
| 记录列表多维筛选 + 自定义排序 + 时间区间筛选 | ✅ 完成 |
| 统计分析（Chart.js 甜甜圈 + 横向柱 + 折线） | ✅ 完成 |
| 数据导出 CSV（UTF-8 BOM，Excel 中文兼容） | ✅ 完成 |
| 区域聚合去重 | ⬜ 待完成 |
| 云服务器部署 | ⬜ 待完成 |