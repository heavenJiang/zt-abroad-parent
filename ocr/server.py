"""
abr-ocr：内网文字识别服务（MA-15）

RapidOCR（Apache-2.0）+ 百度开源 PaddleOCR 模型（rapidocr 3.9 起默认 PP-OCRv6），ONNX Runtime CPU 推理。
只在 Docker 内网提供给后端 abr-admin 调用，不对外暴露；图片只在内存中处理，不落盘、不记录内容。

接口：
  GET  /health   → {"status": "ok"}
  POST /ocr      请求体为图片字节（PNG / JPEG），返回 {"text": "按行拼接的文字", "lines": [{"text", "score"}], "elapsedMs"}
"""
import json
import logging
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from rapidocr import RapidOCR

MAX_BYTES = int(os.environ.get("OCR_MAX_BYTES", str(10 * 1024 * 1024)))
MIN_SCORE = float(os.environ.get("OCR_MIN_SCORE", "0.5"))
PORT = int(os.environ.get("OCR_PORT", "9003"))

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("abr-ocr")

ENGINE = RapidOCR()
# 推理引擎不保证线程安全：串行识别，并发请求排队
LOCK = threading.Lock()


def recognize(image: bytes) -> dict:
    start = time.time()
    with LOCK:
        result = ENGINE(image)
    lines = []
    txts = getattr(result, "txts", None) or ()
    scores = getattr(result, "scores", None) or ()
    for text, score in zip(txts, scores):
        text = (text or "").strip()
        if text and float(score) >= MIN_SCORE:
            lines.append({"text": text, "score": round(float(score), 4)})
    return {
        "text": "\n".join(line["text"] for line in lines),
        "lines": lines,
        "elapsedMs": int((time.time() - start) * 1000),
    }


class Handler(BaseHTTPRequestHandler):
    server_version = "abr-ocr"

    def _send(self, status: int, body: dict) -> None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send(200, {"status": "ok"})
        else:
            self._send(404, {"error": "not_found"})

    def do_POST(self) -> None:
        if self.path != "/ocr":
            self._send(404, {"error": "not_found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            self._send(400, {"error": "empty"})
            return
        if length > MAX_BYTES:
            self._send(413, {"error": "too_large"})
            return
        image = self.rfile.read(length)
        if not (image.startswith(b"\x89PNG") or image.startswith(b"\xff\xd8")):
            self._send(415, {"error": "unsupported"})
            return
        try:
            body = recognize(image)
        except Exception as e:  # noqa: BLE001 识别失败统一返回 500，由后端提示手动填写
            log.warning("识别失败：%s", type(e).__name__)
            self._send(500, {"error": "ocr_failed"})
            return
        log.info("识别 %d 字节，%d 行，%d ms", length, len(body["lines"]), body["elapsedMs"])
        self._send(200, body)

    def log_message(self, fmt, *args) -> None:
        # 不输出请求行以外的内容（不记录图片与文字）
        return


if __name__ == "__main__":
    log.info("abr-ocr 启动，端口 %d", PORT)
    ThreadingHTTPServer(("0.0.0.0", PORT), Handler).serve_forever()
