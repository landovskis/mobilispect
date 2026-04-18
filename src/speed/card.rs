use crate::speed::RouteSpeedDayType;
use std::collections::HashMap;

pub struct DirectionSpeedChart {
    pub chart_id: String,
    pub title: String,
    pub chart_json: String,
    pub avg_stop_spacing_m: Option<f64>,
}

impl DirectionSpeedChart {
    pub fn avg_stop_spacing_display(&self) -> String {
        match self.avg_stop_spacing_m {
            None => "—".to_string(),
            Some(m) if m >= 1000.0 => format!("{:.1} km", m / 1000.0),
            Some(m) => format!("{:.0} m", m),
        }
    }
}

pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub short_name: String,
    pub long_name: String,
    pub charts: Vec<DirectionSpeedChart>,
    /// Sort key: unweighted mean of all non-None scheduled speed values across
    /// all day types (weekday/Saturday/Sunday) and directions. Routes with fewer
    /// non-None slots contribute proportionally less weight.
    pub avg_scheduled_speed_mps: Option<f64>,
    /// Sort key: unweighted mean of all non-None actual speed values across
    /// all day types and directions. `None` when no actual speed data exists.
    pub avg_actual_speed_mps: Option<f64>,
}

fn speed_kmh_json(mps: Option<f64>) -> serde_json::Value {
    match mps {
        Some(s) => {
            let kmh = (s * 3.6 * 10.0).round() / 10.0;
            serde_json::Number::from_f64(kmh)
                .map(serde_json::Value::Number)
                .unwrap_or(serde_json::Value::Null)
        }
        None => serde_json::Value::Null,
    }
}

/// Returns the unweighted mean of all `Some` values in `iter`, or `None` if all are `None`.
fn avg_speeds(iter: impl Iterator<Item = Option<f64>>) -> Option<f64> {
    let (sum, count) = iter
        .flatten()
        .fold((0.0_f64, 0usize), |(s, n), v| (s + v, n + 1));
    (count > 0).then(|| sum / count as f64)
}

fn direction_datasets(row: &RouteSpeedDayType) -> serde_json::Value {
    serde_json::json!([
        {
            "label": "Scheduled",
            "backgroundColor": "#2980b9",
            "data": [
                speed_kmh_json(row.weekday_speed_mps),
                speed_kmh_json(row.saturday_speed_mps),
                speed_kmh_json(row.sunday_speed_mps),
            ]
        },
        {
            "label": "Actual",
            "backgroundColor": "#e67e22",
            "data": [
                speed_kmh_json(row.actual_weekday_speed_mps),
                speed_kmh_json(row.actual_saturday_speed_mps),
                speed_kmh_json(row.actual_sunday_speed_mps),
            ]
        },
    ])
}

pub fn build_speed_cards(
    rows: Vec<RouteSpeedDayType>,
    agency_names: &HashMap<String, String>,
) -> Vec<RouteSpeedCard> {
    let mut cards: Vec<RouteSpeedCard> = Vec::new();
    for route_rows in rows.chunk_by(|a, b| a.agency_id == b.agency_id && a.route_id == b.route_id) {
        let first = &route_rows[0];
        let agency_name = agency_names
            .get(&first.agency_id)
            .cloned()
            .unwrap_or_else(|| first.agency_id.clone());
        let card_idx = cards.len();
        let charts: Vec<DirectionSpeedChart> = route_rows
            .iter()
            .map(|row| DirectionSpeedChart {
                chart_id: format!("chart-{card_idx}-{}", row.direction_id),
                title: row
                    .last_stop_name
                    .clone()
                    .unwrap_or_else(|| super::direction_label(row.direction_id).to_string()),
                chart_json: serde_json::to_string(&direction_datasets(row)).unwrap_or_default(),
                avg_stop_spacing_m: row.avg_stop_spacing_m,
            })
            .collect();
        let avg_scheduled_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
            [
                r.weekday_speed_mps,
                r.saturday_speed_mps,
                r.sunday_speed_mps,
            ]
        }));
        let avg_actual_speed_mps = avg_speeds(route_rows.iter().flat_map(|r| {
            [
                r.actual_weekday_speed_mps,
                r.actual_saturday_speed_mps,
                r.actual_sunday_speed_mps,
            ]
        }));
        cards.push(RouteSpeedCard {
            idx: card_idx,
            agency_name,
            short_name: first.short_name.clone(),
            long_name: first.long_name.clone(),
            charts,
            avg_scheduled_speed_mps,
            avg_actual_speed_mps,
        });
    }
    cards
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_row(
        agency_id: &str,
        route_id: &str,
        direction_id: i64,
        weekday: Option<f64>,
    ) -> RouteSpeedDayType {
        RouteSpeedDayType {
            agency_id: agency_id.to_string(),
            route_id: route_id.to_string(),
            short_name: route_id.to_string(),
            long_name: format!("Route {route_id}"),
            direction_id,
            weekday_speed_mps: weekday,
            saturday_speed_mps: None,
            sunday_speed_mps: None,
            actual_weekday_speed_mps: None,
            actual_saturday_speed_mps: None,
            actual_sunday_speed_mps: None,
            last_stop_name: None,
            avg_stop_spacing_m: None,
        }
    }

    #[test]
    fn speed_kmh_json_converts_mps_to_kmh() {
        // 10 m/s = 36 km/h
        let v = speed_kmh_json(Some(10.0));
        assert_eq!(v, serde_json::json!(36.0));
    }

    #[test]
    fn speed_kmh_json_none_returns_null() {
        assert_eq!(speed_kmh_json(None), serde_json::Value::Null);
    }

    #[test]
    fn build_speed_cards_groups_directions_into_one_card() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(7.5)),
        ];
        let mut names = HashMap::new();
        names.insert("stm".to_string(), "STM".to_string());
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].agency_name, "STM");
        assert_eq!(cards[0].short_name, "R1");
        assert_eq!(cards[0].idx, 0);
    }

    #[test]
    fn build_speed_cards_assigns_sequential_idx() {
        let rows = vec![
            make_row("stm", "R1", 0, None),
            make_row("stm", "R2", 0, None),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].idx, 0);
        assert_eq!(cards[1].idx, 1);
    }

    #[test]
    fn build_speed_cards_falls_back_to_agency_id_when_name_missing() {
        let rows = vec![make_row("unknown", "R1", 0, None)];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].agency_name, "unknown");
    }

    #[test]
    fn build_speed_cards_empty_input_returns_empty() {
        let cards = build_speed_cards(vec![], &HashMap::new());
        assert!(cards.is_empty());
    }

    #[test]
    fn direction_datasets_includes_scheduled_and_actual() {
        let row = RouteSpeedDayType {
            agency_id: "stm".to_string(),
            route_id: "R1".to_string(),
            short_name: "R1".to_string(),
            long_name: "Route R1".to_string(),
            direction_id: 0,
            weekday_speed_mps: Some(10.0),
            saturday_speed_mps: None,
            sunday_speed_mps: None,
            actual_weekday_speed_mps: Some(9.0),
            actual_saturday_speed_mps: None,
            actual_sunday_speed_mps: None,
            last_stop_name: None,
            avg_stop_spacing_m: None,
        };
        let datasets = direction_datasets(&row);
        let arr = datasets.as_array().unwrap();
        assert_eq!(arr.len(), 2);
        assert_eq!(arr[0]["label"], "Scheduled");
        assert_eq!(arr[0]["backgroundColor"], "#2980b9");
        assert_eq!(arr[1]["label"], "Actual");
        assert_eq!(arr[1]["backgroundColor"], "#e67e22");
    }

    #[test]
    fn build_speed_cards_single_direction_produces_one_chart() {
        let rows = vec![make_row("stm", "R1", 0, Some(8.0))];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].charts.len(), 1);
        assert_eq!(cards[0].charts[0].title, "Outbound");
    }

    #[test]
    fn build_speed_cards_both_directions_produce_two_charts() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(7.5)),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].charts.len(), 2);
        assert_eq!(cards[0].charts[0].title, "Outbound");
        assert_eq!(cards[0].charts[1].title, "Inbound");
    }

    #[test]
    fn build_speed_cards_chart_ids_are_unique() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(7.5)),
            make_row("stm", "R2", 0, None),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        let ids: Vec<&str> = cards
            .iter()
            .flat_map(|c| c.charts.iter().map(|ch| ch.chart_id.as_str()))
            .collect();
        let unique: std::collections::HashSet<_> = ids.iter().collect();
        assert_eq!(ids.len(), unique.len(), "chart IDs must be globally unique");
    }

    #[test]
    fn build_speed_cards_handles_route_ids_not_in_lexicographic_order() {
        // SQL sorts by short_name, not route_id. Route "uuid-z" (short_name "1") comes
        // before "uuid-a" (short_name "2") even though "uuid-z" > "uuid-a" lexicographically.
        let rows = vec![
            make_row("stm", "uuid-z", 0, Some(8.0)),
            make_row("stm", "uuid-a", 0, Some(7.0)),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards.len(), 2);
    }

    #[test]
    fn build_speed_cards_computes_avg_scheduled_speed() {
        // Two directions, weekday only. avg = (8.0 + 6.0) / 2 = 7.0 m/s
        let rows = vec![
            RouteSpeedDayType {
                agency_id: "stm".into(),
                route_id: "R1".into(),
                short_name: "R1".into(),
                long_name: "Route R1".into(),
                direction_id: 0,
                weekday_speed_mps: Some(8.0),
                saturday_speed_mps: None,
                sunday_speed_mps: None,
                actual_weekday_speed_mps: None,
                actual_saturday_speed_mps: None,
                actual_sunday_speed_mps: None,
                last_stop_name: None,
                avg_stop_spacing_m: None,
            },
            RouteSpeedDayType {
                agency_id: "stm".into(),
                route_id: "R1".into(),
                short_name: "R1".into(),
                long_name: "Route R1".into(),
                direction_id: 1,
                weekday_speed_mps: Some(6.0),
                saturday_speed_mps: None,
                sunday_speed_mps: None,
                actual_weekday_speed_mps: None,
                actual_saturday_speed_mps: None,
                actual_sunday_speed_mps: None,
                last_stop_name: None,
                avg_stop_spacing_m: None,
            },
        ];
        let cards = build_speed_cards(rows, &HashMap::new());
        let avg = cards[0].avg_scheduled_speed_mps.unwrap();
        assert!((avg - 7.0).abs() < 0.001, "expected 7.0, got {avg}");
    }

    #[test]
    fn build_speed_cards_avg_scheduled_uses_all_day_types() {
        // One direction, three day types: (9.0 + 6.0 + 3.0) / 3 = 6.0
        let rows = vec![RouteSpeedDayType {
            agency_id: "stm".into(),
            route_id: "R1".into(),
            short_name: "R1".into(),
            long_name: "Route R1".into(),
            direction_id: 0,
            weekday_speed_mps: Some(9.0),
            saturday_speed_mps: Some(6.0),
            sunday_speed_mps: Some(3.0),
            actual_weekday_speed_mps: None,
            actual_saturday_speed_mps: None,
            actual_sunday_speed_mps: None,
            last_stop_name: None,
            avg_stop_spacing_m: None,
        }];
        let cards = build_speed_cards(rows, &HashMap::new());
        let avg = cards[0].avg_scheduled_speed_mps.unwrap();
        assert!((avg - 6.0).abs() < 0.001, "expected 6.0, got {avg}");
    }

    #[test]
    fn build_speed_cards_avg_actual_is_none_when_no_actual_data() {
        let rows = vec![make_row("stm", "R1", 0, Some(8.0))];
        let cards = build_speed_cards(rows, &HashMap::new());
        assert!(cards[0].avg_actual_speed_mps.is_none());
    }

    #[test]
    fn build_speed_cards_computes_avg_actual_speed() {
        // actual weekday = 5.0, actual saturday = 7.0 → avg = 6.0
        let rows = vec![RouteSpeedDayType {
            agency_id: "stm".into(),
            route_id: "R1".into(),
            short_name: "R1".into(),
            long_name: "Route R1".into(),
            direction_id: 0,
            weekday_speed_mps: Some(8.0),
            saturday_speed_mps: None,
            sunday_speed_mps: None,
            actual_weekday_speed_mps: Some(5.0),
            actual_saturday_speed_mps: Some(7.0),
            actual_sunday_speed_mps: None,
            last_stop_name: None,
            avg_stop_spacing_m: None,
        }];
        let cards = build_speed_cards(rows, &HashMap::new());
        let avg = cards[0].avg_actual_speed_mps.unwrap();
        assert!((avg - 6.0).abs() < 0.001, "expected 6.0, got {avg}");
    }

    #[test]
    fn build_speed_cards_avg_scheduled_is_none_when_all_scheduled_speeds_none() {
        // make_row sets weekday to None and saturday/sunday are always None
        let rows = vec![make_row("stm", "R1", 0, None)];
        let cards = build_speed_cards(rows, &HashMap::new());
        assert!(cards[0].avg_scheduled_speed_mps.is_none());
    }

    #[test]
    fn build_speed_cards_chart_carries_avg_stop_spacing() {
        let mut row = make_row("stm", "R1", 0, Some(8.0));
        row.avg_stop_spacing_m = Some(450.0);
        let cards = build_speed_cards(vec![row], &HashMap::new());
        assert_eq!(cards[0].charts[0].avg_stop_spacing_m, Some(450.0));
    }

    #[test]
    fn build_speed_cards_uses_last_stop_name_as_chart_title() {
        let rows = vec![RouteSpeedDayType {
            agency_id: "stm".into(),
            route_id: "R1".into(),
            short_name: "R1".into(),
            long_name: "Route R1".into(),
            direction_id: 0,
            weekday_speed_mps: Some(8.0),
            saturday_speed_mps: None,
            sunday_speed_mps: None,
            actual_weekday_speed_mps: None,
            actual_saturday_speed_mps: None,
            actual_sunday_speed_mps: None,
            last_stop_name: Some("Downtown".to_string()),
            avg_stop_spacing_m: None,
        }];
        let cards = build_speed_cards(rows, &HashMap::new());
        assert_eq!(cards[0].charts[0].title, "Downtown");
    }
}
