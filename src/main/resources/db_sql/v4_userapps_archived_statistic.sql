/*
 Navicat Premium Dump SQL

 Source Server         : sparkle3
 Source Server Type    : PostgreSQL
 Source Server Version : 170007 (170007)
 Source Host           : cyrene:5433
 Source Catalog        : sparkle
 Source Schema         : public

 Target Server Type    : PostgreSQL
 Target Server Version : 170007 (170007)
 File Encoding         : 65001

 Date: 10/01/2026 00:08:26
*/


-- ----------------------------
-- Table structure for userapps_archived_statistic
-- ----------------------------
DROP TABLE IF EXISTS "public"."userapps_archived_statistic";
CREATE TABLE "public"."userapps_archived_statistic" (
  "userapp_id" int8 NOT NULL,
  "archived_to_peer_traffic" int8 NOT NULL,
  "archived_from_peer_traffic" int8 NOT NULL,
  "archived_ban_history_records" int8 NOT NULL,
  "archived_swarm_tracker_records" int8 NOT NULL,
  "last_update_at" timestamptz(6) NOT NULL
)
;

-- ----------------------------
-- Primary Key structure for table userapps_archived_statistic
-- ----------------------------
ALTER TABLE "public"."userapps_archived_statistic" ADD CONSTRAINT "userapps_archived_statistic_pkey" PRIMARY KEY ("userapp_id");
