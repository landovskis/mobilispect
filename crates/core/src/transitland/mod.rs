use crate::ids::{AgencyId, RouteId, StationId, StopId};

const DEFAULT_BASE_URL: &str = "https://transit.land/api/v2/rest";

pub struct TransitlandClient {
    http: reqwest::Client,
    api_key: Option<String>,
    base_url: String,
}

impl TransitlandClient {
    pub fn new(api_key: Option<String>) -> Self {
        Self {
            http: reqwest::Client::new(),
            api_key,
            base_url: DEFAULT_BASE_URL.to_string(),
        }
    }

    #[cfg(test)]
    pub(crate) fn with_base_url(api_key: Option<String>, base_url: String) -> Self {
        Self {
            http: reqwest::Client::new(),
            api_key,
            base_url,
        }
    }

    fn apply_auth(&self, request: reqwest::RequestBuilder) -> reqwest::RequestBuilder {
        if let Some(key) = &self.api_key {
            request.header("Apikey", key)
        } else {
            request
        }
    }

    /// Resolve a GTFS agency_id within a feed to a Transitland operator Onestop ID.
    /// Returns `None` if not found.
    pub async fn resolve_agency(
        &self,
        gtfs_agency_id: &str,
        feed_onestop_id: &str,
    ) -> anyhow::Result<Option<AgencyId>> {
        let url = format!("{}/agencies.json", self.base_url);
        let request = self.http.get(&url).query(&[
            ("gtfs_agency_id", gtfs_agency_id),
            ("feed_onestop_id", feed_onestop_id),
            ("per_page", "1"),
        ]);
        let request = self.apply_auth(request);
        let response = request.send().await?.error_for_status()?;
        let body: AgenciesResponse = response.json().await?;
        Ok(body
            .agencies
            .into_iter()
            .next()
            .map(|r| AgencyId::from(r.onestop_id)))
    }

    /// Resolve a GTFS route_id within a feed to a Transitland route Onestop ID.
    /// Returns `None` if not found.
    pub async fn resolve_route(
        &self,
        gtfs_route_id: &str,
        feed_onestop_id: &str,
    ) -> anyhow::Result<Option<RouteId>> {
        let url = format!("{}/routes.json", self.base_url);
        let request = self.http.get(&url).query(&[
            ("route_id", gtfs_route_id),
            ("feed_onestop_id", feed_onestop_id),
            ("per_page", "1"),
        ]);
        let request = self.apply_auth(request);
        let response = request.send().await?.error_for_status()?;
        let body: RoutesResponse = response.json().await?;
        Ok(body
            .routes
            .into_iter()
            .next()
            .map(|r| RouteId::from(r.onestop_id)))
    }

    pub async fn discover_feeds_for_city(&self, city: &str) -> anyhow::Result<Vec<DiscoveredFeed>> {
        let url = format!("{}/operators.json", self.base_url);
        let req = self
            .http
            .get(&url)
            .query(&[("city_name", city), ("per_page", "50")]);
        let body: OperatorsResponse = self
            .apply_auth(req)
            .send()
            .await?
            .error_for_status()?
            .json()
            .await?;

        let feed_ids = unique_feed_ids(&body.operators);

        let mut discovered = Vec::new();
        for (feed_id, timezone) in feed_ids {
            let url = format!("{}/feeds.json", self.base_url);
            let req = self
                .http
                .get(&url)
                .query(&[("onestop_id", feed_id.as_str()), ("per_page", "1")]);
            let body: FeedsResponse = self
                .apply_auth(req)
                .send()
                .await?
                .error_for_status()?
                .json()
                .await?;
            if let Some(record) = body.feeds.into_iter().next() {
                let urls = record.urls.unwrap_or(FeedUrls {
                    static_current: None,
                    realtime_vehicle_positions: None,
                    realtime_trip_updates: None,
                });
                if let Some(static_url) = urls.static_current {
                    discovered.push(DiscoveredFeed {
                        onestop_id: record.onestop_id,
                        name: record.name.unwrap_or_else(|| feed_id.clone()),
                        gtfs_static_url: static_url,
                        gtfs_rt_vehicle_positions_url: urls.realtime_vehicle_positions,
                        gtfs_rt_trip_updates_url: urls.realtime_trip_updates,
                        timezone,
                    });
                }
            }
        }
        Ok(discovered)
    }

    /// Resolve a GTFS stop_id within a feed to a Transitland stop/station Onestop ID.
    /// Returns `(stop_onestop_id, parent_station_onestop_id)`.
    /// `parent_station_onestop_id` is `Some` when the stop has a parent station.
    /// Returns `None` if not found.
    pub async fn resolve_stop(
        &self,
        gtfs_stop_id: &str,
        feed_onestop_id: &str,
    ) -> anyhow::Result<Option<(StopId, Option<StationId>)>> {
        let url = format!("{}/stops.json", self.base_url);
        let request = self.http.get(&url).query(&[
            ("stop_id", gtfs_stop_id),
            ("feed_onestop_id", feed_onestop_id),
            ("per_page", "1"),
        ]);
        let request = self.apply_auth(request);
        let response = request.send().await?.error_for_status()?;
        let body: StopsResponse = response.json().await?;
        Ok(body.stops.into_iter().next().map(|r| {
            let stop_id = StopId::from(r.onestop_id);
            let station_id = r.parent.map(|s| StationId::from(s.onestop_id));
            (stop_id, station_id)
        }))
    }

    pub async fn resolve_stops_for_feed(
        &self,
        feed_onestop_id: &str,
    ) -> anyhow::Result<std::collections::HashMap<String, (StopId, Option<StationId>)>> {
        let url = format!("{}/stops.json", self.base_url);
        let mut after = None;
        let mut resolved = std::collections::HashMap::new();

        loop {
            let request = self
                .http
                .get(&url)
                .query(&[("feed_onestop_id", feed_onestop_id), ("limit", "1000")]);
            let request = match after {
                Some(cursor) => request.query(&[("after", cursor)]),
                None => request,
            };
            let response = self.apply_auth(request).send().await?.error_for_status()?;
            let body: StopsResponse = response.json().await?;
            after = body.meta.and_then(|meta| meta.after);

            for record in body.stops {
                let station_id = record
                    .parent
                    .map(|station| StationId::from(station.onestop_id));
                resolved.insert(
                    record.stop_id,
                    (StopId::from(record.onestop_id), station_id),
                );
            }

            if after.is_none() {
                return Ok(resolved);
            }
        }
    }
}

#[derive(Debug, PartialEq)]
pub struct DiscoveredFeed {
    pub onestop_id: String,
    pub name: String,
    pub gtfs_static_url: String,
    pub gtfs_rt_vehicle_positions_url: Option<String>,
    pub gtfs_rt_trip_updates_url: Option<String>,
    pub timezone: String,
}

#[derive(serde::Deserialize)]
struct OperatorsResponse {
    operators: Vec<OperatorRecord>,
}

#[derive(serde::Deserialize)]
struct OperatorRecord {
    timezone: Option<String>,
    feeds: Vec<FeedRef>,
}

#[derive(serde::Deserialize)]
struct FeedRef {
    onestop_id: String,
}

#[derive(serde::Deserialize)]
struct FeedsResponse {
    feeds: Vec<FeedRecord>,
}

#[derive(serde::Deserialize)]
struct FeedRecord {
    onestop_id: String,
    name: Option<String>,
    urls: Option<FeedUrls>,
}

#[derive(serde::Deserialize)]
struct FeedUrls {
    static_current: Option<String>,
    realtime_vehicle_positions: Option<String>,
    realtime_trip_updates: Option<String>,
}

#[derive(serde::Deserialize)]
struct AgenciesResponse {
    agencies: Vec<AgencyRecord>,
}

#[derive(serde::Deserialize)]
struct AgencyRecord {
    onestop_id: String,
}

#[derive(serde::Deserialize)]
struct RoutesResponse {
    routes: Vec<RouteRecord>,
}

#[derive(serde::Deserialize)]
struct RouteRecord {
    onestop_id: String,
}

#[derive(serde::Deserialize)]
struct StopsResponse {
    stops: Vec<StopRecord>,
    #[serde(default)]
    meta: Option<PaginationMeta>,
}

#[derive(serde::Deserialize)]
struct StopRecord {
    #[serde(default)]
    stop_id: String,
    onestop_id: String,
    #[serde(default, alias = "parent_station")]
    parent: Option<StationRecord>,
}

#[derive(serde::Deserialize)]
struct PaginationMeta {
    after: Option<i64>,
}

#[derive(serde::Deserialize)]
struct StationRecord {
    onestop_id: String,
}

fn unique_feed_ids(operators: &[OperatorRecord]) -> Vec<(String, String)> {
    let mut seen = std::collections::HashSet::new();
    let mut out = Vec::new();
    for op in operators {
        let tz = op.timezone.clone().unwrap_or_else(|| "UTC".to_string());
        for f in &op.feeds {
            if seen.insert(f.onestop_id.clone()) {
                out.push((f.onestop_id.clone(), tz.clone()));
            }
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{method, path, query_param, query_param_is_missing};
    use wiremock::{Mock, MockServer, ResponseTemplate};

    fn client_for(server: &MockServer) -> TransitlandClient {
        TransitlandClient::with_base_url(None, server.uri())
    }

    // ── Agency ────────────────────────────────────────────────────────────────

    #[tokio::test]
    async fn resolve_agency_returns_some_when_match_found() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/agencies.json"))
            .and(query_param("gtfs_agency_id", "STM"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "agencies": [{"onestop_id": "o-f25d-stm"}]
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_agency("STM", "f-f25d-stm").await.unwrap();
        assert_eq!(result, Some(AgencyId::from("o-f25d-stm")));
    }

    #[tokio::test]
    async fn resolve_agency_returns_none_when_empty_array() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/agencies.json"))
            .and(query_param("gtfs_agency_id", "UNKNOWN"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "agencies": []
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client
            .resolve_agency("UNKNOWN", "f-f25d-stm")
            .await
            .unwrap();
        assert_eq!(result, None);
    }

    // ── Route ─────────────────────────────────────────────────────────────────

    #[tokio::test]
    async fn resolve_route_returns_some_when_match_found() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/routes.json"))
            .and(query_param("route_id", "14"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "routes": [{"onestop_id": "r-f25e-14"}]
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_route("14", "f-f25d-stm").await.unwrap();
        assert_eq!(result, Some(RouteId::from("r-f25e-14")));
    }

    // ── Stop ──────────────────────────────────────────────────────────────────

    #[tokio::test]
    async fn resolve_stop_returns_stop_and_parent_station_when_present() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("stop_id", "56789"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [{
                    "onestop_id": "s-f25ek-berrinord",
                    "parent_station": {"onestop_id": "s-f25e-berri"}
                }]
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_stop("56789", "f-f25d-stm").await.unwrap();
        assert_eq!(
            result,
            Some((
                StopId::from("s-f25ek-berrinord"),
                Some(StationId::from("s-f25e-berri"))
            ))
        );
    }

    #[tokio::test]
    async fn resolve_stops_for_feed_returns_map() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("limit", "1000"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [
                    {
                        "stop_id": "56789",
                        "onestop_id": "s-f25ek-berrinord",
                        "parent": {"onestop_id": "s-f25e-berri"}
                    },
                    {
                        "stop_id": "11111",
                        "onestop_id": "s-f25ek-somewhere",
                        "parent": null
                    }
                ],
                "meta": {}
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_stops_for_feed("f-f25d-stm").await.unwrap();

        assert_eq!(
            result.get("56789"),
            Some(&(
                StopId::from("s-f25ek-berrinord"),
                Some(StationId::from("s-f25e-berri"))
            ))
        );
        assert_eq!(
            result.get("11111"),
            Some(&(StopId::from("s-f25ek-somewhere"), None))
        );
    }

    #[tokio::test]
    async fn resolve_stops_for_feed_follows_pagination() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("feed_onestop_id", "f-test"))
            .and(query_param("limit", "1000"))
            .and(query_param_is_missing("after"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [{
                    "stop_id": "first",
                    "onestop_id": "s-first",
                    "parent": null
                }],
                "meta": {"after": 42}
            })))
            .expect(1)
            .mount(&server)
            .await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("feed_onestop_id", "f-test"))
            .and(query_param("limit", "1000"))
            .and(query_param("after", "42"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [{
                    "stop_id": "second",
                    "onestop_id": "s-second",
                    "parent": null
                }],
                "meta": {}
            })))
            .expect(1)
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_stops_for_feed("f-test").await.unwrap();

        assert_eq!(result.get("first"), Some(&(StopId::from("s-first"), None)));
        assert_eq!(
            result.get("second"),
            Some(&(StopId::from("s-second"), None))
        );
    }

    #[tokio::test]
    async fn resolve_stops_for_feed_propagates_later_page_error() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param_is_missing("after"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [],
                "meta": {"after": 42}
            })))
            .mount(&server)
            .await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("after", "42"))
            .respond_with(ResponseTemplate::new(429))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let error = client.resolve_stops_for_feed("f-test").await.unwrap_err();

        assert!(error.to_string().contains("429 Too Many Requests"));
    }

    // ── Discover Feeds ────────────────────────────────────────────────────────

    #[tokio::test]
    async fn discover_feeds_returns_feeds_for_known_city() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/operators.json"))
            .and(query_param("city_name", "Montreal"))
            .and(query_param("per_page", "50"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "operators": [{"name": "STM", "timezone": "America/Toronto", "feeds": [{"onestop_id": "f-f25d-stm"}]}]
            })))
            .mount(&server)
            .await;
        Mock::given(method("GET"))
            .and(path("/feeds.json"))
            .and(query_param("onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "feeds": [{"onestop_id": "f-f25d-stm", "name": "STM GTFS", "urls": {"static_current": "https://stm.info/gtfs.zip", "realtime_vehicle_positions": "https://stm.info/vp.pb", "realtime_trip_updates": "https://stm.info/tu.pb"}}]
            })))
            .mount(&server)
            .await;
        let client = client_for(&server);
        let feeds = client.discover_feeds_for_city("Montreal").await.unwrap();
        assert_eq!(feeds.len(), 1);
        assert_eq!(feeds[0].onestop_id, "f-f25d-stm");
        assert_eq!(feeds[0].gtfs_static_url, "https://stm.info/gtfs.zip");
        assert_eq!(feeds[0].timezone, "America/Toronto");
        assert_eq!(
            feeds[0].gtfs_rt_vehicle_positions_url.as_deref(),
            Some("https://stm.info/vp.pb")
        );
    }

    #[tokio::test]
    async fn discover_feeds_returns_empty_for_unknown_city() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/operators.json"))
            .and(query_param("city_name", "Nowhere"))
            .respond_with(
                ResponseTemplate::new(200).set_body_json(serde_json::json!({"operators": []})),
            )
            .mount(&server)
            .await;
        let client = client_for(&server);
        let feeds = client.discover_feeds_for_city("Nowhere").await.unwrap();
        assert!(feeds.is_empty());
    }

    #[tokio::test]
    async fn discover_feeds_skips_feed_with_no_static_url() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/operators.json"))
            .and(query_param("city_name", "TestCity"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({"operators": [{"name": "Op", "timezone": "UTC", "feeds": [{"onestop_id": "f-abc-op"}]}]})))
            .mount(&server)
            .await;
        Mock::given(method("GET"))
            .and(path("/feeds.json"))
            .and(query_param("onestop_id", "f-abc-op"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({"feeds": [{"onestop_id": "f-abc-op", "name": "Op", "urls": {}}]})))
            .mount(&server)
            .await;
        let client = client_for(&server);
        let feeds = client.discover_feeds_for_city("TestCity").await.unwrap();
        assert!(feeds.is_empty());
    }

    #[tokio::test]
    async fn discover_feeds_propagates_http_error() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/operators.json"))
            .respond_with(ResponseTemplate::new(500))
            .mount(&server)
            .await;
        let client = client_for(&server);
        let result = client.discover_feeds_for_city("Montreal").await;
        assert!(result.is_err());
    }

    #[tokio::test]
    async fn discover_feeds_deduplicates_shared_feed_ids() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/operators.json"))
            .and(query_param("city_name", "TestCity"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "operators": [
                    {"timezone": "UTC", "feeds": [{"onestop_id": "f-shared"}]},
                    {"timezone": "UTC", "feeds": [{"onestop_id": "f-shared"}]}
                ]
            })))
            .mount(&server)
            .await;
        Mock::given(method("GET"))
            .and(path("/feeds.json"))
            .and(query_param("onestop_id", "f-shared"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "feeds": [{"onestop_id": "f-shared", "name": "Shared", "urls": {"static_current": "https://example.com/gtfs.zip"}}]
            })))
            .expect(1)
            .mount(&server)
            .await;
        let client = client_for(&server);
        let feeds = client.discover_feeds_for_city("TestCity").await.unwrap();
        assert_eq!(feeds.len(), 1);
    }

    #[tokio::test]
    async fn resolve_stop_returns_none_parent_station_when_absent() {
        let server = MockServer::start().await;
        Mock::given(method("GET"))
            .and(path("/stops.json"))
            .and(query_param("stop_id", "11111"))
            .and(query_param("feed_onestop_id", "f-f25d-stm"))
            .and(query_param("per_page", "1"))
            .respond_with(ResponseTemplate::new(200).set_body_json(serde_json::json!({
                "stops": [{
                    "onestop_id": "s-f25ek-somewhere",
                    "parent_station": null
                }]
            })))
            .mount(&server)
            .await;

        let client = client_for(&server);
        let result = client.resolve_stop("11111", "f-f25d-stm").await.unwrap();
        assert_eq!(result, Some((StopId::from("s-f25ek-somewhere"), None)));
    }
}
