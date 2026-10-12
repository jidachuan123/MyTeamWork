package com.demo.provider.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;

/**
 * 企业微信「群机器人」推送（Webhook）。
 *
 * 之所以选群机器人而不是自建应用：
 *   - 群机器人用 Webhook URL 即凭证，无需 AppKey/AppSecret、无需 access_token、无需配置服务器出口 IP 白名单
 *   - 群机器人原生支持 image 消息（base64 + md5），可以把报表截图原样发到群里
 *     （对比：钉钉自定义机器人 Webhook 官方不支持图片消息，要发图必须走企业内部应用 + 上传媒体文件）
 *
 * 官方文档约束：
 *   - image 消息：仅支持 JPG/PNG，图片 ≤ 2MB，需同时传 base64 与原始文件 md5
 *   - markdown 消息：内容 ≤ 4096 字节
 *   - 频率限制：每个机器人每分钟最多 20 条
 *
 * 用法：在群里添加「群机器人」→ 复制 Webhook 地址 → 配到 report.daily.main.wecom-webhook
 */
@Service
public class WeComBotService {

    private static final Logger log = LoggerFactory.getLogger(WeComBotService.class);

    /** 企微 image 消息体积上限（2MB），超过要压缩后再发，否则返回 45009 */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 推送一组截图：先发一条 markdown 摘要（标题 + 报表范围 + 日期口径），再逐张发图片。
     *
     * @param webhook 群机器人 Webhook 地址；为空则直接跳过
     * @param digest  摘要文本（markdown），为空则只发图片
     * @param pngPaths 截图文件绝对路径列表
     * @return 可读的执行结果
     */
    public String sendReport(String webhook, String digest, List<String> pngPaths) {
        if (webhook == null || webhook.trim().isEmpty()) {
            return "未配置企业微信机器人 Webhook，跳过推送。";
        }
        String url = webhook.trim();
        StringBuilder sb = new StringBuilder();

        // ① 摘要（可选）
        if (digest != null && !digest.trim().isEmpty()) {
            sb.append("摘要 -> ").append(sendMarkdown(url, digest.trim())).append("\n");
            sleepQuietly(400);   // 简单退让，避免触发 20 条/分钟限流
        }

        // ② 图片
        if (pngPaths == null || pngPaths.isEmpty()) {
            sb.append("无截图可推送。");
            return sb.toString();
        }
        int ok = 0;
        for (String p : pngPaths) {
            File f = new File(p);
            if (!f.exists()) {
                sb.append("图片不存在：").append(p).append("\n");
                continue;
            }
            String r = sendImage(url, f);
            if (r.startsWith("ok")) {
                ok++;
            }
            sb.append(f.getName()).append(" -> ").append(r).append("\n");
            sleepQuietly(400);
        }
        sb.insert(0, "企业微信推送完成：" + ok + "/" + pngPaths.size() + " 张成功\n");
        return sb.toString();
    }

    /** 发送 markdown 文本消息 */
    public String sendMarkdown(String webhook, String content) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "markdown");
            root.putObject("markdown").put("content", content);
            return call(webhook, mapper.writeValueAsString(root));
        } catch (Exception e) {
            return "发送异常：" + e.getMessage();
        }
    }

    /** 发送图片消息（PNG/JPG，≤2MB） */
    public String sendImage(String webhook, File png) {
        try {
            byte[] bytes = Files.readAllBytes(png.toPath());
            if (bytes.length > MAX_IMAGE_BYTES) {
                return "发送失败：图片 " + (bytes.length / 1024) + "KB 超过企微 2MB 限制，需先压缩";
            }
            String b64 = Base64.getEncoder().encodeToString(bytes);
            String md5 = md5(bytes);

            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "image");
            ObjectNode img = root.putObject("image");
            img.put("base64", b64);
            img.put("md5", md5);
            return call(webhook, mapper.writeValueAsString(root));
        } catch (Exception e) {
            return "发送异常：" + e.getMessage();
        }
    }

    /** 纯文本消息（备用，比如只推一句结论） */
    public String sendText(String webhook, String content) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "text");
            root.putObject("text").put("content", content);
            return call(webhook, mapper.writeValueAsString(root));
        } catch (Exception e) {
            return "发送异常：" + e.getMessage();
        }
    }

    /** 统一发 POST 并解析 errcode；errcode=0 视为成功 */
    private String call(String webhook, String json) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(webhook).openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }
            int code = conn.getResponseCode();
            String body;
            try (InputStream is = (code >= 400) ? conn.getErrorStream() : conn.getInputStream();
                 ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
                if (is != null) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        bos.write(buf, 0, n);
                    }
                }
                body = bos.toString(StandardCharsets.UTF_8.name());
            }
            log.debug("[企微机器人] 请求返回 HTTP {} {}", code, body);
            // 正常返回形如 {"errcode":0,"errmsg":"ok"}
            int idx = body.indexOf("\"errcode\"");
            if (idx < 0) {
                return "ok（HTTP " + code + "，无 errcode：" + trim(body, 120) + "）";
            }
            String tail = body.substring(idx + 9).trim();
            StringBuilder num = new StringBuilder();
            for (char c : tail.toCharArray()) {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    num.append(c);
                } else if (num.length() > 0) {
                    break;
                }
            }
            int errcode = num.length() == 0 ? -1 : Integer.parseInt(num.toString());
            return (errcode == 0) ? "ok" : ("失败 errcode=" + errcode + " " + trim(body, 160));
        } finally {
            conn.disconnect();
        }
    }

    private static String md5(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] d = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : d) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replaceAll("\\s+", " ");
        return one.length() <= max ? one : one.substring(0, max) + "...";
    }
}
