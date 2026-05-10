use crate::speed::RouteSpeedDayType;
use std::collections::HashMap;

pub struct RouteSpeedChart {
    pub chart_id: String,
    pub chart_json: String,
    pub avg_stop_spacing_m: Option<f64>,
}

impl RouteSpeedChart {
    pub fn avg_stop_spacing_class(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "none",
            Some(m) => classify_by_spacing(m).css_class(),
        }
    }

    pub fn avg_stop_spacing_number(&self) -> String {
        match self.avg_stop_spacing_m {
            None => "—".to_string(),
            Some(m) if m >= 1000.0 => format!("{:.1}", m / 1000.0),
            Some(m) => format!("{:.0}", m),
        }
    }

    pub fn avg_stop_spacing_unit(&self) -> &'static str {
        match self.avg_stop_spacing_m {
            None => "",
            Some(m) if m >= 1000.0 => "km",
            Some(_) => "m",
        }
    }
}

impl RouteSpeedCard {
    pub fn avg_scheduled_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_scheduled_speed_mps)
    }

    pub fn avg_actual_speed_kmh_display(&self) -> String {
        fmt_speed_kmh(self.avg_actual_speed_mps)
    }
}

fn fmt_speed_kmh(mps: Option<f64>) -> String {
    match mps {
        None => "—".to_string(),
        Some(s) => format!("{:.1}", s * 3.6),
    }
}

pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub agency_id: String,
    pub route_id: String,
    pub short_name: String,
    pub long_name: String,
    pub chart: RouteSpeedChart,
    /// Sort key: unweighted mean of all non-None scheduled speed values across
    /// all day types (weekday/Saturday/Sunday) and directions.
    pub avg_scheduled_speed_mps: Option<f64>,
    /// Sort key: unweighted mean of all non-None actual speed values across
    /// all day types and directions. `None` when no actual speed data exists.
    pub avg_actual_speed_mps: Option<f64>,
    pub classification: Option<RouteClass>,
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

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum RouteClass {
    Local,
    Rapid,
    Express,
}

impl RouteClass {
    pub fn label(&self) -> &'static str {
        match self {
            RouteClass::Local => "Local",
            RouteClass::Rapid => "Rapid",
            RouteClass::Express => "Express",
        }
    }

    pub fn speed_range(&self) -> &'static str {
        match self {
            RouteClass::Local => "12-18 km/h",
            RouteClass::Rapid => "18-25 km/h",
            RouteClass::Express => ">25 km/h",
        }
    }

    pub fn css_class(&self) -> &'static str {
        match self {
            RouteClass::Local => "local",
            RouteClass::Rapid => "rapid",
            RouteClass::Express => "express",
        }
    }
}

pub fn classify_by_spacing(avg_m: f64) -> RouteClass {
    if avg_m < 500.0 {
        RouteClass::Local
    } else if avg_m < 1500.0 {
        RouteClass::Rapid
    } else {
        RouteClass::Express
    }
}

fn combined_datasets(rows: &[RouteSpeedDayType]) -> serde_json::Value {
    let weekday_sched = avg_speeds(rows.iter().map(|r| r.weekday_speed_mps));
    let saturday_sched = avg_speeds(rows.iter().map(|r| r.saturday_speed_mps));
    let sunday_sched = avg_speeds(rows.iter().map(|r| r.sunday_speed_mps));
    let weekday_actual = avg_speeds(rows.iter().map(|r| r.actual_weekday_speed_mps));
    let saturday_actual = avg_speeds(rows.iter().map(|r| r.actual_saturday_speed_mps));
    let sunday_actual = avg_speeds(rows.iter().map(|r| r.actual_sunday_speed_mps));
    serde_json::json!([
        {
            "label": "Scheduled",
            "backgroundColor": "#1D4E89",
            "data": [
                speed_kmh_json(weekday_sched),
                speed_kmh_json(saturday_sched),
                speed_kmh_json(sunday_sched),
            ]
        },
        {
            "label": "Actual",
            "backgroundColor": "#C8463A",
            "data": [
                speed_kmh_json(weekday_actual),
                speed_kmh_json(saturday_actual),
                speed_kmh_json(sunday_actual),
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
        let avg_spacing_m = avg_speeds(route_rows.iter().map(|r| r.avg_stop_spacing_m));
        let classification = avg_spacing_m.map(classify_by_spacing);
        let chart = RouteSpeedChart {
            chart_id: format!("chart-{card_idx}"),
            chart_json: serde_json::to_string(&combined_datasets(route_rows))
                .unwrap_or_default(),
            avg_stop_spacing_m: avg_spacing_m,
        };
        cards.push(RouteSpeedCard {
            idx: card_idx,
            agency_name,
            agency_id: first.agency_id.clone(),
            route_id: first.route_id.clone(),
            short_name: first.short_name.clone(),
            long_name: first.long_name.clone(),
            chart,
            avg_scheduled_speed_mps,
            avg_actual_speed_mps,
            classification,
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
    fn build_speed_cards_produces_one_chart_per_route() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(7.5)),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards.len(), 1);
        assert_eq!(cards[0].chart.chart_id, "chart-0");
    }

    #[test]
    fn build_speed_cards_chart_ids_are_unique_across_routes() {
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R2", 0, None),
        ];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_ne!(cards[0].chart.chart_id, cards[1].chart.chart_id);
    }

    #[test]
    fn combined_datasets_includes_scheduled_and_actual() {
        let rows = vec![RouteSpeedDayType {
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
        }];
        let datasets = combined_datasets(&rows);
        let arr = datasets.as_array().unwrap();
        assert_eq!(arr.len(), 2);
        assert_eq!(arr[0]["label"], "Scheduled");
        assert_eq!(arr[1]["label"], "Actual");
    }

    #[test]
    fn combined_datasets_averages_directions_per_day_type() {
        // Two directions: weekday 8.0 and 6.0 → avg 7.0 m/s = 25.2 km/h
        let rows = vec![
            make_row("stm", "R1", 0, Some(8.0)),
            make_row("stm", "R1", 1, Some(6.0)),
        ];
        let datasets = combined_datasets(&rows);
        let arr = datasets.as_array().unwrap();
        let weekday = arr[0]["data"][0].as_f64().unwrap();
        assert!((weekday - 25.2).abs() < 0.05, "expected ~25.2 km/h, got {weekday}");
    }

    #[test]
    fn build_speed_cards_handles_route_ids_not_in_lexicographic_order() {
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
        let rows = vec![make_row("stm", "R1", 0, None)];
        let cards = build_speed_cards(rows, &HashMap::new());
        assert!(cards[0].avg_scheduled_speed_mps.is_none());
    }

    #[test]
    fn build_speed_cards_chart_carries_avg_stop_spacing() {
        let mut row = make_row("stm", "R1", 0, Some(8.0));
        row.avg_stop_spacing_m = Some(450.0);
        let cards = build_speed_cards(vec![row], &HashMap::new());
        assert_eq!(cards[0].chart.avg_stop_spacing_m, Some(450.0));
    }

    #[test]
    fn build_speed_cards_chart_averages_stop_spacing_across_directions() {
        // direction 0: 400 m, direction 1: 600 m → avg 500 m
        let mut row0 = make_row("stm", "R1", 0, Some(8.0));
        row0.avg_stop_spacing_m = Some(400.0);
        let mut row1 = make_row("stm", "R1", 1, Some(7.0));
        row1.avg_stop_spacing_m = Some(600.0);
        let cards = build_speed_cards(vec![row0, row1], &HashMap::new());
        let spacing = cards[0].chart.avg_stop_spacing_m.unwrap();
        assert!((spacing - 500.0).abs() < 0.001, "expected 500.0, got {spacing}");
    }

    #[test]
    fn build_speed_cards_carries_agency_id_and_route_id() {
        let rows = vec![make_row("stm", "R99", 0, None)];
        let names = HashMap::new();
        let cards = build_speed_cards(rows, &names);
        assert_eq!(cards[0].agency_id, "stm");
        assert_eq!(cards[0].route_id, "R99");
    }

    #[test]
    fn classify_local_below_500() {
        assert_eq!(classify_by_spacing(0.0), RouteClass::Local);
        assert_eq!(classify_by_spacing(499.9), RouteClass::Local);
    }

    #[test]
    fn classify_rapid_500_to_1500() {
        assert_eq!(classify_by_spacing(500.0), RouteClass::Rapid);
        assert_eq!(classify_by_spacing(1499.9), RouteClass::Rapid);
    }

    #[test]
    fn classify_express_at_1500_and_above() {
        assert_eq!(classify_by_spacing(1500.0), RouteClass::Express);
        assert_eq!(classify_by_spacing(9999.0), RouteClass::Express);
    }

    #[test]
    fn route_class_label() {
        assert_eq!(RouteClass::Local.label(), "Local");
        assert_eq!(RouteClass::Rapid.label(), "Rapid");
        assert_eq!(RouteClass::Express.label(), "Express");
    }

    #[test]
    fn route_class_css_class() {
        assert_eq!(RouteClass::Local.css_class(), "local");
        assert_eq!(RouteClass::Rapid.css_class(), "rapid");
        assert_eq!(RouteClass::Express.css_class(), "express");
    }

    #[test]
    fn build_speed_cards_sets_classification_from_stop_spacing() {
        // avg spacing of 620 m → Rapid
        let mut row = make_row("stm", "R1", 0, Some(8.0));
        row.avg_stop_spacing_m = Some(620.0);
        let cards = build_speed_cards(vec![row], &HashMap::new());
        assert_eq!(cards[0].classification, Some(RouteClass::Rapid));
    }

    #[test]
    fn build_speed_cards_classification_is_none_when_no_spacing_data() {
        let rows = vec![make_row("stm", "R1", 0, Some(8.0))];
        let cards = build_speed_cards(rows, &HashMap::new());
        assert!(cards[0].classification.is_none());
    }

    #[test]
    fn build_speed_cards_classification_averages_across_directions() {
        // direction 0: 400 m (Local), direction 1: 600 m (Rapid) → avg 500 m → Rapid
        let mut row0 = make_row("stm", "R1", 0, Some(8.0));
        row0.avg_stop_spacing_m = Some(400.0);
        let mut row1 = make_row("stm", "R1", 1, Some(7.0));
        row1.avg_stop_spacing_m = Some(600.0);
        let cards = build_speed_cards(vec![row0, row1], &HashMap::new());
        assert_eq!(cards[0].classification, Some(RouteClass::Rapid));
    }

    fn chart_with_spacing(m: Option<f64>) -> RouteSpeedChart {
        RouteSpeedChart {
            chart_id: "x".into(),
            chart_json: "[]".into(),
            avg_stop_spacing_m: m,
        }
    }

    #[test]
    fn spacing_class_none_when_no_data() {
        assert_eq!(chart_with_spacing(None).avg_stop_spacing_class(), "none");
    }

    #[test]
    fn spacing_class_local_below_500m() {
        assert_eq!(chart_with_spacing(Some(300.0)).avg_stop_spacing_class(), "local");
    }

    #[test]
    fn spacing_class_rapid_500_to_1500m() {
        assert_eq!(chart_with_spacing(Some(800.0)).avg_stop_spacing_class(), "rapid");
    }

    #[test]
    fn spacing_class_express_1500m_and_above() {
        assert_eq!(chart_with_spacing(Some(2000.0)).avg_stop_spacing_class(), "express");
    }

    #[test]
    fn spacing_number_none() {
        assert_eq!(chart_with_spacing(None).avg_stop_spacing_number(), "—");
    }

    #[test]
    fn spacing_number_metres() {
        assert_eq!(chart_with_spacing(Some(342.0)).avg_stop_spacing_number(), "342");
    }

    #[test]
    fn spacing_number_kilometres() {
        assert_eq!(chart_with_spacing(Some(1200.0)).avg_stop_spacing_number(), "1.2");
    }

    #[test]
    fn spacing_unit_none() {
        assert_eq!(chart_with_spacing(None).avg_stop_spacing_unit(), "");
    }

    #[test]
    fn spacing_unit_metres() {
        assert_eq!(chart_with_spacing(Some(342.0)).avg_stop_spacing_unit(), "m");
    }

    #[test]
    fn spacing_unit_kilometres() {
        assert_eq!(chart_with_spacing(Some(1200.0)).avg_stop_spacing_unit(), "km");
    }

    #[test]
    fn scheduled_speed_display_formats_kmh_one_decimal() {
        // 10.0 m/s = 36.0 km/h
        let card = RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
            avg_scheduled_speed_mps: Some(10.0),
            avg_actual_speed_mps: None,
            classification: None,
        };
        assert_eq!(card.avg_scheduled_speed_kmh_display(), "36.0");
    }

    #[test]
    fn scheduled_speed_display_dash_when_none() {
        let card = RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
            classification: None,
        };
        assert_eq!(card.avg_scheduled_speed_kmh_display(), "—");
    }

    #[test]
    fn actual_speed_display_formats_kmh_one_decimal() {
        // 5.0 m/s = 18.0 km/h
        let card = RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: Some(5.0),
            classification: None,
        };
        assert_eq!(card.avg_actual_speed_kmh_display(), "18.0");
    }

    #[test]
    fn actual_speed_display_dash_when_none() {
        let card = RouteSpeedCard {
            idx: 0,
            agency_name: "A".into(),
            agency_id: "a".into(),
            route_id: "R1".into(),
            short_name: "1".into(),
            long_name: "Route 1".into(),
            chart: RouteSpeedChart { chart_id: String::new(), chart_json: "[]".into(), avg_stop_spacing_m: None },
            avg_scheduled_speed_mps: None,
            avg_actual_speed_mps: None,
            classification: None,
        };
        assert_eq!(card.avg_actual_speed_kmh_display(), "—");
    }
}
