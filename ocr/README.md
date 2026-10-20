# abr-ocr 内嵌文字识别（MA-15）

- 程序：[RapidOCR](https://github.com/RapidAI/RapidOCR) 3.9.2（Apache-2.0），模型为百度开源的 PaddleOCR 模型（rapidocr 3.9 起默认 PP-OCRv6 small 检测与识别），ONNX Runtime 1.30.0 CPU 推理。
- 选择理由：中文识别准确率在开源方案中领先，纯 CPU 可用；模型在镜像构建时下载，运行时不访问外网；部署在自有服务器内，图片不出服务器，不新增个人信息受托方。
- 接口：`GET /health`；`POST /ocr`（请求体为 PNG / JPEG 字节，最大 10 MB）→ `{"text", "lines": [{"text", "score"}], "elapsedMs"}`；置信度低于 `OCR_MIN_SCORE`（默认 0.5）的行丢弃。服务不落盘、不记录图片与文字。
- 调用方：后端 `com.ygb.abroad.integration.AbrRapidOcrClient`（配置 `abroad.ocr.url`），用于简历 / 成绩单图片与扫描版 PDF（M4-07）、offer 截图核验（MA-11）。
- 自测（2026-10-20）：模拟简历图片（中英混排、分数、日期）与“手机拍照”版本（旋转 2.5°、模糊、压暗、JPEG 60 质量）逐字正确，丢失部分中英文之间的空格；单张 0.5–1.2 秒。
- 升级：改 `requirements.txt` 版本后重建镜像，并用上面的样例重跑自测。
