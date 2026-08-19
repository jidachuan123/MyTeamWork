package com.demo.consumer.portal.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 门户子系统配置（application.yml 的 portal.subsystems）
 *
 * 配置示例：
 * portal:
 *   subsystems:
 *     - code: A
 *       name: 子系统A
 *       desc: 报表查询系统
 *       remark: 真实对接系统
 *       color: "#409EFF"
 *       icon: DataAnalysis
 *       url: "http://192.168.199.202:8003/"    # 真实跳转地址（外链）→ 前端用 window.location.href
 *     - code: B
 *       name: 子系统B
 *       path: /sub/b                            # 仅前端路由（模拟）→ 前端用 router.push
 */
@Data
@Component
@ConfigurationProperties(prefix = "portal")
public class PortalProperties {

    private List<Subsystem> subsystems = new ArrayList<>();

    @Data
    public static class Subsystem {
        /** 应用编码（写入 ticket 的 targetApp，防止跨系统冒用） */
        private String code;
        private String name;
        private String desc;
        private String remark;
        private String color;
        private String icon;
        /** 子系统真实地址（外链跳转）。配置了 url 即走真实 SSO 跳转 */
        private String url;
        /** 前端路由路径（仅模拟跳转用，真实子系统无需配置） */
        private String path;

        /** 真实跳转时是否携带 SSO ticket（默认 true；对纯外链、不做 SSO 对接的系统设 false） */
        private boolean withTicket = true;

        public boolean isRealRedirect() {
            return url != null && !url.trim().isEmpty();
        }
    }
}
