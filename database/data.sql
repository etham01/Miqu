-- ============================================================
--  Miqu「觅取」社交系统 —— 开发/测试环境初始化数据
--  依赖：先执行 schema.sql
--
--  设计原则：
--    1. **显式写死主键 ID**，不依赖 AUTO_INCREMENT 顺序
--       —— 接口测试要能断言"用户 id=3 有 5 个粉丝"这类事实，
--          随机 ID 会让用例互相耦合、无法重复运行
--    2. 批量数据用确定性的 INSERT ... SELECT 生成，**不使用随机数**
--       同一个 post_id 每次跑出来的点赞集合完全相同
--    3. 文件末尾统一重算所有冗余计数，保证计数与实际关系表一致
--
--  测试账号（密码均为 123456）：
--    admin    / 123456   管理员
--    test001  / 123456   普通用户（id=2）
--    banned001/ 123456   已禁用用户（id=12）
--    deleted001/123456   已注销用户（id=13）
--
--  BCrypt 哈希 $2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm
--  == BCryptPasswordEncoder().encode("123456")
-- ============================================================

USE `miqu`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- 固定时间基准，避免每次执行后数据时间不同导致断言漂移
SET @NOW = NOW(3);

-- ============================================================
-- 1. 用户（21 个）
-- ============================================================
INSERT INTO `user` (`id`, `username`, `password`, `nickname`, `email`, `gender`, `birthday`, `bio`, `avatar`, `role`, `status`, `deleted`, `create_time`) VALUES
(1,  'admin',     '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '系统管理员', 'admin@miqu.com',      0, NULL,         'Miqu 官方管理员账号',                'https://i.pravatar.cc/150?img=68', 2, 1, 0, DATE_SUB(@NOW, INTERVAL 120 DAY)),
(2,  'test001',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '张三',       'test001@miqu.com',    1, '1998-03-15', '热爱摄影与旅行，喜欢记录生活的瞬间。', 'https://i.pravatar.cc/150?img=12', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 110 DAY)),
(3,  'test002',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '李四',       'test002@miqu.com',    1, '1997-07-02', '后端开发工程师，业余时间写写开源。',   'https://i.pravatar.cc/150?img=13', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 105 DAY)),
(4,  'test003',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '王五',       'test003@miqu.com',    2, '2000-01-20', '设计师，喜欢一切有秩序的美。',         'https://i.pravatar.cc/150?img=45', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 100 DAY)),
(5,  'test004',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '赵六',       'test004@miqu.com',    1, '1995-11-08', '咖啡爱好者 / 独立开发者。',           'https://i.pravatar.cc/150?img=14', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 95 DAY)),
(6,  'test005',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '孙七',       'test005@miqu.com',    2, '1999-05-30', '正在学习前端的路上。',                 'https://i.pravatar.cc/150?img=32', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 90 DAY)),
(7,  'test006',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '周八',       'test006@miqu.com',    1, '1996-09-12', '健身、跑步、篮球。',                   'https://i.pravatar.cc/150?img=15', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 85 DAY)),
(8,  'test007',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '吴九',       'test007@miqu.com',    2, '2001-02-14', '猫奴一枚，家里有两只布偶。',           'https://i.pravatar.cc/150?img=47', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 80 DAY)),
(9,  'test008',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '郑十',       'test008@miqu.com',    1, '1994-12-01', '十年 Java，仍然觉得自己是新手。',      'https://i.pravatar.cc/150?img=51', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 75 DAY)),
(10, 'test009',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '钱多多',     'test009@miqu.com',    2, '1998-08-18', '喜欢做饭，研究各种家常菜。',           'https://i.pravatar.cc/150?img=44', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 70 DAY)),
(11, 'test010',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '刘一',       'test010@miqu.com',    1, '2000-06-06', '在读大学生，主修计算机。',             'https://i.pravatar.cc/150?img=33', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 65 DAY)),
-- id=12 已禁用账号：用于验证登录返回 423、旧 Token 失效
(12, 'banned001', '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '被禁用的用户', 'banned001@miqu.com', 0, NULL,       '用于测试账号禁用场景',                 'https://i.pravatar.cc/150?img=60', 1, 0, 0, DATE_SUB(@NOW, INTERVAL 60 DAY)),
-- id=13 已注销账号：用于验证登录返回 401、访问返回 404
(13, 'deleted001','$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '已注销用户', 'deleted001@miqu.com',0, NULL,       '用于测试账号已注销场景',               'https://i.pravatar.cc/150?img=61', 1, 1, 1, DATE_SUB(@NOW, INTERVAL 55 DAY)),
(14, 'test011',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '陈小明',     'test011@miqu.com',    1, '1997-04-25', '产品经理，喜欢琢磨用户体验。',         'https://i.pravatar.cc/150?img=16', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 50 DAY)),
(15, 'test012',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '杨柳',       'test012@miqu.com',    2, '1999-10-10', '插画师，接稿中。',                     'https://i.pravatar.cc/150?img=48', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 45 DAY)),
(16, 'test013',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '黄二',       'test013@miqu.com',    1, '1993-01-03', '运维工程师，信奉自动化。',             'https://i.pravatar.cc/150?img=17', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 40 DAY)),
(17, 'test014',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '林小满',     'test014@miqu.com',    2, '2002-03-28', '大三在读，准备考研。',                 'https://i.pravatar.cc/150?img=49', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 35 DAY)),
(18, 'test015',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '许三',       'test015@miqu.com',    1, '1991-07-17', '架构师，关注分布式与数据库。',         'https://i.pravatar.cc/150?img=18', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 30 DAY)),
(19, 'test016',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '徐蕾',       'test016@miqu.com',    2, '1998-11-11', '测试工程师，正在学接口自动化。',       'https://i.pravatar.cc/150?img=50', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 25 DAY)),
(20, 'test017',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '何欢',       'test017@miqu.com',    1, '1996-02-29', '喜欢深夜写代码，白天睡觉。',           'https://i.pravatar.cc/150?img=19', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 20 DAY)),
(21, 'test018',   '$2a$10$Ul.sMNQWzHAcbS0M2SKG7ugp6Gu2LIOZ63EcZkIsABS3E5CLG5bxm', '罗小雨',     'test018@miqu.com',    2, '2001-09-09', '刚入行的前端，请多指教。',             'https://i.pravatar.cc/150?img=52', 1, 1, 0, DATE_SUB(@NOW, INTERVAL 15 DAY));

-- ============================================================
-- 2. 关注关系（49 条）
-- ============================================================

-- 互相关注对（断言用：user 2 与 user 3 互相关注）
INSERT INTO `follow` (`follower_id`, `following_id`, `create_time`) VALUES
(2, 3, DATE_SUB(@NOW, INTERVAL 100 DAY)),
(3, 2, DATE_SUB(@NOW, INTERVAL 99 DAY));

-- 显式关注的若干条
INSERT INTO `follow` (`follower_id`, `following_id`, `create_time`) VALUES
(2,  4,  DATE_SUB(@NOW, INTERVAL 80 DAY)),
(2,  5,  DATE_SUB(@NOW, INTERVAL 79 DAY)),
(2,  6,  DATE_SUB(@NOW, INTERVAL 78 DAY)),
(2,  7,  DATE_SUB(@NOW, INTERVAL 77 DAY)),
(2,  8,  DATE_SUB(@NOW, INTERVAL 76 DAY)),
(3,  4,  DATE_SUB(@NOW, INTERVAL 75 DAY)),
(3,  5,  DATE_SUB(@NOW, INTERVAL 74 DAY)),
(3,  6,  DATE_SUB(@NOW, INTERVAL 73 DAY)),
(3,  9,  DATE_SUB(@NOW, INTERVAL 72 DAY)),
(4,  2,  DATE_SUB(@NOW, INTERVAL 70 DAY)),
(4,  5,  DATE_SUB(@NOW, INTERVAL 69 DAY)),
(4,  9,  DATE_SUB(@NOW, INTERVAL 68 DAY)),
(5,  2,  DATE_SUB(@NOW, INTERVAL 66 DAY)),
(5,  3,  DATE_SUB(@NOW, INTERVAL 65 DAY)),
(5,  10, DATE_SUB(@NOW, INTERVAL 64 DAY)),
(6,  2,  DATE_SUB(@NOW, INTERVAL 62 DAY)),
(6,  3,  DATE_SUB(@NOW, INTERVAL 61 DAY)),
(6,  11, DATE_SUB(@NOW, INTERVAL 60 DAY)),
(7,  2,  DATE_SUB(@NOW, INTERVAL 58 DAY)),
(8,  2,  DATE_SUB(@NOW, INTERVAL 56 DAY)),
(9,  3,  DATE_SUB(@NOW, INTERVAL 54 DAY)),
(10, 4,  DATE_SUB(@NOW, INTERVAL 52 DAY)),
(11, 5,  DATE_SUB(@NOW, INTERVAL 50 DAY));

-- 批量关注：user 14~21 关注 user 2、3、4（24 条，确定性）
INSERT INTO `follow` (`follower_id`, `following_id`, `create_time`)
SELECT u.id, t.following_id, DATE_SUB(@NOW, INTERVAL (u.id * 2 + t.following_id) DAY)
FROM `user` u
JOIN (SELECT 2 AS following_id UNION ALL SELECT 3 UNION ALL SELECT 4) t
WHERE u.id BETWEEN 14 AND 21;

-- ============================================================
-- 3. 动态（40 条）
-- ============================================================

-- id=1：断言基准动态 —— 9 张图（上限）、10 个赞、5 条评论
INSERT INTO `post` (`id`, `user_id`, `content`, `image_count`, `create_time`) VALUES
(1, 2, '周末去了趟青海湖，天气好得不像话。整理了一组照片分享给大家 🌊', 9, DATE_SUB(@NOW, INTERVAL 30 DAY)),
-- id=2：无图动态（边界：images 为空）
(2, 3, '今天终于把困扰两周的线上问题定位到了，原来是一个没加索引的模糊查询。', 0, DATE_SUB(@NOW, INTERVAL 29 DAY)),
-- id=3：超长文本（边界：恰好 1000 字符）
(3, 4, CONCAT(REPEAT('这是一条用于测试内容长度上限的动态。', 44), '到此为止恰好一千字符。'), 0, DATE_SUB(@NOW, INTERVAL 28 DAY)),
(4, 5, '新买的咖啡豆到了，浅烘的耶加雪菲，柑橘味很明显。', 2, DATE_SUB(@NOW, INTERVAL 27 DAY)),
(5, 6, '学 React 的第三周，终于搞懂了 useEffect 的依赖数组。', 0, DATE_SUB(@NOW, INTERVAL 26 DAY)),
(6, 7, '今天跑了 10 公里，配速 5:20，状态不错。', 1, DATE_SUB(@NOW, INTERVAL 25 DAY)),
(7, 8, '我家主子今天又把花瓶打翻了，已经习惯了。', 3, DATE_SUB(@NOW, INTERVAL 24 DAY)),
(8, 9, '分享一个 MySQL 小技巧：ORDER BY 排序时带上主键，可以避免分页数据重复。', 0, DATE_SUB(@NOW, INTERVAL 23 DAY)),
(9, 10, '第一次做红烧肉，卖相一般但味道还行。', 4, DATE_SUB(@NOW, INTERVAL 22 DAY)),
(10, 11, '期末周结束了，终于可以睡个好觉。', 0, DATE_SUB(@NOW, INTERVAL 21 DAY));

-- id=11~40：批量生成，作者在 user 2~21 间循环，图片数 0~3
INSERT INTO `post` (`id`, `user_id`, `content`, `image_count`, `create_time`)
SELECT n.seq,
       2 + ((n.seq * 7) % 20),
       CONCAT('这是一条测试动态 #', n.seq, '，用于验证首页列表、分页与计数逻辑。'),
       (n.seq % 4),
       DATE_SUB(@NOW, INTERVAL (20 * 24 - n.seq * 11) HOUR)
FROM (
    SELECT 11 AS seq UNION ALL SELECT 12 UNION ALL SELECT 13 UNION ALL SELECT 14 UNION ALL SELECT 15
    UNION ALL SELECT 16 UNION ALL SELECT 17 UNION ALL SELECT 18 UNION ALL SELECT 19 UNION ALL SELECT 20
    UNION ALL SELECT 21 UNION ALL SELECT 22 UNION ALL SELECT 23 UNION ALL SELECT 24 UNION ALL SELECT 25
    UNION ALL SELECT 26 UNION ALL SELECT 27 UNION ALL SELECT 28 UNION ALL SELECT 29 UNION ALL SELECT 30
    UNION ALL SELECT 31 UNION ALL SELECT 32 UNION ALL SELECT 33 UNION ALL SELECT 34 UNION ALL SELECT 35
    UNION ALL SELECT 36 UNION ALL SELECT 37 UNION ALL SELECT 38 UNION ALL SELECT 39 UNION ALL SELECT 40
) n;

-- 批量补齐图片：为 image_count > 0 的动态生成对应数量的图片行
-- sort_order 严格等于 0..image_count-1，满足 uk_post_sort
INSERT INTO `post_image` (`post_id`, `url`, `sort_order`, `create_time`)
SELECT p.id,
       CONCAT('https://picsum.photos/seed/miqu', p.id, '_', s.ord, '/800/600'),
       s.ord,
       p.create_time
FROM `post` p
JOIN (
    SELECT 0 AS ord UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
    UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8
) s ON s.ord < p.image_count;

-- ============================================================
-- 4. 点赞（306 条）
-- ============================================================

-- post id=1 固定 10 个赞：user 2~11（断言基准）
INSERT INTO `post_like` (`post_id`, `user_id`, `create_time`)
SELECT 1, u.id, DATE_SUB(@NOW, INTERVAL (30 * 24 - u.id) HOUR)
FROM `user` u
WHERE u.id BETWEEN 2 AND 11;

-- 其余动态的点赞：确定性的分布（每条动态被哪些用户点赞完全可复现）
INSERT INTO `post_like` (`post_id`, `user_id`, `create_time`)
SELECT p.id, u.id, DATE_SUB(@NOW, INTERVAL (p.id * 3 + u.id) HOUR)
FROM `post` p
JOIN `user` u
  ON u.id BETWEEN 2 AND 21
 AND u.id <> p.user_id                      -- 不给自己点赞
 AND (p.id * 7 + u.id * 11) % 5 < 2         -- 约 40% 的点赞密度
WHERE p.id BETWEEN 2 AND 40;

-- ============================================================
-- 5. 评论（111 条）
-- ============================================================

-- post id=1 固定 5 条评论（断言基准）
INSERT INTO `comment` (`id`, `post_id`, `user_id`, `content`, `create_time`) VALUES
(1, 1, 3,  '构图很棒，第三张的湖面倒影尤其好看。', DATE_SUB(@NOW, INTERVAL 30 DAY)),
(2, 1, 5,  '请问用的什么镜头？',                   DATE_SUB(@NOW, INTERVAL 29 DAY)),
(3, 1, 7,  '青海湖确实值得去，去年我也去过一次。', DATE_SUB(@NOW, INTERVAL 28 DAY)),
(4, 1, 9,  '最后一张的色调处理得很舒服。',         DATE_SUB(@NOW, INTERVAL 27 DAY)),
(5, 1, 11, '收藏了，下次照着这个路线走。',         DATE_SUB(@NOW, INTERVAL 26 DAY));

-- 其余评论：批量生成
INSERT INTO `comment` (`post_id`, `user_id`, `content`, `create_time`)
SELECT p.id,
       2 + ((p.id * 13 + u.id * 5) % 20),
       CONCAT('评论 #', p.id, '：说得有道理，学习了。'),
       DATE_SUB(@NOW, INTERVAL (p.id * 2 + u.id) HOUR)
FROM `post` p
JOIN `user` u
  ON u.id BETWEEN 2 AND 21
 AND u.id <> p.user_id
 AND (p.id + u.id * 3) % 7 = 0
WHERE p.id BETWEEN 2 AND 40
  AND NOT (p.id = 1);

-- ============================================================
-- 6. 会话与私信
--    强制 user1_id < user2_id，满足 ck_user_order 与 uk_users
-- ============================================================
INSERT INTO `conversation` (`id`, `user1_id`, `user2_id`, `last_message_preview`, `last_message_time`, `user1_unread`, `user2_unread`, `create_time`) VALUES
(1, 2, 3, '好的，那就这么定了。',           DATE_SUB(@NOW, INTERVAL 2 HOUR),  0, 2, DATE_SUB(@NOW, INTERVAL 40 DAY)),
(2, 2, 4, '你的那张照片我特别喜欢。',       DATE_SUB(@NOW, INTERVAL 5 HOUR),  1, 0, DATE_SUB(@NOW, INTERVAL 35 DAY)),
(3, 3, 5, '下周的分享会你来吗？',           DATE_SUB(@NOW, INTERVAL 1 DAY),   0, 0, DATE_SUB(@NOW, INTERVAL 30 DAY)),
(4, 4, 6, '链接发你了，记得查收。',         DATE_SUB(@NOW, INTERVAL 2 DAY),   3, 0, DATE_SUB(@NOW, INTERVAL 25 DAY)),
(5, 5, 7, '收到，谢谢！',                   DATE_SUB(@NOW, INTERVAL 3 DAY),   0, 1, DATE_SUB(@NOW, INTERVAL 20 DAY));

-- 会话 1：user 2 <-> user 3，10 条
INSERT INTO `message` (`conversation_id`, `sender_id`, `receiver_id`, `content`, `is_read`, `read_time`, `create_time`) VALUES
(1, 2, 3, '在吗？想请教你一个问题。',       1, DATE_SUB(@NOW, INTERVAL 10 HOUR), DATE_SUB(@NOW, INTERVAL 12 HOUR)),
(1, 3, 2, '在的，你说。',                   1, DATE_SUB(@NOW, INTERVAL 11 HOUR), DATE_SUB(@NOW, INTERVAL 11 HOUR)),
(1, 2, 3, 'MySQL 的分页查询有没有推荐写法？', 1, DATE_SUB(@NOW, INTERVAL 10 HOUR), DATE_SUB(@NOW, INTERVAL 10 HOUR)),
(1, 3, 2, 'ORDER BY 带上主键，然后限制 size。', 1, DATE_SUB(@NOW, INTERVAL 9 HOUR), DATE_SUB(@NOW, INTERVAL 9 HOUR)),
(1, 2, 3, '为什么必须带主键？',             1, DATE_SUB(@NOW, INTERVAL 8 HOUR),  DATE_SUB(@NOW, INTERVAL 8 HOUR)),
(1, 3, 2, '因为 create_time 可能重复，排序不稳定。', 1, DATE_SUB(@NOW, INTERVAL 7 HOUR), DATE_SUB(@NOW, INTERVAL 7 HOUR)),
(1, 2, 3, '明白了，谢谢！',                 1, DATE_SUB(@NOW, INTERVAL 6 HOUR),  DATE_SUB(@NOW, INTERVAL 6 HOUR)),
(1, 3, 2, '不客气。',                       1, DATE_SUB(@NOW, INTERVAL 5 HOUR),  DATE_SUB(@NOW, INTERVAL 5 HOUR)),
(1, 3, 2, '对了，周末有空吗？想约你一起看展。', 0, NULL, DATE_SUB(@NOW, INTERVAL 3 HOUR)),
(1, 3, 2, '好的，那就这么定了。',           0, NULL,                           DATE_SUB(@NOW, INTERVAL 2 HOUR));

-- 会话 2~5：批量生成，确定性内容
INSERT INTO `message` (`conversation_id`, `sender_id`, `receiver_id`, `content`, `is_read`, `read_time`, `create_time`)
SELECT c.id,
       IF(s.n % 2 = 0, c.user1_id, c.user2_id),
       IF(s.n % 2 = 0, c.user2_id, c.user1_id),
       CONCAT('这是会话 ', c.id, ' 的第 ', s.n, ' 条测试消息。'),
       -- 会话 4 的最后 3 条保持未读，用于验证未读数
       IF(c.id = 4 AND s.n >= 8, 0, 1),
       IF(c.id = 4 AND s.n >= 8, NULL, DATE_SUB(@NOW, INTERVAL (s.n + c.id) HOUR)),
       DATE_SUB(@NOW, INTERVAL (s.n * 6 + c.id * 12) HOUR)
FROM `conversation` c
JOIN (
    SELECT 1 AS n UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5
    UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9 UNION ALL SELECT 10
) s
WHERE c.id BETWEEN 2 AND 5;

-- 回填每个会话的 last_message_id / last_message_time / 未读数
UPDATE `conversation` c
SET c.last_message_id = (
        SELECT m.id FROM `message` m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1
    ),
    c.last_message_preview = (
        SELECT LEFT(m.content, 100) FROM `message` m WHERE m.conversation_id = c.id ORDER BY m.id DESC LIMIT 1
    ),
    c.user1_unread = (
        SELECT COUNT(*) FROM `message` m WHERE m.conversation_id = c.id AND m.receiver_id = c.user1_id AND m.is_read = 0
    ),
    c.user2_unread = (
        SELECT COUNT(*) FROM `message` m WHERE m.conversation_id = c.id AND m.receiver_id = c.user2_id AND m.is_read = 0
    );

-- ============================================================
-- 7. 通知（覆盖 关注/点赞/评论 三类，已读与未读混合）
-- ============================================================

-- 关注通知：通知 user 2（张三）被 14~21 关注
INSERT INTO `notification` (`user_id`, `type`, `actor_id`, `content`, `is_read`, `create_time`)
SELECT 2, 1, u.id, '', IF(u.id <= 16, 1, 0), DATE_SUB(@NOW, INTERVAL u.id DAY)
FROM `user` u WHERE u.id BETWEEN 14 AND 21;

-- 点赞通知：post 1 的点赞者通知给作者 user 2
INSERT INTO `notification` (`user_id`, `type`, `actor_id`, `post_id`, `content`, `is_read`, `create_time`)
SELECT 2, 2, pl.user_id, pl.post_id, '', IF(pl.user_id % 2 = 0, 1, 0), pl.create_time
FROM `post_like` pl WHERE pl.post_id = 1;

-- 评论通知：post 1 的评论者通知给作者 user 2
INSERT INTO `notification` (`user_id`, `type`, `actor_id`, `post_id`, `comment_id`, `content`, `is_read`, `create_time`)
SELECT 2, 3, c.user_id, c.post_id, c.id, LEFT(c.content, 50), 0, c.create_time
FROM `comment` c WHERE c.post_id = 1;

-- ============================================================
-- 8. 举报（5 条，覆盖 3 种 target_type、3 种 status）
-- ============================================================
INSERT INTO `report` (`id`, `reporter_id`, `target_type`, `target_id`, `reason_type`, `reason_detail`, `status`, `handler_id`, `handle_remark`, `handle_time`, `create_time`) VALUES
(1, 5,  2, 11, 1, '疑似营销广告内容',   0, NULL, '',                        NULL,                           DATE_SUB(@NOW, INTERVAL 5 DAY)),
(2, 6,  2, 12, 2, '语言攻击他人',       1, 1,    '已删除该动态',            DATE_SUB(@NOW, INTERVAL 3 DAY), DATE_SUB(@NOW, INTERVAL 4 DAY)),
(3, 7,  3, 20, 3, '评论内容低俗',       0, NULL, '',                        NULL,                           DATE_SUB(@NOW, INTERVAL 3 DAY)),
(4, 8,  1, 15, 2, '头像与简介存在骚扰信息', 2, 1, '经核实未违规，予以驳回', DATE_SUB(@NOW, INTERVAL 1 DAY), DATE_SUB(@NOW, INTERVAL 2 DAY)),
(5, 9,  2, 25, 5, '其他：内容与标题不符', 0, NULL, '',                       NULL,                           DATE_SUB(@NOW, INTERVAL 1 DAY));

-- ============================================================
-- 9. 管理员操作日志
-- ============================================================
INSERT INTO `admin_operation_log` (`admin_id`, `operation_type`, `target_type`, `target_id`, `detail`, `ip`, `create_time`) VALUES
(1, 'DELETE_POST',  2, 12,  '删除违规动态 #12',           '127.0.0.1', DATE_SUB(@NOW, INTERVAL 3 DAY)),
(1, 'HANDLE_REPORT',4, 2,   '处理举报 #2：删除动态',      '127.0.0.1', DATE_SUB(@NOW, INTERVAL 3 DAY)),
(1, 'HANDLE_REPORT',4, 4,   '处理举报 #4：驳回',          '127.0.0.1', DATE_SUB(@NOW, INTERVAL 1 DAY)),
(1, 'DISABLE_USER', 1, 12,  '禁用用户 banned001',         '127.0.0.1', DATE_SUB(@NOW, INTERVAL 2 DAY));

-- ============================================================
-- 10. 重算所有冗余计数
--     保证 user.following_count / follower_count / post_count
--     与 post.like_count / comment_count 与实际关系表完全一致。
--     这段同时也是"计数器修复脚本"，数据异常时可直接重跑。
-- ============================================================
UPDATE `user` u
SET u.follower_count  = (SELECT COUNT(*) FROM `follow` f WHERE f.following_id = u.id),
    u.following_count = (SELECT COUNT(*) FROM `follow` f WHERE f.follower_id  = u.id),
    u.post_count      = (SELECT COUNT(*) FROM `post`   p WHERE p.user_id = u.id AND p.deleted = 0);

UPDATE `post` p
SET p.like_count    = (SELECT COUNT(*) FROM `post_like` pl WHERE pl.post_id = p.id),
    p.comment_count = (SELECT COUNT(*) FROM `comment`  c  WHERE c.post_id  = p.id AND c.deleted = 0),
    p.image_count   = (SELECT COUNT(*) FROM `post_image` pi WHERE pi.post_id = p.id);

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 数据自检（可选执行，用于确认初始化结果）
-- ============================================================
-- SELECT 'users' AS t, COUNT(*) FROM `user`
-- UNION ALL SELECT 'follow',    COUNT(*) FROM `follow`
-- UNION ALL SELECT 'post',      COUNT(*) FROM `post`
-- UNION ALL SELECT 'post_image',COUNT(*) FROM `post_image`
-- UNION ALL SELECT 'post_like', COUNT(*) FROM `post_like`
-- UNION ALL SELECT 'comment',   COUNT(*) FROM `comment`
-- UNION ALL SELECT 'message',   COUNT(*) FROM `message`
-- UNION ALL SELECT 'notify',    COUNT(*) FROM `notification`
-- UNION ALL SELECT 'report',    COUNT(*) FROM `report`;
