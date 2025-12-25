DO $$
BEGIN
  IF to_regclass('public.route_variants') IS NOT NULL THEN
    IF to_regclass('public.stops') IS NOT NULL THEN
      WITH expanded AS (
        SELECT
          rv.id AS variant_id,
          s.name AS stop_name,
          x.gtfs_stop_id AS gtfs_stop_id,
          x.ordinality AS seq
        FROM route_variants rv
        JOIN routes r ON r.id = rv.route_id
        JOIN agencies a ON a.agency_onestop_id = r.agency_onestop_id
        JOIN feeds f ON f.feed_onestop_id = a.feed_onestop_id
        JOIN LATERAL unnest(string_to_array(rv.stop_pattern, '|')) WITH ORDINALITY AS x(gtfs_stop_id, ordinality) ON true
        LEFT JOIN stops s
          ON s.feed_onestop_id = f.feed_onestop_id
         AND s.gtfs_stop_id = x.gtfs_stop_id
      ),
      names AS (
        SELECT
          variant_id,
          string_agg(COALESCE(stop_name, gtfs_stop_id), '|' ORDER BY seq) AS stop_name_pattern
        FROM expanded
        GROUP BY variant_id
      )
      UPDATE route_variants rv
      SET stop_name_pattern = names.stop_name_pattern
      FROM names
      WHERE rv.id = names.variant_id
        AND (rv.stop_name_pattern IS NULL OR rv.stop_name_pattern = '');
    ELSE
      UPDATE route_variants
      SET stop_name_pattern = stop_pattern
      WHERE stop_name_pattern IS NULL OR stop_name_pattern = '';
    END IF;
  END IF;
END $$;
