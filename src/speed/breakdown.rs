// src/speed/breakdown.rs

use anyhow::Result;
use crate::db::Database;

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum FactorKind {
    DwellExcess,
    Bunching,
    RunningTimeLoss,
}

impl FactorKind {
    pub fn label(self) -> &'static str {
        match self {
            FactorKind::DwellExcess => "Dwell time at stops",
            FactorKind::Bunching => "Bunching",
            FactorKind::RunningTimeLoss => "Running time loss",
        }
    }

    pub fn color(self) -> &'static str {
        match self {
            FactorKind::DwellExcess => "#e74c3c",
            FactorKind::Bunching => "#27ae60",
            FactorKind::RunningTimeLoss => "#e67e22",
        }
    }
}

pub struct DeficitFactor {
    pub kind: FactorKind,
    pub delta_mps: f64,
    pub from_mps: f64,
    pub to_mps: f64,
    pub detail: String,
}

impl DeficitFactor {
    pub fn delta_kmh(&self) -> f64 {
        self.delta_mps * 3.6
    }

    pub fn from_kmh(&self) -> f64 {
        self.from_mps * 3.6
    }

    pub fn to_kmh(&self) -> f64 {
        self.to_mps * 3.6
    }
}

pub struct SpeedDeficitBreakdown {
    pub scheduled_speed_mps: f64,
    pub actual_speed_mps: f64,
    pub factors: Vec<DeficitFactor>,
    pub unexplained_mps: f64,
}

impl SpeedDeficitBreakdown {
    pub fn scheduled_speed_kmh(&self) -> f64 {
        self.scheduled_speed_mps * 3.6
    }

    pub fn actual_speed_kmh(&self) -> f64 {
        self.actual_speed_mps * 3.6
    }

    pub fn has_deficit(&self) -> bool {
        self.scheduled_speed_mps > self.actual_speed_mps + 0.1
    }

    pub fn chart_json(&self) -> String {
        let mut labels: Vec<serde_json::Value> = vec![serde_json::json!("Scheduled")];
        let mut data: Vec<serde_json::Value> =
            vec![serde_json::json!([0.0, self.scheduled_speed_kmh()])];
        let mut colors: Vec<serde_json::Value> = vec![serde_json::json!("#2980b9")];

        for factor in &self.factors {
            labels.push(serde_json::json!(format!("− {}", factor.kind.label())));
            data.push(serde_json::json!([factor.to_kmh(), factor.from_kmh()]));
            colors.push(serde_json::json!(factor.kind.color()));
        }

        if self.unexplained_mps.abs() > 0.05 {
            let from_kmh = self
                .factors
                .last()
                .map(|f| f.to_kmh())
                .unwrap_or_else(|| self.scheduled_speed_kmh());
            labels.push(serde_json::json!("− Other"));
            data.push(serde_json::json!([self.actual_speed_kmh(), from_kmh]));
            colors.push(serde_json::json!("#aaaaaa"));
        }

        labels.push(serde_json::json!("Actual"));
        data.push(serde_json::json!([0.0, self.actual_speed_kmh()]));
        colors.push(serde_json::json!("#e67e22"));

        serde_json::to_string(&serde_json::json!({
            "labels": labels,
            "datasets": [{
                "data": data,
                "backgroundColor": colors,
                "borderWidth": 0,
            }]
        }))
        .unwrap_or_default()
    }
}

pub async fn compute_speed_deficit_breakdown(
    _db: &Database,
    _agency_id: &str,
    _route_id: &str,
    _direction_id: i64,
    _days: i64,
) -> Result<Option<SpeedDeficitBreakdown>> {
    Ok(None)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn deficit_factor_delta_kmh_converts_mps() {
        let f = DeficitFactor {
            kind: FactorKind::DwellExcess,
            delta_mps: -1.0,
            from_mps: 5.0,
            to_mps: 4.0,
            detail: "test".to_string(),
        };
        assert!((f.delta_kmh() - (-3.6)).abs() < 0.001);
        assert!((f.from_kmh() - 18.0).abs() < 0.001);
        assert!((f.to_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn breakdown_has_deficit_true_when_gap_exceeds_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 6.0,
            actual_speed_mps: 4.5,
            factors: vec![],
            unexplained_mps: 1.5,
        };
        assert!(bd.has_deficit());
    }

    #[test]
    fn breakdown_has_deficit_false_when_gap_below_threshold() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.95,
            factors: vec![],
            unexplained_mps: 0.05,
        };
        assert!(!bd.has_deficit());
    }

    #[test]
    fn breakdown_scheduled_and_actual_kmh_conversion() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![],
            unexplained_mps: 1.0,
        };
        assert!((bd.scheduled_speed_kmh() - 18.0).abs() < 0.001);
        assert!((bd.actual_speed_kmh() - 14.4).abs() < 0.001);
    }

    #[test]
    fn chart_json_is_valid_json_with_labels_and_datasets() {
        let bd = SpeedDeficitBreakdown {
            scheduled_speed_mps: 5.0,
            actual_speed_mps: 4.0,
            factors: vec![DeficitFactor {
                kind: FactorKind::DwellExcess,
                delta_mps: -0.5,
                from_mps: 5.0,
                to_mps: 4.5,
                detail: "avg 30 s/stop".to_string(),
            }],
            unexplained_mps: 0.5,
        };
        let json = bd.chart_json();
        let v: serde_json::Value = serde_json::from_str(&json).unwrap();
        assert!(v["labels"].is_array());
        assert!(v["datasets"].is_array());
    }
}
