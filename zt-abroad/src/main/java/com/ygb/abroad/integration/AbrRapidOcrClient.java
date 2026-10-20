package com.ygb.abroad.integration;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 内嵌文字识别（MA-15）：调用同一部署内的 abr-ocr 服务（RapidOCR + PaddleOCR 开源模型，CPU 推理）
 * <p>
 * 服务只在 Docker 内网可达，图片不出服务器，不涉及第三方受托方；配置 abroad.ocr.url 为空时不启用（图片与扫描件提示手动填写）。
 * </p>
 *
 * @author ygb
 */
@Component
@ConditionalOnExpression("'${abroad.ocr.url:}' != ''")
public class AbrRapidOcrClient implements AbrOcrClient
{
    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();

    /** abr-ocr 地址（如 http://abr-ocr:9003） */
    @Value("${abroad.ocr.url:}")
    private String url;

    /** 单张图片识别超时（秒；CPU 识别一页通常 1–3 秒，排队时更长） */
    @Value("${abroad.ocr.timeout-seconds:60}")
    private int timeoutSeconds;

    @Override
    public String recognize(byte[] image, String ext)
    {
        String base = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/ocr")).timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .header("Content-Type", "jpg".equals(ext) || "jpeg".equals(ext) ? "image/jpeg" : "image/png")
                .POST(HttpRequest.BodyPublishers.ofByteArray(image)).build();
        HttpResponse<String> resp;
        try
        {
            resp = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        catch (HttpTimeoutException e)
        {
            throw new AbrFetchException(AbrFetchException.TIMEOUT, "文字识别超时");
        }
        catch (IOException e)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "文字识别服务不可用");
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "文字识别被中断");
        }
        if (resp.statusCode() == 413)
        {
            throw new AbrFetchException(AbrFetchException.TOO_LARGE, "图片过大，无法识别");
        }
        if (resp.statusCode() != 200)
        {
            throw new AbrFetchException(AbrFetchException.OPEN_FAILED, "文字识别失败（" + resp.statusCode() + "）");
        }
        JsonNode body = MAPPER.readTree(resp.body());
        return body.path("text").asString("");
    }
}
