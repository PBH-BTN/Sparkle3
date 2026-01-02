/*
 Navicat Premium Dump SQL

 Source Server         : pbhbtn-netcup-g12
 Source Server Type    : PostgreSQL
 Source Server Version : 180001 (180001)
 Source Host           : pbhbtn-netcup-g12:5432
 Source Catalog        : sparkle3
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 180001 (180001)
 File Encoding         : 65001

 Date: 02/01/2026 15:16:32
*/


-- ----------------------------
-- Table structure for user_swarm_statistic
-- ----------------------------
DROP TABLE IF EXISTS "public"."user_swarm_statistic";
CREATE TABLE "public"."user_swarm_statistic" (
  "user_id" int8 NOT NULL,
  "last_update_at" timestamptz(6) NOT NULL,
  "sent_traffic_self_report" int8 NOT NULL,
  "received_traffic_self_report" int8 NOT NULL,
  "sent_traffic_other_ack" int8 NOT NULL,
  "received_traffic_other_ack" int8 NOT NULL
)
;

-- ----------------------------
-- Indexes structure for table user_swarm_statistic
-- ----------------------------
CREATE INDEX "user_swarm_statistic_last_update_at" ON "public"."user_swarm_statistic" USING btree (
  "last_update_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE INDEX "user_swarm_statistic_user_id" ON "public"."user_swarm_statistic" USING btree (
  "user_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table user_swarm_statistic
-- ----------------------------
ALTER TABLE "public"."user_swarm_statistic" ADD CONSTRAINT "user_swarm_statistic_pkey" PRIMARY KEY ("user_id");
