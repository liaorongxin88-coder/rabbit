-- 笼位编号统一成「排-位-层」，例如 2-3-1。
--
-- 起因：建兔舍生成 `1-3-2`，App 批量建笼生成 `2(下)1`，同一个兔舍里两套写法并存，
-- 工人拿笼上的签对不上系统。生成侧已经收口到后端 CageNumbers，这里把存量补齐。
--
-- 三条自保规则，宁可少改也不能改错：
--   1) 坐标不全（row_code 为空/LEGACY，或位号层号缺失）的一律不动——推不出编号就别瞎编；
--   2) 新号在同一个兔舍里已经被别的笼位占了，跳过——绝不覆盖别人的编号；
--   3) 两个笼位算出同一个新号（坐标本身就重复），两个都跳过——留着让人去查，
--      自动挑一个改名只会把账做平、把问题埋住。
-- 跳过的笼位保持原编号，功能不受影响：地图按坐标画，编号只用于展示和搜索。
--
-- 改名前的编号写进 remark：笼子上可能已经贴着旧号的签，工人在页面上还能对上。

CREATE TABLE tmp_cage_renumber_v31 (
    id BIGINT NOT NULL PRIMARY KEY,
    house_id BIGINT NOT NULL,
    old_number VARCHAR(50) NULL,
    canonical VARCHAR(50) NOT NULL,
    KEY idx_house_canonical (house_id, canonical)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 候选：坐标齐全，且现编号跟规范编号不一致
INSERT INTO tmp_cage_renumber_v31 (id, house_id, old_number, canonical)
SELECT
    c.id,
    c.house_id,
    c.cage_number,
    CONCAT(
        CASE
            -- 排号存成 R2 这种带前缀的形式，编号里只留数字；CAST 顺手抹掉 R02 的前导零
            WHEN c.row_code REGEXP '^[Rr][0-9]+$' THEN CAST(SUBSTRING(c.row_code, 2) AS UNSIGNED)
            ELSE c.row_code
        END,
        '-', c.position_index, '-', c.layer_index
    )
FROM cages c
WHERE c.row_code IS NOT NULL
  AND c.row_code <> ''
  AND UPPER(c.row_code) <> 'LEGACY'
  AND c.position_index IS NOT NULL AND c.position_index > 0
  AND c.layer_index IS NOT NULL AND c.layer_index > 0;

DELETE t FROM tmp_cage_renumber_v31 t
JOIN cages c ON c.id = t.id
WHERE c.cage_number = t.canonical;

-- 规则 2：新号已被同舍其它笼位占用
DELETE t FROM tmp_cage_renumber_v31 t
JOIN cages c ON c.house_id = t.house_id AND c.cage_number = t.canonical AND c.id <> t.id;

-- 规则 3：两个笼位算出同一个新号
DELETE t FROM tmp_cage_renumber_v31 t
JOIN (
    SELECT house_id, canonical
    FROM tmp_cage_renumber_v31
    GROUP BY house_id, canonical
    HAVING COUNT(*) > 1
) d ON d.house_id = t.house_id AND d.canonical = t.canonical;

UPDATE cages c
JOIN tmp_cage_renumber_v31 t ON t.id = c.id
SET c.cage_number = t.canonical,
    c.remark = CASE
        WHEN t.old_number IS NULL OR t.old_number = '' THEN c.remark
        WHEN c.remark IS NULL OR c.remark = '' THEN CONCAT('原编号 ', t.old_number)
        -- 已有备注就往后缀，不覆盖人写的东西
        ELSE CONCAT(c.remark, '；原编号 ', t.old_number)
    END,
    c.update_time = c.update_time;

DROP TABLE tmp_cage_renumber_v31;
