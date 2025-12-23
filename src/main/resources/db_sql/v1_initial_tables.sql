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

 Date: 23/12/2025 18:59:38
*/


-- ----------------------------
-- Type structure for gtrgm
-- ----------------------------
DROP TYPE IF EXISTS "public"."gtrgm";
CREATE TYPE "public"."gtrgm" (
  INPUT = "public"."gtrgm_in",
  OUTPUT = "public"."gtrgm_out",
  INTERNALLENGTH = VARIABLE,
  CATEGORY = U,
  DELIMITER = ','
);

-- ----------------------------
-- Sequence structure for analyse_rule_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."analyse_rule_id_seq";
CREATE SEQUENCE "public"."analyse_rule_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for analyserules_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."analyserules_id_seq";
CREATE SEQUENCE "public"."analyserules_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for ban_history_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."ban_history_id_seq";
CREATE SEQUENCE "public"."ban_history_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for banhistory_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."banhistory_id_seq";
CREATE SEQUENCE "public"."banhistory_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for rules_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."rules_id_seq";
CREATE SEQUENCE "public"."rules_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for swarm_tracker_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."swarm_tracker_id_seq";
CREATE SEQUENCE "public"."swarm_tracker_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for swarmtracker_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."swarmtracker_id_seq";
CREATE SEQUENCE "public"."swarmtracker_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for torrent_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."torrent_id_seq";
CREATE SEQUENCE "public"."torrent_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for torrents_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."torrents_id_seq";
CREATE SEQUENCE "public"."torrents_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for user_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."user_id_seq";
CREATE SEQUENCE "public"."user_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for user_rel_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."user_rel_id_seq";
CREATE SEQUENCE "public"."user_rel_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for user_rel_id_seq1
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."user_rel_id_seq1";
CREATE SEQUENCE "public"."user_rel_id_seq1" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for userapp_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."userapp_id_seq";
CREATE SEQUENCE "public"."userapp_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for userapps_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."userapps_id_seq";
CREATE SEQUENCE "public"."userapps_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Sequence structure for users_id_seq
-- ----------------------------
DROP SEQUENCE IF EXISTS "public"."users_id_seq";
CREATE SEQUENCE "public"."users_id_seq" 
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1;

-- ----------------------------
-- Table structure for analyse_rule
-- ----------------------------
DROP TABLE IF EXISTS "public"."analyse_rule";
CREATE TABLE "public"."analyse_rule" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "module" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "value" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "comment" text COLLATE "pg_catalog"."default" NOT NULL
)
;

-- ----------------------------
-- Table structure for ban_history
-- ----------------------------
DROP TABLE IF EXISTS "public"."ban_history";
CREATE TABLE "public"."ban_history" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "insert_time" timestamptz(6) NOT NULL,
  "populate_time" timestamptz(6) NOT NULL,
  "userapps_id" int8 NOT NULL,
  "torrent_id" int8 NOT NULL,
  "peer_ip" inet NOT NULL,
  "peer_port" int4 NOT NULL,
  "peer_id" varchar(20) COLLATE "pg_catalog"."default",
  "peer_client_name" text COLLATE "pg_catalog"."default",
  "peer_progress" float8 NOT NULL,
  "peer_flags" varchar(255) COLLATE "pg_catalog"."default",
  "peer_geoip" jsonb,
  "reporter_progress" float8,
  "to_peer_traffic" int8 NOT NULL,
  "from_peer_traffic" int8 NOT NULL,
  "module_name" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "rule" text COLLATE "pg_catalog"."default" NOT NULL,
  "structured_data" jsonb,
  "description" text COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Table structure for client_discovery
-- ----------------------------
DROP TABLE IF EXISTS "public"."client_discovery";
CREATE TABLE "public"."client_discovery" (
  "hash" int8 NOT NULL,
  "peer_id" varchar(20) COLLATE "pg_catalog"."default",
  "peer_client_name" text COLLATE "pg_catalog"."default",
  "found_at" timestamptz(6) NOT NULL,
  "found_userapps_id" int8 NOT NULL,
  "client_type" varchar COLLATE "pg_catalog"."default",
  "client_semver" varchar(255) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Table structure for qrtz_blob_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_blob_triggers";
CREATE TABLE "public"."qrtz_blob_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "blob_data" bytea
)
;

-- ----------------------------
-- Table structure for qrtz_calendars
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_calendars";
CREATE TABLE "public"."qrtz_calendars" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "calendar_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "calendar" bytea NOT NULL
)
;

-- ----------------------------
-- Table structure for qrtz_cron_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_cron_triggers";
CREATE TABLE "public"."qrtz_cron_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "cron_expression" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "time_zone_id" varchar(80) COLLATE "pg_catalog"."default"
)
;

-- ----------------------------
-- Table structure for qrtz_fired_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_fired_triggers";
CREATE TABLE "public"."qrtz_fired_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "entry_id" varchar(95) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "instance_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "fired_time" int8 NOT NULL,
  "sched_time" int8 NOT NULL,
  "priority" int4 NOT NULL,
  "state" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default",
  "job_group" varchar(200) COLLATE "pg_catalog"."default",
  "is_nonconcurrent" bool,
  "requests_recovery" bool
)
;

-- ----------------------------
-- Table structure for qrtz_job_details
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_job_details";
CREATE TABLE "public"."qrtz_job_details" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(250) COLLATE "pg_catalog"."default",
  "job_class_name" varchar(250) COLLATE "pg_catalog"."default" NOT NULL,
  "is_durable" bool NOT NULL,
  "is_nonconcurrent" bool NOT NULL,
  "is_update_data" bool NOT NULL,
  "requests_recovery" bool NOT NULL,
  "job_data" bytea
)
;

-- ----------------------------
-- Table structure for qrtz_locks
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_locks";
CREATE TABLE "public"."qrtz_locks" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "lock_name" varchar(40) COLLATE "pg_catalog"."default" NOT NULL
)
;

-- ----------------------------
-- Table structure for qrtz_paused_trigger_grps
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_paused_trigger_grps";
CREATE TABLE "public"."qrtz_paused_trigger_grps" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL
)
;

-- ----------------------------
-- Table structure for qrtz_scheduler_state
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_scheduler_state";
CREATE TABLE "public"."qrtz_scheduler_state" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "instance_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "last_checkin_time" int8 NOT NULL,
  "checkin_interval" int8 NOT NULL
)
;

-- ----------------------------
-- Table structure for qrtz_simple_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_simple_triggers";
CREATE TABLE "public"."qrtz_simple_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "repeat_count" int8 NOT NULL,
  "repeat_interval" int8 NOT NULL,
  "times_triggered" int8 NOT NULL
)
;

-- ----------------------------
-- Table structure for qrtz_simprop_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_simprop_triggers";
CREATE TABLE "public"."qrtz_simprop_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "str_prop_1" varchar(512) COLLATE "pg_catalog"."default",
  "str_prop_2" varchar(512) COLLATE "pg_catalog"."default",
  "str_prop_3" varchar(512) COLLATE "pg_catalog"."default",
  "int_prop_1" int4,
  "int_prop_2" int4,
  "long_prop_1" int8,
  "long_prop_2" int8,
  "dec_prop_1" numeric(13,4),
  "dec_prop_2" numeric(13,4),
  "bool_prop_1" bool,
  "bool_prop_2" bool
)
;

-- ----------------------------
-- Table structure for qrtz_triggers
-- ----------------------------
DROP TABLE IF EXISTS "public"."qrtz_triggers";
CREATE TABLE "public"."qrtz_triggers" (
  "sched_name" varchar(120) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_name" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "job_group" varchar(200) COLLATE "pg_catalog"."default" NOT NULL,
  "description" varchar(250) COLLATE "pg_catalog"."default",
  "next_fire_time" int8,
  "prev_fire_time" int8,
  "priority" int4,
  "trigger_state" varchar(16) COLLATE "pg_catalog"."default" NOT NULL,
  "trigger_type" varchar(8) COLLATE "pg_catalog"."default" NOT NULL,
  "start_time" int8 NOT NULL,
  "end_time" int8,
  "calendar_name" varchar(200) COLLATE "pg_catalog"."default",
  "misfire_instr" int2,
  "job_data" bytea
)
;

-- ----------------------------
-- Table structure for rules
-- ----------------------------
DROP TABLE IF EXISTS "public"."rules";
CREATE TABLE "public"."rules" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "category" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "content" text COLLATE "pg_catalog"."default" NOT NULL,
  "type" varchar(255) COLLATE "pg_catalog"."default" NOT NULL
)
;

-- ----------------------------
-- Table structure for swarm_tracker
-- ----------------------------
DROP TABLE IF EXISTS "public"."swarm_tracker";
CREATE TABLE "public"."swarm_tracker" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "userapps_id" int8,
  "user_downloader" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "torrent_id" int8 NOT NULL,
  "peer_ip" inet NOT NULL,
  "peer_port" int4 NOT NULL,
  "peer_id" varchar(20) COLLATE "pg_catalog"."default",
  "peer_client_name" text COLLATE "pg_catalog"."default",
  "peer_progress" float8 NOT NULL,
  "from_peer_traffic" int8 NOT NULL,
  "to_peer_traffic" int8 NOT NULL,
  "from_peer_traffic_offset" int8 NOT NULL,
  "to_peer_traffic_offset" int8 NOT NULL,
  "flags" varchar(255) COLLATE "pg_catalog"."default",
  "first_time_seen" timestamptz(6) NOT NULL,
  "last_time_seen" timestamptz(6) NOT NULL,
  "user_progress" float8 NOT NULL
)
;

-- ----------------------------
-- Table structure for torrent
-- ----------------------------
DROP TABLE IF EXISTS "public"."torrent";
CREATE TABLE "public"."torrent" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "torrent_identifier" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "size" int8 NOT NULL,
  "private_torrent" bool,
  "info_hash" varchar(255) COLLATE "pg_catalog"."default",
  "torrent_name" text COLLATE "pg_catalog"."default",
  "last_seen_at" timestamptz(6)
)
;

-- ----------------------------
-- Table structure for user
-- ----------------------------
DROP TABLE IF EXISTS "public"."user";
CREATE TABLE "public"."user" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "avatar" text COLLATE "pg_catalog"."default" NOT NULL,
  "email" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "nickname" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "register_at" timestamptz(6) NOT NULL,
  "banned_at" timestamptz(6),
  "banned_reason" text COLLATE "pg_catalog"."default",
  "last_login_at" timestamptz(6) NOT NULL,
  "role" varchar(255) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'user'::character varying
)
;

-- ----------------------------
-- Table structure for user_rel
-- ----------------------------
DROP TABLE IF EXISTS "public"."user_rel";
CREATE TABLE "public"."user_rel" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "platform" varchar(255) COLLATE "pg_catalog"."default" NOT NULL DEFAULT 'github'::character varying,
  "platform_user_id" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "platform_user_login" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "platform_user_email" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "bind_user_id" int8 NOT NULL,
  "bind_at" timestamptz(6) NOT NULL DEFAULT now()
)
;

-- ----------------------------
-- Table structure for userapp
-- ----------------------------
DROP TABLE IF EXISTS "public"."userapp";
CREATE TABLE "public"."userapp" (
  "id" int8 NOT NULL GENERATED BY DEFAULT AS IDENTITY (
INCREMENT 1
MINVALUE  1
MAXVALUE 9223372036854775807
START 1
CACHE 1
),
  "app_id" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "app_secret" varchar(255) COLLATE "pg_catalog"."default" NOT NULL,
  "created_at" timestamptz(6) NOT NULL,
  "owner" int8 NOT NULL,
  "banned_at" timestamptz(6),
  "banned_reason" text COLLATE "pg_catalog"."default",
  "comment" text COLLATE "pg_catalog"."default" NOT NULL,
  "delete_at" timestamptz(6),
  "installation_id" varchar(255) COLLATE "pg_catalog"."default",
  "create_ip" inet,
  "last_seen_at" timestamptz(6)
)
;

-- ----------------------------
-- Table structure for userapps_heartbeat
-- ----------------------------
DROP TABLE IF EXISTS "public"."userapps_heartbeat";
CREATE TABLE "public"."userapps_heartbeat" (
  "id" int8 NOT NULL,
  "userapp_id" int8 NOT NULL,
  "ip" inet NOT NULL,
  "first_seen_at" timestamptz(6) NOT NULL,
  "last_seen_at" timestamptz(6) NOT NULL
)
;

-- ----------------------------
-- Function structure for gin_extract_query_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_extract_query_trgm"(text, internal, int2, internal, internal, internal, internal);
CREATE FUNCTION "public"."gin_extract_query_trgm"(text, internal, int2, internal, internal, internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gin_extract_query_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_extract_value_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_extract_value_trgm"(text, internal);
CREATE FUNCTION "public"."gin_extract_value_trgm"(text, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gin_extract_value_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_trgm_consistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_trgm_consistent"(internal, int2, text, int4, internal, internal, internal, internal);
CREATE FUNCTION "public"."gin_trgm_consistent"(internal, int2, text, int4, internal, internal, internal, internal)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'gin_trgm_consistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gin_trgm_triconsistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gin_trgm_triconsistent"(internal, int2, text, int4, internal, internal, internal);
CREATE FUNCTION "public"."gin_trgm_triconsistent"(internal, int2, text, int4, internal, internal, internal)
  RETURNS "pg_catalog"."char" AS '$libdir/pg_trgm', 'gin_trgm_triconsistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_compress
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_compress"(internal);
CREATE FUNCTION "public"."gtrgm_compress"(internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_compress'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_consistent
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_consistent"(internal, text, int2, oid, internal);
CREATE FUNCTION "public"."gtrgm_consistent"(internal, text, int2, oid, internal)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'gtrgm_consistent'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_decompress
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_decompress"(internal);
CREATE FUNCTION "public"."gtrgm_decompress"(internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_decompress'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_distance
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_distance"(internal, text, int2, oid, internal);
CREATE FUNCTION "public"."gtrgm_distance"(internal, text, int2, oid, internal)
  RETURNS "pg_catalog"."float8" AS '$libdir/pg_trgm', 'gtrgm_distance'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_in
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_in"(cstring);
CREATE FUNCTION "public"."gtrgm_in"(cstring)
  RETURNS "public"."gtrgm" AS '$libdir/pg_trgm', 'gtrgm_in'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_options
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_options"(internal);
CREATE FUNCTION "public"."gtrgm_options"(internal)
  RETURNS "pg_catalog"."void" AS '$libdir/pg_trgm', 'gtrgm_options'
  LANGUAGE c IMMUTABLE
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_out
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_out"("public"."gtrgm");
CREATE FUNCTION "public"."gtrgm_out"("public"."gtrgm")
  RETURNS "pg_catalog"."cstring" AS '$libdir/pg_trgm', 'gtrgm_out'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_penalty
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_penalty"(internal, internal, internal);
CREATE FUNCTION "public"."gtrgm_penalty"(internal, internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_penalty'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_picksplit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_picksplit"(internal, internal);
CREATE FUNCTION "public"."gtrgm_picksplit"(internal, internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_picksplit'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_same
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_same"("public"."gtrgm", "public"."gtrgm", internal);
CREATE FUNCTION "public"."gtrgm_same"("public"."gtrgm", "public"."gtrgm", internal)
  RETURNS "pg_catalog"."internal" AS '$libdir/pg_trgm', 'gtrgm_same'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for gtrgm_union
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."gtrgm_union"(internal, internal);
CREATE FUNCTION "public"."gtrgm_union"(internal, internal)
  RETURNS "public"."gtrgm" AS '$libdir/pg_trgm', 'gtrgm_union'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pg_stat_statements
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pg_stat_statements"("showtext" bool, OUT "userid" oid, OUT "dbid" oid, OUT "toplevel" bool, OUT "queryid" int8, OUT "query" text, OUT "plans" int8, OUT "total_plan_time" float8, OUT "min_plan_time" float8, OUT "max_plan_time" float8, OUT "mean_plan_time" float8, OUT "stddev_plan_time" float8, OUT "calls" int8, OUT "total_exec_time" float8, OUT "min_exec_time" float8, OUT "max_exec_time" float8, OUT "mean_exec_time" float8, OUT "stddev_exec_time" float8, OUT "rows" int8, OUT "shared_blks_hit" int8, OUT "shared_blks_read" int8, OUT "shared_blks_dirtied" int8, OUT "shared_blks_written" int8, OUT "local_blks_hit" int8, OUT "local_blks_read" int8, OUT "local_blks_dirtied" int8, OUT "local_blks_written" int8, OUT "temp_blks_read" int8, OUT "temp_blks_written" int8, OUT "shared_blk_read_time" float8, OUT "shared_blk_write_time" float8, OUT "local_blk_read_time" float8, OUT "local_blk_write_time" float8, OUT "temp_blk_read_time" float8, OUT "temp_blk_write_time" float8, OUT "wal_records" int8, OUT "wal_fpi" int8, OUT "wal_bytes" numeric, OUT "wal_buffers_full" int8, OUT "jit_functions" int8, OUT "jit_generation_time" float8, OUT "jit_inlining_count" int8, OUT "jit_inlining_time" float8, OUT "jit_optimization_count" int8, OUT "jit_optimization_time" float8, OUT "jit_emission_count" int8, OUT "jit_emission_time" float8, OUT "jit_deform_count" int8, OUT "jit_deform_time" float8, OUT "parallel_workers_to_launch" int8, OUT "parallel_workers_launched" int8, OUT "stats_since" timestamptz, OUT "minmax_stats_since" timestamptz);
CREATE FUNCTION "public"."pg_stat_statements"(IN "showtext" bool, OUT "userid" oid, OUT "dbid" oid, OUT "toplevel" bool, OUT "queryid" int8, OUT "query" text, OUT "plans" int8, OUT "total_plan_time" float8, OUT "min_plan_time" float8, OUT "max_plan_time" float8, OUT "mean_plan_time" float8, OUT "stddev_plan_time" float8, OUT "calls" int8, OUT "total_exec_time" float8, OUT "min_exec_time" float8, OUT "max_exec_time" float8, OUT "mean_exec_time" float8, OUT "stddev_exec_time" float8, OUT "rows" int8, OUT "shared_blks_hit" int8, OUT "shared_blks_read" int8, OUT "shared_blks_dirtied" int8, OUT "shared_blks_written" int8, OUT "local_blks_hit" int8, OUT "local_blks_read" int8, OUT "local_blks_dirtied" int8, OUT "local_blks_written" int8, OUT "temp_blks_read" int8, OUT "temp_blks_written" int8, OUT "shared_blk_read_time" float8, OUT "shared_blk_write_time" float8, OUT "local_blk_read_time" float8, OUT "local_blk_write_time" float8, OUT "temp_blk_read_time" float8, OUT "temp_blk_write_time" float8, OUT "wal_records" int8, OUT "wal_fpi" int8, OUT "wal_bytes" numeric, OUT "wal_buffers_full" int8, OUT "jit_functions" int8, OUT "jit_generation_time" float8, OUT "jit_inlining_count" int8, OUT "jit_inlining_time" float8, OUT "jit_optimization_count" int8, OUT "jit_optimization_time" float8, OUT "jit_emission_count" int8, OUT "jit_emission_time" float8, OUT "jit_deform_count" int8, OUT "jit_deform_time" float8, OUT "parallel_workers_to_launch" int8, OUT "parallel_workers_launched" int8, OUT "stats_since" timestamptz, OUT "minmax_stats_since" timestamptz)
  RETURNS SETOF "pg_catalog"."record" AS '$libdir/pg_stat_statements', 'pg_stat_statements_1_12'
  LANGUAGE c VOLATILE STRICT
  COST 1
  ROWS 1000;

-- ----------------------------
-- Function structure for pg_stat_statements_info
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pg_stat_statements_info"(OUT "dealloc" int8, OUT "stats_reset" timestamptz);
CREATE FUNCTION "public"."pg_stat_statements_info"(OUT "dealloc" int8, OUT "stats_reset" timestamptz)
  RETURNS "pg_catalog"."record" AS '$libdir/pg_stat_statements', 'pg_stat_statements_info'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for pg_stat_statements_reset
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."pg_stat_statements_reset"("userid" oid, "dbid" oid, "queryid" int8, "minmax_only" bool);
CREATE FUNCTION "public"."pg_stat_statements_reset"("userid" oid=0, "dbid" oid=0, "queryid" int8=0, "minmax_only" bool=false)
  RETURNS "pg_catalog"."timestamptz" AS '$libdir/pg_stat_statements', 'pg_stat_statements_reset_1_11'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for set_limit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."set_limit"(float4);
CREATE FUNCTION "public"."set_limit"(float4)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'set_limit'
  LANGUAGE c VOLATILE STRICT
  COST 1;

-- ----------------------------
-- Function structure for show_limit
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."show_limit"();
CREATE FUNCTION "public"."show_limit"()
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'show_limit'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for show_trgm
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."show_trgm"(text);
CREATE FUNCTION "public"."show_trgm"(text)
  RETURNS "pg_catalog"."_text" AS '$libdir/pg_trgm', 'show_trgm'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity"(text, text);
CREATE FUNCTION "public"."similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity_dist
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity_dist"(text, text);
CREATE FUNCTION "public"."similarity_dist"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'similarity_dist'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."similarity_op"(text, text);
CREATE FUNCTION "public"."similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity"(text, text);
CREATE FUNCTION "public"."strict_word_similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_commutator_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_commutator_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'strict_word_similarity_commutator_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_dist_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_dist_commutator_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_dist_commutator_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity_dist_commutator_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_dist_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_dist_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_dist_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'strict_word_similarity_dist_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for strict_word_similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."strict_word_similarity_op"(text, text);
CREATE FUNCTION "public"."strict_word_similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'strict_word_similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity"(text, text);
CREATE FUNCTION "public"."word_similarity"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_commutator_op"(text, text);
CREATE FUNCTION "public"."word_similarity_commutator_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'word_similarity_commutator_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_dist_commutator_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_dist_commutator_op"(text, text);
CREATE FUNCTION "public"."word_similarity_dist_commutator_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity_dist_commutator_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_dist_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_dist_op"(text, text);
CREATE FUNCTION "public"."word_similarity_dist_op"(text, text)
  RETURNS "pg_catalog"."float4" AS '$libdir/pg_trgm', 'word_similarity_dist_op'
  LANGUAGE c IMMUTABLE STRICT
  COST 1;

-- ----------------------------
-- Function structure for word_similarity_op
-- ----------------------------
DROP FUNCTION IF EXISTS "public"."word_similarity_op"(text, text);
CREATE FUNCTION "public"."word_similarity_op"(text, text)
  RETURNS "pg_catalog"."bool" AS '$libdir/pg_trgm', 'word_similarity_op'
  LANGUAGE c STABLE STRICT
  COST 1;

-- ----------------------------
-- View structure for pg_stat_statements_info
-- ----------------------------
DROP VIEW IF EXISTS "public"."pg_stat_statements_info";
CREATE VIEW "public"."pg_stat_statements_info" AS  SELECT dealloc,
    stats_reset
   FROM pg_stat_statements_info() pg_stat_statements_info(dealloc, stats_reset);

-- ----------------------------
-- View structure for pg_stat_statements
-- ----------------------------
DROP VIEW IF EXISTS "public"."pg_stat_statements";
CREATE VIEW "public"."pg_stat_statements" AS  SELECT userid,
    dbid,
    toplevel,
    queryid,
    query,
    plans,
    total_plan_time,
    min_plan_time,
    max_plan_time,
    mean_plan_time,
    stddev_plan_time,
    calls,
    total_exec_time,
    min_exec_time,
    max_exec_time,
    mean_exec_time,
    stddev_exec_time,
    rows,
    shared_blks_hit,
    shared_blks_read,
    shared_blks_dirtied,
    shared_blks_written,
    local_blks_hit,
    local_blks_read,
    local_blks_dirtied,
    local_blks_written,
    temp_blks_read,
    temp_blks_written,
    shared_blk_read_time,
    shared_blk_write_time,
    local_blk_read_time,
    local_blk_write_time,
    temp_blk_read_time,
    temp_blk_write_time,
    wal_records,
    wal_fpi,
    wal_bytes,
    wal_buffers_full,
    jit_functions,
    jit_generation_time,
    jit_inlining_count,
    jit_inlining_time,
    jit_optimization_count,
    jit_optimization_time,
    jit_emission_count,
    jit_emission_time,
    jit_deform_count,
    jit_deform_time,
    parallel_workers_to_launch,
    parallel_workers_launched,
    stats_since,
    minmax_stats_since
   FROM pg_stat_statements(true) pg_stat_statements(userid, dbid, toplevel, queryid, query, plans, total_plan_time, min_plan_time, max_plan_time, mean_plan_time, stddev_plan_time, calls, total_exec_time, min_exec_time, max_exec_time, mean_exec_time, stddev_exec_time, rows, shared_blks_hit, shared_blks_read, shared_blks_dirtied, shared_blks_written, local_blks_hit, local_blks_read, local_blks_dirtied, local_blks_written, temp_blks_read, temp_blks_written, shared_blk_read_time, shared_blk_write_time, local_blk_read_time, local_blk_write_time, temp_blk_read_time, temp_blk_write_time, wal_records, wal_fpi, wal_bytes, wal_buffers_full, jit_functions, jit_generation_time, jit_inlining_count, jit_inlining_time, jit_optimization_count, jit_optimization_time, jit_emission_count, jit_emission_time, jit_deform_count, jit_deform_time, parallel_workers_to_launch, parallel_workers_launched, stats_since, minmax_stats_since);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."analyse_rule_id_seq"
OWNED BY "public"."analyse_rule"."id";
SELECT setval('"public"."analyse_rule_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."analyserules_id_seq"
OWNED BY "public"."analyse_rule"."id";
SELECT setval('"public"."analyserules_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."ban_history_id_seq"
OWNED BY "public"."ban_history"."id";
SELECT setval('"public"."ban_history_id_seq"', 22576986, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."banhistory_id_seq"
OWNED BY "public"."ban_history"."id";
SELECT setval('"public"."banhistory_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."rules_id_seq"
OWNED BY "public"."rules"."id";
SELECT setval('"public"."rules_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."swarm_tracker_id_seq"
OWNED BY "public"."swarm_tracker"."id";
SELECT setval('"public"."swarm_tracker_id_seq"', 32795700, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."swarmtracker_id_seq"
OWNED BY "public"."swarm_tracker"."id";
SELECT setval('"public"."swarmtracker_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."torrent_id_seq"
OWNED BY "public"."torrent"."id";
SELECT setval('"public"."torrent_id_seq"', 192604, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."torrents_id_seq"
OWNED BY "public"."torrent"."id";
SELECT setval('"public"."torrents_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."user_id_seq"
OWNED BY "public"."user"."id";
SELECT setval('"public"."user_id_seq"', 16492, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."user_rel_id_seq"
OWNED BY "public"."user_rel"."id";
SELECT setval('"public"."user_rel_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."user_rel_id_seq1"
OWNED BY "public"."user_rel"."id";
SELECT setval('"public"."user_rel_id_seq1"', 5930, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."userapp_id_seq"
OWNED BY "public"."userapp"."id";
SELECT setval('"public"."userapp_id_seq"', 18396, true);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."userapps_id_seq"
OWNED BY "public"."userapp"."id";
SELECT setval('"public"."userapps_id_seq"', 1, false);

-- ----------------------------
-- Alter sequences owned by
-- ----------------------------
ALTER SEQUENCE "public"."users_id_seq"
OWNED BY "public"."user"."id";
SELECT setval('"public"."users_id_seq"', 1, false);

-- ----------------------------
-- Indexes structure for table analyse_rule
-- ----------------------------
CREATE INDEX "analyse_rule_idx" ON "public"."analyse_rule" USING btree (
  "module" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table analyse_rule
-- ----------------------------
ALTER TABLE "public"."analyse_rule" ADD CONSTRAINT "analyserules_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table ban_history
-- ----------------------------
CREATE INDEX "ban_history_analyse_idx" ON "public"."ban_history" USING btree (
  "insert_time" "pg_catalog"."timestamptz_ops" ASC NULLS LAST,
  "peer_ip" "pg_catalog"."inet_ops" ASC NULLS LAST,
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "peer_client_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_description_trgm" ON "public"."ban_history" USING gin (
  "description" COLLATE "pg_catalog"."default" "public"."gin_trgm_ops"
);
CREATE INDEX "ban_history_insert_time_idx" ON "public"."ban_history" USING btree (
  "insert_time" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
);
CREATE INDEX "ban_history_modulename_idx" ON "public"."ban_history" USING btree (
  "module_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_peer_client_name_idx" ON "public"."ban_history" USING btree (
  "peer_client_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_peer_client_name_trgm" ON "public"."ban_history" USING gin (
  "peer_client_name" COLLATE "pg_catalog"."default" "public"."gin_trgm_ops"
);
CREATE INDEX "ban_history_peer_flags_idx" ON "public"."ban_history" USING btree (
  "peer_flags" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_peer_geoip_idx" ON "public"."ban_history" USING gin (
  "peer_geoip" "pg_catalog"."jsonb_ops"
) WITH (GIN_PENDING_LIST_LIMIT = 4096);
CREATE INDEX "ban_history_peer_id_idx" ON "public"."ban_history" USING btree (
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_peer_ip_idx" ON "public"."ban_history" USING gist (
  "peer_ip" "pg_catalog"."inet_ops"
);
CREATE INDEX "ban_history_populate_time_idx" ON "public"."ban_history" USING btree (
  "populate_time" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
);
CREATE INDEX "ban_history_structured_data" ON "public"."ban_history" USING gin (
  "structured_data" "pg_catalog"."jsonb_ops"
) WITH (GIN_PENDING_LIST_LIMIT = 4096);
CREATE INDEX "ban_history_torrent_id_idx" ON "public"."ban_history" USING btree (
  "torrent_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "ban_history_userapps_idx" ON "public"."ban_history" USING btree (
  "userapps_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table ban_history
-- ----------------------------
ALTER TABLE "public"."ban_history" ADD CONSTRAINT "banhistory_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table client_discovery
-- ----------------------------
CREATE INDEX "client_discovery_found_at_idx" ON "public"."client_discovery" USING btree (
  "found_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE INDEX "client_discovery_peer_client_name_trgm" ON "public"."client_discovery" USING gin (
  "peer_client_name" COLLATE "pg_catalog"."default" "public"."gin_trgm_ops"
);
CREATE INDEX "client_discovery_peer_id_idx" ON "public"."client_discovery" USING btree (
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "client_discovery_unique_idx" ON "public"."client_discovery" USING btree (
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "peer_client_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table client_discovery
-- ----------------------------
ALTER TABLE "public"."client_discovery" ADD CONSTRAINT "clientdiscovery_pkey" PRIMARY KEY ("hash");

-- ----------------------------
-- Primary Key structure for table qrtz_blob_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_blob_triggers" ADD CONSTRAINT "qrtz_blob_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_calendars
-- ----------------------------
ALTER TABLE "public"."qrtz_calendars" ADD CONSTRAINT "qrtz_calendars_pkey" PRIMARY KEY ("sched_name", "calendar_name");

-- ----------------------------
-- Primary Key structure for table qrtz_cron_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_cron_triggers" ADD CONSTRAINT "qrtz_cron_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Indexes structure for table qrtz_fired_triggers
-- ----------------------------
CREATE INDEX "idx_qrtz_ft_inst_job_req_rcvry" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "instance_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "requests_recovery" "pg_catalog"."bool_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_ft_j_g" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_ft_jg" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_ft_t_g" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_ft_tg" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_ft_trig_inst_name" ON "public"."qrtz_fired_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "instance_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table qrtz_fired_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_fired_triggers" ADD CONSTRAINT "qrtz_fired_triggers_pkey" PRIMARY KEY ("sched_name", "entry_id");

-- ----------------------------
-- Indexes structure for table qrtz_job_details
-- ----------------------------
CREATE INDEX "idx_qrtz_j_grp" ON "public"."qrtz_job_details" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_j_req_recovery" ON "public"."qrtz_job_details" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "requests_recovery" "pg_catalog"."bool_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table qrtz_job_details
-- ----------------------------
ALTER TABLE "public"."qrtz_job_details" ADD CONSTRAINT "qrtz_job_details_pkey" PRIMARY KEY ("sched_name", "job_name", "job_group");

-- ----------------------------
-- Primary Key structure for table qrtz_locks
-- ----------------------------
ALTER TABLE "public"."qrtz_locks" ADD CONSTRAINT "qrtz_locks_pkey" PRIMARY KEY ("sched_name", "lock_name");

-- ----------------------------
-- Primary Key structure for table qrtz_paused_trigger_grps
-- ----------------------------
ALTER TABLE "public"."qrtz_paused_trigger_grps" ADD CONSTRAINT "qrtz_paused_trigger_grps_pkey" PRIMARY KEY ("sched_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_scheduler_state
-- ----------------------------
ALTER TABLE "public"."qrtz_scheduler_state" ADD CONSTRAINT "qrtz_scheduler_state_pkey" PRIMARY KEY ("sched_name", "instance_name");

-- ----------------------------
-- Primary Key structure for table qrtz_simple_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simple_triggers" ADD CONSTRAINT "qrtz_simple_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Primary Key structure for table qrtz_simprop_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simprop_triggers" ADD CONSTRAINT "qrtz_simprop_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Indexes structure for table qrtz_triggers
-- ----------------------------
CREATE INDEX "idx_qrtz_t_c" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "calendar_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_g" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_j" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_jg" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "job_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_n_g_state" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_n_state" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_next_fire_time" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "next_fire_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_nft_misfire" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "misfire_instr" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "next_fire_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_nft_st" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "next_fire_time" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_nft_st_misfire" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "misfire_instr" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "next_fire_time" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_nft_st_misfire_grp" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "misfire_instr" "pg_catalog"."int2_ops" ASC NULLS LAST,
  "next_fire_time" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "trigger_group" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "idx_qrtz_t_state" ON "public"."qrtz_triggers" USING btree (
  "sched_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "trigger_state" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table qrtz_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_triggers" ADD CONSTRAINT "qrtz_triggers_pkey" PRIMARY KEY ("sched_name", "trigger_name", "trigger_group");

-- ----------------------------
-- Indexes structure for table rules
-- ----------------------------
CREATE INDEX "rules_type_idx" ON "public"."rules" USING btree (
  "type" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table rules
-- ----------------------------
ALTER TABLE "public"."rules" ADD CONSTRAINT "rules_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table swarm_tracker
-- ----------------------------
CREATE INDEX "swarm_tracker_analyse_idx" ON "public"."swarm_tracker" USING btree (
  "peer_ip" "pg_catalog"."inet_ops" ASC NULLS LAST,
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "peer_client_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "last_time_seen" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_first_time_seen_idx" ON "public"."swarm_tracker" USING btree (
  "first_time_seen" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_last_time_seen_asc_idx" ON "public"."swarm_tracker" USING btree (
  "last_time_seen" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_last_time_seen_idx" ON "public"."swarm_tracker" USING btree (
  "last_time_seen" "pg_catalog"."timestamptz_ops" DESC NULLS FIRST
);
CREATE INDEX "swarm_tracker_peer_client_name_idx" ON "public"."swarm_tracker" USING btree (
  "peer_client_name" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_peer_client_name_trgm" ON "public"."swarm_tracker" USING gin (
  "peer_client_name" COLLATE "pg_catalog"."default" "public"."gin_trgm_ops"
);
CREATE INDEX "swarm_tracker_peer_id_idx" ON "public"."swarm_tracker" USING btree (
  "peer_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_torrent_id_idx" ON "public"."swarm_tracker" USING btree (
  "torrent_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "swarm_tracker_unique_idx" ON "public"."swarm_tracker" USING btree (
  "userapps_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "user_downloader" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "torrent_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "peer_ip" "pg_catalog"."inet_ops" ASC NULLS LAST,
  "peer_port" "pg_catalog"."int4_ops" ASC NULLS LAST
);
CREATE INDEX "swarm_tracker_userapps_id_idx" ON "public"."swarm_tracker" USING btree (
  "userapps_id" "pg_catalog"."int8_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table swarm_tracker
-- ----------------------------
ALTER TABLE "public"."swarm_tracker" ADD CONSTRAINT "swarmtracker_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table torrent
-- ----------------------------
CREATE UNIQUE INDEX "torrent_torrent_identifier_idx" ON "public"."torrent" USING btree (
  "torrent_identifier" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table torrent
-- ----------------------------
ALTER TABLE "public"."torrent" ADD CONSTRAINT "torrents_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table user
-- ----------------------------
CREATE INDEX "user_email_idx" ON "public"."user" USING btree (
  "email" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table user
-- ----------------------------
ALTER TABLE "public"."user" ADD CONSTRAINT "users_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table user_rel
-- ----------------------------
CREATE UNIQUE INDEX "user_rel_unique_idx" ON "public"."user_rel" USING btree (
  "platform" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "platform_user_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table user_rel
-- ----------------------------
ALTER TABLE "public"."user_rel" ADD CONSTRAINT "user_rel_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table userapp
-- ----------------------------
CREATE INDEX "userapp_auth_idx" ON "public"."userapp" USING btree (
  "app_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST,
  "app_secret" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "userapp_installation_id_idx" ON "public"."userapp" USING btree (
  "installation_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);
CREATE INDEX "userapp_owner_idx" ON "public"."userapp" USING btree (
  "owner" "pg_catalog"."int8_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "userapp_unique_idx" ON "public"."userapp" USING btree (
  "app_id" COLLATE "pg_catalog"."default" "pg_catalog"."text_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table userapp
-- ----------------------------
ALTER TABLE "public"."userapp" ADD CONSTRAINT "userapps_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Indexes structure for table userapps_heartbeat
-- ----------------------------
CREATE INDEX "userapps_heartbeat_idx" ON "public"."userapps_heartbeat" USING btree (
  "userapp_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "ip" "pg_catalog"."inet_ops" ASC NULLS LAST,
  "last_seen_at" "pg_catalog"."timestamptz_ops" ASC NULLS LAST
);
CREATE UNIQUE INDEX "userapps_heartbeat_unique_idx" ON "public"."userapps_heartbeat" USING btree (
  "userapp_id" "pg_catalog"."int8_ops" ASC NULLS LAST,
  "ip" "pg_catalog"."inet_ops" ASC NULLS LAST
);

-- ----------------------------
-- Primary Key structure for table userapps_heartbeat
-- ----------------------------
ALTER TABLE "public"."userapps_heartbeat" ADD CONSTRAINT "userapps_heartbeat_pkey" PRIMARY KEY ("id");

-- ----------------------------
-- Foreign Keys structure for table qrtz_blob_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_blob_triggers" ADD CONSTRAINT "qrtz_blob_triggers_sched_name_trigger_name_trigger_group_fkey" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_cron_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_cron_triggers" ADD CONSTRAINT "qrtz_cron_triggers_sched_name_trigger_name_trigger_group_fkey" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_simple_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simple_triggers" ADD CONSTRAINT "qrtz_simple_triggers_sched_name_trigger_name_trigger_group_fkey" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_simprop_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_simprop_triggers" ADD CONSTRAINT "qrtz_simprop_triggers_sched_name_trigger_name_trigger_grou_fkey" FOREIGN KEY ("sched_name", "trigger_name", "trigger_group") REFERENCES "public"."qrtz_triggers" ("sched_name", "trigger_name", "trigger_group") ON DELETE NO ACTION ON UPDATE NO ACTION;

-- ----------------------------
-- Foreign Keys structure for table qrtz_triggers
-- ----------------------------
ALTER TABLE "public"."qrtz_triggers" ADD CONSTRAINT "qrtz_triggers_sched_name_job_name_job_group_fkey" FOREIGN KEY ("sched_name", "job_name", "job_group") REFERENCES "public"."qrtz_job_details" ("sched_name", "job_name", "job_group") ON DELETE NO ACTION ON UPDATE NO ACTION;
