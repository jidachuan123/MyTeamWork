-- ============================================================
-- 销售详情3（SD3）查询 SQL  ——  MySQL(bcm)  ——  2026-10-15 新建
--
-- ★ 会话变量（@开始日期 等）由 Java 在同一 Connection 上先 SET 再执行本 SQL，
--   因此这里保持用户原 SQL 写法不变，不要用 ? 占位符替换。
-- ★ 本文件是「页面查询」和「截图」共用的唯一取数口径，改这里两边同时生效。
--
-- ★ @机构 = 逗号分隔的门店编码「精确」匹配（2026-10-15 由模糊前缀匹配改过来）：
--     传 '1103011'          → 只查这一个店
--     传 '1103011,1103021'  → 只查这两个店
--     传 ''                 → 不按机构过滤（全部店）
--   Java 侧会先做归一化（按中英文逗号切分 + 去空格 + 去重），所以写 '1103011, 1103021' 也可以。
--   COLLATE 说明：bcm 库的 store_code 是 utf8mb4_general_ci，会话变量跟连接走 utf8mb4_0900_ai_ci，
--   两边不显式统一会报 Illegal mix of collations，所以这里给列加 COLLATE（实测可用，勿删）。
-- ============================================================

SELECT
    t.`门店编码` AS 门店编码,
    t.`门店名称` AS 门店名称,
    c.chnl_name AS 渠道,
    t.`含税销售金额` AS 含税销售金额,
    t.包装费 as 包装费,
    t.`订单号` AS 订单号
FROM (
    -- 正常订单
    SELECT
        o.store_code AS `门店编码`,
        s.store_name AS `门店名称`,
        o.order_no AS `订单号`,
        o.order_create_time AS `下单时间`,
        o.finished_time AS `订单完成时间`,
        o.trans_sale_time AS `传流水时间`,
        o.chnl_type AS `渠道`,
        o.original_price AS `售价金额`,
        IFNULL(dis.sum_tenant_fee, 0) AS `订单级优惠`,
        IFNULL(dis.sum_tenant_fee2, 0) AS `商品级优惠`,
        IFNULL(dis.sum_tenant_fee3, 0) AS `运费级优惠`,
        IFNULL(dis.sum_tenant_fee5, 0) AS `包装费优惠`,
        (o.original_price
            - IFNULL(dis.sum_tenant_fee, 0)
            - IFNULL(dis.sum_tenant_fee2, 0)
            - IFNULL(dis.sum_tenant_fee3, 0)
            - IFNULL(dis.sum_tenant_fee5, 0)
        ) AS `含税销售金额`,
        o.third_order_no AS 三方订单号,
        IFNULL(bcmbill.sum_commission, 0) as 平台佣金,
        IFNULL(bcmbill.sum_package, 0) as 包装费
    FROM bcm_order o
    LEFT JOIN bcm_store s ON o.store_code = s.store_code
    LEFT JOIN (
        SELECT
            order_no,
            SUM(IF(prom_level=1, tenant_fee, 0)) AS sum_tenant_fee,
            SUM(IF(prom_level=2, tenant_fee, 0)) AS sum_tenant_fee2,
            SUM(IF(prom_level=3, tenant_fee, 0)) AS sum_tenant_fee3,
            SUM(IF(prom_level=5, tenant_fee, 0)) AS sum_tenant_fee5
        FROM bcm_order_discount
        WHERE create_time >= DATE_ADD(STR_TO_DATE(@开始日期, '%Y-%m-%d'), INTERVAL -7 DAY)
          AND create_time < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
        GROUP BY order_no
    ) dis ON dis.order_no = o.order_no
    LEFT JOIN (
        SELECT
            third_src_order_no,
            SUM(IFNULL(commission, 0)+IFNULL(pay_service_commission, 0)) as sum_commission,
            SUM(package_amount) as sum_package
        FROM bcm_bill
        WHERE bill_create_time >= DATE_ADD(STR_TO_DATE(@开始日期, '%Y-%m-%d'), INTERVAL -7 DAY)
          AND bill_create_time < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
        GROUP BY third_src_order_no
    ) bcmbill ON o.third_order_no = bcmbill.third_src_order_no
    WHERE o.sync_erp_status = 1

    UNION ALL

    -- 售后订单
    SELECT
        o.store_code AS `门店编码`,
        s.store_name AS `门店名称`,
        o.afs_order_no AS `订单号`,
        o.create_time AS `下单时间`,
        bsh.trade_time AS `订单完成时间`,
        o.trans_sale_time AS `传流水时间`,
        o.chnl_type AS `渠道`,
        -o.original_price AS `售价金额`,
        -IFNULL(dis1.sum_tenant_fee, 0) AS `订单级优惠`,
        -IFNULL(dis1.sum_tenant_fee2, 0) AS `商品级优惠`,
        -IFNULL(dis2.sum_tenant_fee3, 0) AS `运费级优惠`,
        -IFNULL(dis2.sum_tenant_fee5, 0) AS `包装费优惠`,
        -(o.original_price
            - IFNULL(dis1.sum_tenant_fee, 0)
            - IFNULL(dis1.sum_tenant_fee2, 0)
            - IFNULL(dis2.sum_tenant_fee3, 0)
            - IFNULL(dis2.sum_tenant_fee5, 0)
        ) AS `含税销售金额`,
        o.third_order_no AS 三方订单号,
        IFNULL(bcmbill.sum_commission, 0) as 平台佣金,
        IFNULL(bcmbill.sum_package, 0) as 包装费
    FROM bcm_afs_order o
    LEFT JOIN bcm_sale_head bsh ON o.afs_order_no=bsh.order_no AND o.store_code=bsh.store_code
    LEFT JOIN bcm_store s ON o.store_code = s.store_code
    LEFT JOIN (
        SELECT
            afs_order_no,
            SUM(IF(prom_level = 1, tenant_share_fee, 0)) AS sum_tenant_fee,
            SUM(IF(prom_level = 2, tenant_share_fee, 0)) AS sum_tenant_fee2
        FROM bcm_afs_order_goods_discount
        WHERE create_time >= DATE_ADD(STR_TO_DATE(@开始日期, '%Y-%m-%d'), INTERVAL -7 DAY)
          AND create_time < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
        GROUP BY afs_order_no
    ) dis1 ON o.afs_order_no = dis1.afs_order_no
    LEFT JOIN (
        SELECT
            afs_order_no,
            SUM(IF(prom_level = 3, tenant_fee, 0)) AS sum_tenant_fee3,
            SUM(IF(prom_level = 5, tenant_fee, 0)) AS sum_tenant_fee5
        FROM bcm_afs_order_discount
        WHERE create_time >= DATE_ADD(STR_TO_DATE(@开始日期, '%Y-%m-%d'), INTERVAL -7 DAY)
          AND create_time < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
        GROUP BY afs_order_no
    ) dis2 ON o.afs_order_no = dis2.afs_order_no
    LEFT JOIN (
        SELECT
            third_src_order_no,
            SUM(IFNULL(commission, 0)+IFNULL(pay_service_commission, 0)) as sum_commission,
            SUM(package_amount) as sum_package
        FROM bcm_bill
        WHERE bill_create_time >= DATE_ADD(STR_TO_DATE(@开始日期, '%Y-%m-%d'), INTERVAL -7 DAY)
          AND bill_create_time < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
        GROUP BY third_src_order_no
    ) bcmbill ON o.third_afs_order_no = bcmbill.third_src_order_no
    WHERE o.sync_erp_status = 1
) t
LEFT JOIN bcm_chnl c ON t.`渠道` = c.chnl_type
WHERE t.`订单完成时间` >= STR_TO_DATE(@开始日期, '%Y-%m-%d')
  AND t.`订单完成时间` < DATE_ADD(STR_TO_DATE(@结束日期, '%Y-%m-%d'), INTERVAL 1 DAY)
  AND (IFNULL(@机构, '') = '' OR FIND_IN_SET(t.`门店编码` COLLATE utf8mb4_general_ci, @机构) > 0)
  AND (IFNULL(@渠道, '') = '' OR c.chnl_name = @渠道 COLLATE utf8mb4_general_ci)
  AND (IFNULL(@订单号, '') = '' OR t.`订单号` = @订单号 COLLATE utf8mb4_general_ci)
  AND t.含税销售金额 > 0