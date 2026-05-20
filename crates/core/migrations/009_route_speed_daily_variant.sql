ALTER TABLE route_speed_daily ADD COLUMN variant_id TEXT NOT NULL DEFAULT '';

ALTER TABLE route_speed_daily DROP CONSTRAINT route_speed_daily_pkey;

ALTER TABLE route_speed_daily ADD PRIMARY KEY (agency_id, route_id, service_date, direction_id, variant_id);
