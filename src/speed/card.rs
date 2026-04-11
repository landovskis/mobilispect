use std::collections::HashMap;
use crate::speed::RouteSpeedDayType;

pub struct RouteSpeedCard {
    pub idx: usize,
    pub agency_name: String,
    pub short_name: String,
    pub long_name: String,
    pub chart_json: String,
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

fn day_type_data(row: Option<&RouteSpeedDayType>) -> serde_json::Value {
    match row {
        Some(r) => serde_json::json!([
            speed_kmh_json(r.weekday_speed_mps),
            speed_kmh_json(r.saturday_speed_mps),
            speed_kmh_json(r.sunday_speed_mps),
        ]),
        None => serde_json::json!([null, null, null]),
    }
}

pub fn build_speed_cards(
    rows: Vec<RouteSpeedDayType>,
    agency_names: &HashMap<String, String>,
) -> Vec<RouteSpeedCard> {
    let mut cards: Vec<RouteSpeedCard> = Vec::new();
    let mut i = 0;
    while i < rows.len() {
        let agency_id = rows[i].agency_id.clone();
        let route_id = rows[i].route_id.clone();
        let mut j = i;
        while j < rows.len() && rows[j].agency_id == agency_id && rows[j].route_id == route_id {
            j += 1;
        }
        let route_rows = &rows[i..j];
        let first = &rows[i];
        let agency_name = agency_names
            .get(&first.agency_id)
            .cloned()
            .unwrap_or_else(|| first.agency_id.clone());
        let outbound = route_rows.iter().find(|r| r.direction_id == 0);
        let inbound = route_rows.iter().find(|r| r.direction_id == 1);
        let datasets = serde_json::json!([
            { "label": "Outbound", "data": day_type_data(outbound), "backgroundColor": "#2980b9" },
            { "label": "Inbound",  "data": day_type_data(inbound),  "backgroundColor": "#27ae60" },
        ]);
        cards.push(RouteSpeedCard {
            idx: cards.len(),
            agency_name,
            short_name: first.short_name.clone(),
            long_name: first.long_name.clone(),
            chart_json: serde_json::to_string(&datasets).unwrap_or_default(),
        });
        i = j;
    }
    cards
}

#[cfg(test)]
mod tests {
    use super::*;

    fn make_row(agency_id: &str, route_id: &str, direction_id: i64, weekday: Option<f64>) -> RouteSpeedDayType {
        RouteSpeedDayType {
            agency_id: agency_id.to_string(),
            route_id: route_id.to_string(),
            short_name: route_id.to_string(),
            long_name: format!("Route {route_id}"),
            direction_id,
            weekday_speed_mps: weekday,
            saturday_speed_mps: None,
            sunday_speed_mps: None,
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
}
