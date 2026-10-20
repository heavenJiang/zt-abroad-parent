# abr-ocr：内网文字识别服务（MA-15），RapidOCR + PaddleOCR 开源模型，ONNX Runtime CPU
FROM python:3.12-slim

# 国内服务器可在构建时指定镜像源：docker compose build --build-arg PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple abr-ocr
ARG PIP_INDEX_URL=https://pypi.org/simple

WORKDIR /app
COPY requirements.txt .
# rapidocr 依赖的 opencv-python 需要系统图形库；换成无界面版，镜像里不需要 apt 安装任何包
RUN pip install --no-cache-dir -i "$PIP_INDEX_URL" -r requirements.txt \
    && pip uninstall -y opencv-python \
    && pip install --no-cache-dir -i "$PIP_INDEX_URL" opencv-python-headless==4.14.0.94

# 构建时下载并固定模型（运行时不访问外网）
RUN python -c "from rapidocr import RapidOCR; RapidOCR()"

COPY server.py .
RUN useradd --system --no-create-home ocr
USER ocr

EXPOSE 9003
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=5 \
    CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:9003/health', timeout=3).status == 200 else 1)"
CMD ["python", "server.py"]
