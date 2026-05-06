# 路面病害检测系统 — 服务端管理平台

> 大创项目：基于安卓APP的路面病害检测系统配套后端

---

## 对话核心摘要（新对话复用）

APP端（Kotlin + CameraX + YOLO双通道，包名 `com.example.pavementdetection`）已完成，本对话完成服务端从零搭建到功能完善。

**技术栈：** Spring Boot 3.5.13 + MySQL 8.0（collectdata_db，表名 detections）+ JPA + Thymeleaf + Leaflet + 原生 Canvas 图表，JDK 25，端口 8080，包名 `com.example.pavementdetection.server`。

**已完成接口：** POST upload / GET list（分页）/ GET stats（类型分布+30天趋势）/ DELETE {id} / GET export（CSV）。

**已完成前端：** 统计面板（原生 Canvas，无 CDN 依赖）+ 地图标记（按类型着色）+ 列表分页筛选 + 详情弹窗 + 删除（二次确认）+ 导出 CSV（带 BOM，Excel 不乱码）。

**关键踩坑：**
- MySQL 8 认证：URL 加 `allowPublicKeyRetrieval=true`
- Android HTTP：Manifest 加 `android:usesCleartextTraffic="true"`
- 局域网访问：`server.address=0.0.0.0`
- Chart.js 被 Edge 跟踪防护拦截 → 改用原生 Canvas API 自绘图表
- 开代理工具会改变电脑 IP → 建议设静态 IP
- Thymeleaf 模板有缓存 → 改完 HTML 必须重启 Spring Boot
- 内联 229KB JS 会导致 `response already committed` → 用静态文件或原生实现

**待完成：** C. 置信度过滤阈值 / D. 系统架构图 / B. 云服务器部署

---

## 项目结构

```
pavement_detection_server/
├── src/main/java/com/example/pavementdetection/server/
│   ├── entity/         Detection.java            # 数据库实体，对应 detections 表
│   ├── repository/     DetectionRepository.java  # JPA Repository + 统计查询
│   ├── service/        DetectionService.java     # 业务逻辑 + 图片存储 + 分页 + 统计
│   ├── controller/     DetectionController.java  # REST API
│   │                   WebController.java        # 页面路由 + 图片访问
│   └── ServerApplication.java
├── src/main/resources/
│   ├── templates/      index.html                # 管理平台前端（无外部 JS 依赖）
│   ├── static/                                   # 静态资源目录
│   └── application.properties
```

---

## 技术栈

| 层次 | 技术 |
|------|------|
| 后端框架 | Spring Boot 3.5.13 + Maven |
| 数据库 | MySQL 8.0（库名：collectdata_db） |
| ORM | Spring Data JPA + Hibernate |
| 前端 | Thymeleaf + Leaflet.js（OpenStreetMap）+ 原生 Canvas |
| 运行环境 | JDK 25，本地端口 8080 |

---

## 环境配置

### application.properties

```properties
server.port=8080
server.address=0.0.0.0

spring.datasource.url=jdbc:mysql://localhost:3306/collectdata_db?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai&characterEncoding=utf8
spring.datasource.username=root
spring.datasource.password=你的密码
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect

spring.servlet.multipart.max-file-size=50MB
spring.servlet.multipart.max-request-size=50MB

upload.path=D:/collectdata/collectdata_uploads/
```

### 初始化数据库

```sql
CREATE DATABASE collectdata_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

---

## API 接口

### 上传检测记录

```
POST /api/detection/upload
Content-Type: multipart/form-data
```

| 参数 | 类型 | 说明 |
|------|------|------|
| latitude | Double | GPS 纬度 |
| longitude | Double | GPS 经度 |
| defectType | String | 病害类型（见下表） |
| confidence | Float | 置信度 0~1 |
| bboxX1/Y1/X2/Y2 | Float | 检测框归一化坐标 |
| channel | String | A=IMU触发，B=实时检测 |
| deviceId | String | 设备唯一 ID |
| image | File | 检测帧图片（可选） |

返回示例：`{"success": true, "id": 6, "message": "上传成功"}`

### 其他接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/detection/list?page=0&size=20` | 分页查询记录 |
| GET | `/api/detection/stats` | 统计（类型分布/30天趋势/总数） |
| DELETE | `/api/detection/{id}` | 删除单条记录 |
| GET | `/api/detection/export?defectType=&channel=` | 导出全量数据（支持筛选） |
| GET | `/images/{filename}` | 访问检测图片 |

---

## 病害类型对照表

| 英文标识 | 中文名称 |
|----------|----------|
| crack | 裂缝 |
| patched_crack | 修补后裂缝 |
| pothole | 坑槽 |
| patched_pothole | 修补后坑槽 |
| alligator_crack | 网裂 / 鳄鱼纹裂缝 |
| patched_alligator_crack | 修补后网裂 |
| manhole | 检查井 / 井盖周边 |
| street_waste | 路面垃圾 / 杂物 |

---

## Web 管理平台功能

访问 `http://localhost:8080`，功能包括：

- **统计面板**：病害类型分布饼图 + 近30天趋势折线图 + 总记录数（原生 Canvas，无 CDN 依赖）
- **地图**：Leaflet + OpenStreetMap，标记按病害类型颜色区分，点击定位
- **列表**：分页加载（每页20条），按病害类型 / 触发通道筛选
- **详情弹窗**：含检测图片、GPS坐标、设备ID、置信度、上传时间
- **删除**：卡片悬停显示 🗑 按钮，二次确认，同步更新地图和统计
- **导出 CSV**：按当前筛选条件全量导出，带 BOM 头（Excel 中文不乱码）

---

## APP 端接入

**APP 名：** 路面病害检测  
**包名：** `com.example.pavementdetection`

### DetectionUploader.kt 服务器地址

```kotlin
private const val SERVER_URL = "http://192.168.5.3:8080/api/detection/upload"
```

> 手机和电脑需在同一 WiFi。电脑 IP 用 `ipconfig` 查 WLAN 的 IPv4，建议设静态 IP 防止变动。

### AndroidManifest.xml 必要配置

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<application
    android:usesCleartextTraffic="true"
    ...>
```

### build.gradle.kts 依赖

```kotlin
implementation("com.squareup.okhttp3:okhttp:4.12.0")
```

### 上传调用位置

- 通道 B：在 `saveChannelBEvent()` 末尾调用，channel="B"
- 通道 A：在 `saveEventData()` 末尾调用，channel="A"

---

## 已知踩坑

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `Public Key Retrieval is not allowed` | MySQL 8 新认证机制 | URL 加 `allowPublicKeyRetrieval=true` |
| `CLEARTEXT communication not permitted` | Android 9+ 禁 HTTP | Manifest 加 `usesCleartextTraffic="true"` |
| 手机无法访问服务器 | 服务器只监听 localhost | `server.address=0.0.0.0` |
| 统计面板图表空白 | Edge 跟踪防护拦截 Chart.js CDN | 改用原生 Canvas API 自绘 |
| `response already committed` | 内联大体积 JS 超 Thymeleaf 缓冲区 | 用静态文件或原生实现 |
| 电脑 IP 变动上传失败 | 代理工具改变网络路由 | 设置静态 IP |
| 改完 HTML 不生效 | Thymeleaf 模板缓存 | 修改后重启 Spring Boot |
| `OkHttp Unresolved reference` | 依赖未同步 | build.gradle.kts 添加后 Sync Now |
| `Table 'collectdata_db.detection' doesn't exist` | 原生 SQL 表名写错 | 改为 `detections`（Hibernate 自动加复数） |

---

## 待完成

- [ ] **C. 数据质量优化**：服务端置信度过滤阈值，低于阈值不入库（street_waste 置信度偏低 39%~66%）
- [ ] **D. 为答辩准备**：系统架构图
- [ ] **B. 云服务器部署**：阿里云/腾讯云，脱离局域网限制