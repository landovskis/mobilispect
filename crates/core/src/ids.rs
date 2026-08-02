use std::{fmt, ops::Deref};

macro_rules! string_id {
    ($name:ident) => {
        #[derive(
            Debug,
            Clone,
            PartialEq,
            Eq,
            PartialOrd,
            Ord,
            Hash,
            sqlx::Type,
            serde::Serialize,
            serde::Deserialize,
        )]
        #[sqlx(transparent)]
        #[serde(transparent)]
        pub struct $name(pub String);

        impl $name {
            pub fn as_str(&self) -> &str {
                &self.0
            }
        }

        impl fmt::Display for $name {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                write!(f, "{}", self.0)
            }
        }

        impl From<String> for $name {
            fn from(s: String) -> Self {
                Self(s)
            }
        }

        impl From<&str> for $name {
            fn from(s: &str) -> Self {
                Self(s.to_owned())
            }
        }

        impl AsRef<str> for $name {
            fn as_ref(&self) -> &str {
                &self.0
            }
        }

        impl Deref for $name {
            type Target = str;
            fn deref(&self) -> &str {
                &self.0
            }
        }

        impl PartialEq<str> for $name {
            fn eq(&self, other: &str) -> bool {
                self.0 == other
            }
        }

        impl PartialEq<&str> for $name {
            fn eq(&self, other: &&str) -> bool {
                self.0 == *other
            }
        }

        impl PartialEq<String> for $name {
            fn eq(&self, other: &String) -> bool {
                &self.0 == other
            }
        }
    };
}

macro_rules! int_id {
    ($name:ident) => {
        #[derive(
            Debug,
            Clone,
            Copy,
            PartialEq,
            Eq,
            PartialOrd,
            Ord,
            Hash,
            sqlx::Type,
            serde::Serialize,
            serde::Deserialize,
        )]
        #[sqlx(transparent)]
        #[serde(transparent)]
        pub struct $name(pub i64);

        impl $name {
            pub fn as_i64(self) -> i64 {
                self.0
            }
        }

        impl fmt::Display for $name {
            fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
                write!(f, "{}", self.0)
            }
        }

        impl From<i64> for $name {
            fn from(n: i64) -> Self {
                Self(n)
            }
        }

        impl PartialEq<i64> for $name {
            fn eq(&self, other: &i64) -> bool {
                self.0 == *other
            }
        }
    };
}

// String-based IDs
string_id!(AgencyId);
string_id!(RouteId);
string_id!(TripId);
string_id!(StopId);
string_id!(StationId);
string_id!(VariantId);
string_id!(ServiceId);
string_id!(VehicleId);

// Integer-based IDs
int_id!(FeedId);
int_id!(RegionId);
int_id!(NetworkId);
int_id!(DirectionId);
int_id!(CorridorId);
int_id!(CrossSectionId);
int_id!(RemixId);

/// Convert from config `id: u32` to `FeedId`.
impl From<u32> for FeedId {
    fn from(n: u32) -> Self {
        Self(i64::from(n))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    // --- AgencyId (string — Transitland Onestop ID) ---

    #[test]
    fn agency_id_from_str_roundtrips() {
        let id = AgencyId::from("o-f25d-stm");
        assert_eq!(id.as_str(), "o-f25d-stm");
        assert_eq!(&*id, "o-f25d-stm");
        assert_eq!(id, "o-f25d-stm");
    }

    #[test]
    fn agency_id_display() {
        let id = AgencyId::from("o-f25d-stm");
        assert_eq!(id.to_string(), "o-f25d-stm");
    }

    // --- FeedId (i64 — feed partition key) ---

    #[test]
    fn feed_id_from_i64_roundtrips() {
        let id = FeedId::from(1i64);
        assert_eq!(id.as_i64(), 1);
        assert_eq!(id, 1i64);
    }

    #[test]
    fn feed_id_from_u32_converts() {
        let id = FeedId::from(42u32);
        assert_eq!(id.as_i64(), 42);
        assert_eq!(id.to_string(), "42");
    }

    #[test]
    fn feed_id_display() {
        let id = FeedId(7);
        assert_eq!(id.to_string(), "7");
    }

    // --- StationId (string — Transitland stop Onestop ID for stations) ---

    #[test]
    fn station_id_from_str_roundtrips() {
        let id = StationId::from("s-f25d-berri");
        assert_eq!(id.as_str(), "s-f25d-berri");
        assert_eq!(&*id, "s-f25d-berri");
        assert_eq!(id, "s-f25d-berri");
    }

    #[test]
    fn station_id_display() {
        let id = StationId::from("s-f25d-berri");
        assert_eq!(id.to_string(), "s-f25d-berri");
    }

    // --- RegionId (i64) ---

    #[test]
    fn region_id_from_i64_roundtrips() {
        let id = RegionId::from(10i64);
        assert_eq!(id.as_i64(), 10);
        assert_eq!(id, 10i64);
    }

    #[test]
    fn region_id_display() {
        let id = RegionId(3);
        assert_eq!(id.to_string(), "3");
    }

    // --- NetworkId (i64) ---

    #[test]
    fn network_id_from_i64_roundtrips() {
        let id = NetworkId::from(5i64);
        assert_eq!(id.as_i64(), 5);
        assert_eq!(id, 5i64);
    }

    #[test]
    fn network_id_display() {
        let id = NetworkId(99);
        assert_eq!(id.to_string(), "99");
    }

    // --- DirectionId (i64) ---

    #[test]
    fn direction_id_wraps_i64() {
        let id = DirectionId::from(0i64);
        assert_eq!(id.as_i64(), 0);
        assert_eq!(id, 0i64);
    }

    #[test]
    fn direction_id_as_i64_returns_nonzero_inner_value() {
        assert_eq!(DirectionId(1).as_i64(), 1);
    }

    #[test]
    fn direction_id_display() {
        let id = DirectionId(1);
        assert_eq!(id.to_string(), "1");
    }

    // --- RouteId ---

    #[test]
    fn route_id_display() {
        let id = RouteId::from("R1".to_string());
        assert_eq!(id.to_string(), "R1");
        assert_eq!(id, "R1");
    }

    // --- VariantId ---

    #[test]
    fn variant_id_from_str() {
        let id = VariantId::from("abc123");
        assert_eq!(&*id, "abc123");
    }

    // --- int_id Copy semantics ---

    #[test]
    fn feed_id_is_copy() {
        let a = FeedId(1);
        let b = a; // copy
        assert_eq!(a, b);
    }

    #[test]
    fn region_id_is_copy() {
        let a = RegionId(2);
        let b = a;
        assert_eq!(a, b);
    }

    #[test]
    fn network_id_is_copy() {
        let a = NetworkId(3);
        let b = a;
        assert_eq!(a, b);
    }
}
