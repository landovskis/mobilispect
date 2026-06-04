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
            let station_id = r.parent_station.map(|s| StationId::from(s.onestop_id));
            (stop_id, station_id)
        }))
    }
}

// Private serde structs for deserialising Transitland API responses.

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
}

#[derive(serde::Deserialize)]
struct StopRecord {
    onestop_id: String,
    parent_station: Option<StationRecord>,
}

#[derive(serde::Deserialize)]
struct StationRecord {
    onestop_id: String,
}

#[cfg(test)]
mod tests {
    use super::*;
    use wiremock::matchers::{method, path, query_param};
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
