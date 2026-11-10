# Legacy SQL archive

The files in this directory are historical reference migrations from before Flyway was enabled.
They must not be used for normal installations or upgrades.

Active migrations live in `src/main/resources/db/migration` and run automatically at startup.

The only supported manual use is an emergency upgrade of a very old database whose
`ban_history` table is still partitioned by pg_partman rather than converted to a
TimescaleDB hypertable. In that case, complete the historical `v20` through `v22` conversion
before starting a Flyway-enabled build. The application refuses to baseline an unconverted
schema because replaying those data-rewriting scripts automatically would be unsafe.
