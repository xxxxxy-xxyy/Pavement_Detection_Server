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
│   │   ├── DetectionService.java    # 检测业务 + 置信度过滤 + 严重程度评级 + 动态阈值刷新
│   │   └── UserService.java         # 注册 / 登录（BCrypt）
│   ├── controller/
│   │   ├── DetectionController.java # 检测 REST API（含图片对比验证接口）
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
spring.thymeleaf.cache=false

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
ALTER TABLE detections ADD COLUMN confidence_status  VARCHAR(10)  DEFAULT 'normal';
ALTER TABLE detections ADD COLUMN handle_status      VARCHAR(20)  DEFAULT 'pending';
ALTER TABLE detections ADD COLUMN handle_by          VARCHAR(50)  DEFAULT NULL;
ALTER TABLE detections ADD COLUMN handle_time        DATETIME     DEFAULT NULL;
ALTER TABLE detections ADD COLUMN severity_score     FLOAT        DEFAULT NULL;
ALTER TABLE detections ADD COLUMN severity_level     VARCHAR(10)  DEFAULT NULL;
ALTER TABLE detections ADD COLUMN after_image_name   VARCHAR(255) DEFAULT NULL;
```

---

## REST API 接口

### 检测记录

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/detection/upload` | APP 上传检测记录（含图片） |
| GET | `/api/detection/list?page=0&size=20` | 分页查询（按严重程度降序） |
| GET | `/api/detection/stats` | 统计（类型分布 / 30天趋势 / 总数 / 严重程度分布） |
| DELETE | `/api/detection/{id}` | 删除单条记录 |
| PATCH | `/api/detection/{id}/status` | 更新处理状态（pending/processing/ignored，切回时自动清除处理后图片） |
| POST | `/api/detection/{id}/resolve-with-image` | 上传处理后图片并标记已解决 |
| GET | `/api/detection/export?defectType=&channel=` | 导出 CSV（支持筛选） |
| GET | `/images/{filename}` | 访问检测图片（含处理后图片） |

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

upload 响应示例：
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

resolve-with-image 响应示例：
```json
{
  "success": true,
  "handleStatus": "resolved",
  "handleBy": "admin",
  "handleTime": "2026-05-09T10:23:38",
  "afterImageName": "after_xxxx-xxxx.jpg"
}
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
| bboxx1/y1/x2/y2 | FLOAT | 检测框坐标（JPA驼峰转换，注意列名无下划线） |
| channel | VARCHAR | A / B |
| device_id | VARCHAR | 设备标识 |
| image_name | VARCHAR | 检测帧图片文件名 |
| after_image_name | VARCHAR | 处理后图片文件名 |
| upload_time | DATETIME | 上传时间（自动填充） |
| confidence_status | VARCHAR | normal / low |
| handle_status | VARCHAR | pending / processing / resolved / ignored |
| handle_by | VARCHAR | 处理人（登录用户名） |
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

## 病害类型对照表

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

---

## 严重程度评级机制

```
severityScore = 类型基础分 × confidence × bbox面积系数

bbox面积系数（归一化面积 = (x2-x1)×(y2-y1)）：
  面积 > 0.10  → × 1.5
  面积 > 0.04  → × 1.2
  其他         → × 1.0

分级规则：
  score >= 7.0  → critical（紧急，红色标签）
  score >= 4.5  → high    （高危，橙色标签）
  score >= 2.5  → medium  （中危，黄色标签）
  其他          → low     （低危，绿色标签）

列表排序：按 severity_score DESC，同分按 id DESC
旧数据（score=NULL）自动排末尾
```

---

## 处理前后图片对比机制

```
点击「已解决」
  └→ 右侧出现「点击上传处理后照片」虚线区域
       └→ 选择图片 → 本地预览（dataset.loaded='preview'）
            └→ 点「确认已解决」→ POST /resolve-with-image
                 └→ 图片保存为 after_{uuid}.jpg
                      └→ 详情弹窗左右对比展示

切换到其他状态（pending/processing/ignored）
  └→ 自动删除磁盘文件
       └→ after_image_name 清空
            └→ 前端同步隐藏处理后图片

切换记录时重置：
  - afterImageInput 强制清空（type=text→file trick）
  - btnResolve 隐藏并重置 disabled/textContent
  - dataset.loaded = 'false'
```

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

## Web 管理平台功能

访问 `http://localhost:8080`（未登录自动跳转登录页）。

### 登录 / 注册页
- 粒子连线动态背景 + 毛玻璃卡片
- 多用户注册，密码 BCrypt 哈希存储
- Session 8小时有效，顶部显示登录用户名 + 退出按钮

### 统计面板（四个区块）
- **总记录数**：实时显示
- **病害类型分布**：原生 Canvas 饼图
- **严重程度分布**：四级横向条形图（紧急/高危/中危/低危）
- **近30天检测趋势**：原生 Canvas 折线图

### 地图可视化
- Leaflet + OpenStreetMap，标记按病害类型颜色区分
- 点击标记定位卡片，切换页面自动同步地图视角

### 记录列表
- 每页20条分页，按**严重程度降序**排列
- 按病害类型 / 触发通道筛选
- 卡片标签：通道 + **严重程度**（彩色）+ 低置信度 + 处理状态
- 低于阈值记录置灰排末尾
- 悬停显示删除按钮，二次确认删除

### 详情弹窗
- **处理前后图片左右对比**（处理后区域支持点击上传）
- GPS 坐标、置信度、通道、设备ID、上传时间
- 处理状态四按钮切换：
    - 待处理 / 处理中 / 已忽略：直接切换，从已解决切回时自动清除处理后图片
    - **已解决：必须上传处理后图片才能标记**，形成验收闭环
- 记录处理人 + 处理时间

### 阈值设置
- 8种类型独立滑块，保存后运行时立即生效，无需重启

### 数据导出
- 按筛选条件全量导出 CSV，UTF-8 BOM，Excel 中文不乱码

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
| 改 HTML 不生效 | Thymeleaf 模板缓存 | `spring.thymeleaf.cache=false` + 重启 |
| `confidence_status` 列不存在 | `ddl-auto=update` 不对已有表加列 | 手动执行 ALTER TABLE |
| 阈值改了不生效 | `@Value` 仅初始化时注入 | `refreshThresholds()` 手动重读 Environment |
| APP 上传被拦截返回 302 | 拦截器未排除上传接口 | WebConfig 排除 `/api/detection/upload` |
| bbox 列名不含下划线 | JPA 驼峰转换规则（bboxX1→bboxx1） | SQL 直接用 `bboxx1` 而非 `bbox_x1` |
| 处理后图片切换记录残留 | file input 无法用 `value=''` 清空 | `type=text` 再改回 `type=file` 强制清空 |
| 「已解决」按钮卡住上传中 | `btn.disabled/textContent` 未在两处重置 | `fillAndShow` 和成功回调均重置按钮状态 |
| `src.endsWith('/images/')` 判断失效 | 浏览器将相对路径补全为完整 URL | 改用 `dataset.loaded='true/false/preview'` 标记 |
| 切换记录后状态高亮错误 | 卡片 `data-*` 缺少 handle 相关属性 | 模板加 `data-handle-status/by/time`，selectCard 完整传递 |

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
| **严重程度自动评级**（多维度评分+分级+排序） | ✅ |
| **处理前后图片对比验证**（上传验收闭环） | ✅ |
| **严重程度统计面板**（条形图） | ✅ |
| 热力图 | ⬜ 待完成 |
| 区域聚合去重 | ⬜ 待完成 |
| 云服务器部署 | ⬜ 待完成 |