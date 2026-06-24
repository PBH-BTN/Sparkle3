CREATE OR REPLACE PROCEDURE proc_migrate_ban_history()
LANGUAGE plpgsql
AS $$
DECLARE
v_child_table RECORD;
    v_count INT := 0;
BEGIN
    -- 1. 找出当前 ban_history 的所有底层子分区
FOR v_child_table IN
SELECT nmsp_child.nspname  AS child_schema,
       tbl_child.relname   AS child_table
FROM pg_inherits
         JOIN pg_class tbl_parent    ON pg_inherits.inhparent = tbl_parent.oid
         JOIN pg_class tbl_child     ON pg_inherits.inhrelid = tbl_child.oid
         JOIN pg_namespace nmsp_child ON tbl_child.relnamespace = nmsp_child.oid
WHERE tbl_parent.relname = 'ban_history'
ORDER BY tbl_child.relname ASC
    LOOP
        RAISE NOTICE '正在迁移分区: %.% ...', v_child_table.child_schema, v_child_table.child_table;

-- 2. 动态插入当前分区的数据
EXECUTE format('INSERT INTO ban_history_new SELECT * FROM %I.%I',
               v_child_table.child_schema, v_child_table.child_table);

v_count := v_count + 1;
        RAISE NOTICE '分区 % 迁移完成。累计已完成 % 个分区。', v_child_table.child_table, v_count;

        -- 3. 【边导边删，极限腾空间】确认导完一个，立刻物理删除原表对应的子分区
EXECUTE format('DROP TABLE %I.%I', v_child_table.child_schema, v_child_table.child_table);
RAISE NOTICE '已物理删除旧分区 %, 释放磁盘空间。', v_child_table.child_table;

        -- 4. 【核心灵魂】强行提交当前事务！
        -- 这会立刻释放之前的锁，允许 Postgres 触发 Checkpoint 回收这部分 WAL，并释放内存！
COMMIT;

END LOOP;

    RAISE NOTICE '所有历史分区数据迁移完毕！';
END $$;

CALL proc_migrate_ban_history();

BEGIN;

-- 1. 旧表改名备份
ALTER TABLE ban_history RENAME TO ban_history_old;

-- 2. 新超表正式上线
ALTER TABLE ban_history_new RENAME TO ban_history;

COMMIT;

DROP TABLE ban_history_old;