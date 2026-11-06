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
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
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
 *   - text  消息：内容 ≤ 2048 字节
 *   - markdown 消息：内容 ≤ 4096 字节
 *   - 频率限制：每个机器人每分钟最多 20 条
 *
 * ⚠ 消息类型与「微信」客户端兼容性（2026-10-19 线上踩坑）：
 *   群机器人发的 markdown 消息，在「企业微信」里显示正常，但群聊被转发/在「微信」里查看时
 *   会被拦成一条灰条「暂不支持此消息类型，点击前往企业微信查看」。
 *   text 与 image 在微信里都能正常展示 —— 所以摘要统一走 text（markdown 语法必须先清洗成纯文本）。
 *
 * 用法：在群里添加「群机器人」→ 复制 Webhook 地址 → 配到 report.daily.main.wecom-webhook
 */
@Service
public class WeComBotService {

    private static final Logger log = LoggerFactory.getLogger(WeComBotService.class);

    /** 企微 image 消息体积上限（2MB），超过要压缩后再发，否则返回 45009 */
    private static final int MAX_IMAGE_BYTES = 2 * 1024 * 1024;

    /** 企微 text 消息内容上限（2048 字节，UTF-8 下约 680 个汉字） */
    private static final int MAX_TEXT_BYTES = 2048;

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 推送一组截图：先发一条 text 摘要（标题 + 报表范围 + 日期口径），再逐张发图片。
     *
     * @param webhook 群机器人 Webhook 地址；为空则直接跳过
     * @param digest  摘要文本（可含 markdown 语法，发送前会清洗成纯文本），为空则只发图片
     * @param pngPaths 截图文件绝对路径列表
     * @return 可读的执行结果
     */
    public String sendReport(String webhook, String digest, List<String> pngPaths) {
        if (webhook == null || webhook.trim().isEmpty()) {
            return "未配置企业微信机器人 Webhook，跳过推送。";
        }
        String url = webhook.trim();
        StringBuilder sb = new StringBuilder();

        // ⓿ 推送前先等域名可解析：家用宽带从待机唤醒后，头几分钟 DNS 经常抽风；
        //    此时直接发 → 一次 UnknownHost 就把整组截图判死。等待窗口内恢复即可正常发。
        waitForDns(url, 90);

        // ① 摘要（可选）：必须是 text，markdown 在微信客户端打不开
        if (digest != null && !digest.trim().isEmpty()) {
            sb.append("摘要 -> ").append(sendText(url, digest.trim())).append("\n");
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
        if (ok < pngPaths.size()) {
            log.warn("[企微机器人] 推送未全部成功：{}/{} 张，逐条结果见返回文案", ok, pngPaths.size());
        }
        return sb.toString();
    }

    /**
     * 发送 markdown 文本消息。
     * ⚠ 仅企业微信客户端可见：在「微信」里查看会显示"暂不支持此消息类型"。群推送摘要请用 sendText。
     */
    public String sendMarkdown(String webhook, String content) {
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "markdown");
            root.putObject("markdown").put("content", content);
            return call(webhook, mapper.writeValueAsString(root));
        } catch (Exception e) {
            return fail("markdown", e);
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
            return fail("image(" + png.getName() + ")", e);
        }
    }

    /**
     * 发送纯文本消息。
     * 之所以用 text 而不是 markdown：text / image 在「微信」客户端都能正常展示，
     * markdown 只认企业微信客户端，转出去会变成"暂不支持此消息类型"。
     * 传入内容里的 markdown 语法会先清洗成纯文本（否则屏幕上会看到 ** 和 > 这些符号）。
     */
    public String sendText(String webhook, String content) {
        try {
            String plain = toPlainText(content);
            if (plain.isEmpty()) {
                return "未发送：文本内容为空";
            }
            if (plain.getBytes(StandardCharsets.UTF_8).length > MAX_TEXT_BYTES) {
                log.warn("[企微机器人] 文本超过 {} 字节，已截断", MAX_TEXT_BYTES);
                plain = truncateUtf8(plain, MAX_TEXT_BYTES - 3) + "...";
            }
            ObjectNode root = mapper.createObjectNode();
            root.put("msgtype", "text");
            root.putObject("text").put("content", plain);
            return call(webhook, mapper.writeValueAsString(root));
        } catch (Exception e) {
            return fail("text", e);
        }
    }

    /**
     * markdown → 纯文本：去掉加粗/斜体/行内代码、引用符号、标题井号、HTML 标签，链接只留文字。
     * 目的：text 消息不做渲染，不清洗就会原样显示 **标题**、&gt; 本期：xxx。
     * 改为 text 后，历史配置里写的 markdown 文案仍可直接用（自动降级为纯文本）。
     */
    static String toPlainText(String s) {
        if (s == null) {
            return "";
        }
        String t = s;
        t = t.replaceAll("(?m)^\\s*>+\\s?", "· ");                 // > 引用  → · 
        t = t.replaceAll("(?m)^\\s*#{1,6}\\s+", "");               // # 标题（要求 # 后有空格，避免误伤 "#1 店"）
        t = t.replaceAll("(?m)^\\s*(?:-{3,}|\\*{3,}|_{3,})\\s*$", ""); // 分隔线直接删掉
        t = t.replaceAll("<[^>]{1,80}>", "");                      // <font color="info"> 等标签
        t = t.replaceAll("!?\\[([^\\]]*)]\\(([^)]*)\\)", "$1 $2"); // [文字](url) → 文字 url
        t = t.replace("**", "").replace("~~", "").replace("`", "");
        t = t.replaceAll("(?<!\\w)__(.+?)__(?!\\w)", "$1");        // __斜体__（避免误伤 a__b 这类标识）
        return t.trim();
    }

    /** 按 UTF-8 字节数安全截断，不切半个汉字 */
    private static String truncateUtf8(String s, int maxBytes) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        if (b.length <= maxBytes) {
            return s;
        }
        int end = Math.max(0, maxBytes);
        while (end > 0 && (b[end] & 0xC0) == 0x80) {   // 0x80~0xBF 是后续字节，回退到字符首字节
            end--;
        }
        return new String(b, 0, end, StandardCharsets.UTF_8);
    }

    /** 网络类异常的重试次数：DNS 抖动/连接重置时，一次失败不该让整组截图全丢 */
    private static final int CALL_RETRIES = 3;

    /**
     * 带重试的发送入口：只对「网络层」异常重试（域名解析失败、连不上、读超时）；
     * HTTP 200 + errcode≠0 属于业务失败（key 失效、图片超限等），重试没有意义，直接返回。
     */
    private String call(String webhook, String json) throws Exception {
        Exception last = null;
        for (int attempt = 1; attempt <= CALL_RETRIES; attempt++) {
            try {
                return callOnce(webhook, json);
            } catch (java.net.UnknownHostException | java.net.ConnectException
                     | java.net.SocketTimeoutException e) {
                last = e;
                log.warn("[企微机器人] 第 {}/{} 次发送失败（{} : {}）",
                        attempt, CALL_RETRIES, e.getClass().getSimpleName(), e.getMessage());
                if (attempt < CALL_RETRIES) {
                    // ★ 必须 > JDK 的 DNS 负缓存 10 秒（networkaddress.cache.negative.ttl），
                    //   否则重试会直接命中缓存、瞬间失败，等于没重试（2026-11-06 踩过）
                    sleepQuietly(12_000L);
                }
            }
        }
        if (last != null) {
            throw last;
        }
        throw new IllegalStateException("企业微信推送失败：重试 " + CALL_RETRIES + " 次仍无结果");
    }

    /** 单次发送：POST JSON 并解析 errcode；errcode=0 视为成功 */
    private String callOnce(String webhook, String json) throws Exception {
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

    /**
     * 推送前确认域名可解析，最多等 maxWaitSeconds 秒（每 15 秒探一次）。
     *
     * 为什么需要：本机 24h 不关机、夜里在 Modern Standby，早上唤醒后 USB 无线网卡 + DNS
     * 需要一段时间才稳定；定时任务恰好落在这个窗口里时，一次 UnknownHost 就会让整组截图全灭。
     * 探测成功时正常情况是 0 开销（第一次就返回）。
     *
     * 间隔必须 > JDK 负缓存 10 秒：解析失败的结果会被 InetAddress 缓存 10 秒，
     * 间隔小于 10 秒的重试全部命中缓存，看着"重试了"其实一次 DNS 查询都没发出去。
     */
    private boolean waitForDns(String webhook, int maxWaitSeconds) {
        String host;
        try {
            host = new URL(webhook).getHost();
        } catch (Exception e) {
            return true;    // URL 本身有问题，交给 call() 报明确错误
        }
        long deadline = System.currentTimeMillis() + maxWaitSeconds * 1000L;
        int attempt = 0;
        while (true) {
            attempt++;
            try {
                InetAddress.getByName(host);
                if (attempt > 1) {
                    log.info("[企微机器人] DNS 已就绪（第 {} 次探测，等了约 {} 秒）", attempt, (attempt - 1) * 15);
                }
                return true;
            } catch (UnknownHostException e) {
                if (System.currentTimeMillis() >= deadline) {
                    log.warn("[企微机器人] DNS 等待 {} 秒仍未就绪：{}，仍尝试发送", maxWaitSeconds, host);
                    return false;
                }
                log.warn("[企微机器人] DNS 未就绪（第 {} 次）：{}，15 秒后重试", attempt, host);
                sleepQuietly(15_000L);
            }
        }
    }

    /**
     * 统一失败文案：必须带异常类型（UnknownHostException / SocketTimeoutException…），
     * 否则只剩一个域名，分不清是 DNS、超时还是 HTTP 业务错误。同时落一条带堆栈的 WARN。
     */
    private static String fail(String scene, Exception e) {
        String msg = (e.getMessage() == null) ? "" : e.getMessage().trim();
        String type = e.getClass().getSimpleName();
        String hint = "";
        if (e instanceof java.net.UnknownHostException) {
            hint = "（域名解析失败：本机 DNS 不可用或网络刚切换）";
        } else if (e instanceof java.net.ConnectException) {
            hint = "（连接被拒绝/不通：检查防火墙与代理）";
        } else if (e instanceof java.net.SocketTimeoutException) {
            hint = "（读超时：网络拥塞或图片过大）";
        }
        log.warn("[企微机器人] {} 发送异常 {} : {}", scene, type, msg, e);
        return "发送异常：" + type + (msg.isEmpty() ? "" : " " + msg) + hint;
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
