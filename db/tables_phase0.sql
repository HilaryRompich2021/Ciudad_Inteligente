-- TODO: crear tablas events_raw y alerts en el schema del equipo

CREATE TABLE events (
  event_id        uuid PRIMARY KEY,
  event_type      text NOT NULL,
  event_version   text NOT NULL,
  producer        text NOT NULL,
  source          text NOT NULL,
  correlation_id  uuid,
  trace_id        uuid,
  partition_key   text NOT NULL,
  ts_utc          timestamptz NOT NULL,
  zone            text,
  geo_lat         numeric,
  geo_lon         numeric,
  severity        text,
  payload         jsonb NOT NULL
);

CREATE INDEX idx_events_ts    ON events (ts_utc);
CREATE INDEX idx_events_type  ON events (event_type);
CREATE INDEX idx_events_zone  ON events (zone);
CREATE INDEX idx_events_pkey  ON events (partition_key);

CREATE TABLE alerts (
  alert_id        uuid PRIMARY KEY,
  correlation_id  uuid,
  type            text NOT NULL,
  score           numeric,
  zone            text,
  window_start    timestamptz,
  window_end      timestamptz,
  evidence        jsonb,    -- array of event_ids w/ minimal context
  created_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_alerts_ts  ON alerts (created_at);
CREATE INDEX idx_alerts_zone ON alerts (zone);
