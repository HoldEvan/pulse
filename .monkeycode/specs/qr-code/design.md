# 技术设计：二维码生成与识别（#30）

## 方案

### 依赖

`gradle/libs.versions.toml` 新增 `zxing = "3.5.3"` 与 `zxing-core`，`app/build.gradle.kts` 显式声明 `implementation(libs.zxing.core)`。只依赖 core，编码用 `qrcode.encoder.Encoder`、解码用 `qrcode.QRCodeReader`，不引入 android 客户端库。

### 领域层

新文件 `domain/tools/QrCodec.kt`：

- `QrErrorCorrection{LOW, MEDIUM, QUARTILE, HIGH}`，`level()` 显式映射到 zxing 的 `ErrorCorrectionLevel.L/M/Q/H`。
- `QrMatrix(size, bits)` 为行优先布尔点阵，`isDark(x, y)`、`toPixels(scale, margin, dark, light)` 负责放大并补白边；与 zxing 的 `BitMatrix` 解耦，单测可直接构造。
- `QrCodec.encode(text, correction)` 返回 `QrEncodeResult(matrix, error)`。仅当文本不能用 ISO-8859-1 表示时才设置 `CHARACTER_SET=UTF-8`：zxing 默认按 ISO-8859-1 编码会把中文变成问号，而给纯 ASCII 提前声明 UTF-8 会多写 12 位 ECI 头，把版本顶高一级。
- `QrCodec.decode(pixels, width, height)` 返回 `QrDecodeResult(text, format, error)`。依次尝试 HybridBinarizer、GlobalHistogramBinarizer，再对各自尝试反色版本，并开启 `TRY_HARDER`；全部失败时区分 `notFound` 与 `failed`。
- 错误编码 `empty` / `tooLong` / `invalid` / `notFound`；`MAX_INPUT_BYTES = 2953`。

### 界面

新文件 `ui/tools/QrCodeScreen.kt`，单页两 Tab：

- 生成：多行输入 + 纠错级别下拉 + 预览卡片（白底、上限 260dp）+ 点阵边长 + 保存按钮，编码结果用 `remember(input, correction)` 缓存。
- 识别：复用 `PickVisualMedia` 选图，解码在 `Dispatchers.IO` 完成，界面只展示图片预览、结果文本（`MonospaceBlock`）与复制按钮；按最长边 2000px 做 2 的幂降采样。

新文件 `ui/common/ImageSaving.kt`：把 `saveImageBytes` / `detectImageMime` 从 `ImageBase64Screen` 抽出为共享实现，并新增 `Bitmap.toPngBytes()`，两个工具页复用同一套保存逻辑。

新增路由 `PulseRoute.QrCodeTool`、首页工具箱入口（图标 `SearchDevice`）与三语 27 条文案。

## 测试

- `QrCodecTest` 15 项：点阵形状、空输入、超长、容量边界、ASCII/中文/多行往返、四级别往返、纠错级别影响版本、点阵含明暗、定位图案、白边、空白图报未识别、参数非法。
- `QrCodecScaleTest` 4 项：ASCII 与中文在 2/3/4/6/10 倍放大下均往返成功、长内容多倍数往返、反色仍可识别。
