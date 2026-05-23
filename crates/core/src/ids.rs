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

string_id!(AgencyId);
string_id!(RouteId);
string_id!(TripId);
string_id!(StopId);
string_id!(VariantId);
string_id!(ServiceId);
string_id!(VehicleId);

impl From<u32> for AgencyId {
    fn from(n: u32) -> Self {
        Self(n.to_string())
    }
}

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
pub struct DirectionId(pub i64);

impl DirectionId {
    pub fn as_i64(self) -> i64 {
        self.0
    }
}

impl fmt::Display for DirectionId {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl From<i64> for DirectionId {
    fn from(n: i64) -> Self {
        Self(n)
    }
}

impl PartialEq<i64> for DirectionId {
    fn eq(&self, other: &i64) -> bool {
        self.0 == *other
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn agency_id_from_str_roundtrips() {
        let id = AgencyId::from("STM");
        assert_eq!(id.as_str(), "STM");
        assert_eq!(&*id, "STM");
        assert_eq!(id, "STM");
    }

    #[test]
    fn agency_id_from_u32_converts_to_string() {
        let id = AgencyId::from(42u32);
        assert_eq!(id.as_str(), "42");
        assert_eq!(id.to_string(), "42");
    }

    #[test]
    fn route_id_display() {
        let id = RouteId::from("R1".to_string());
        assert_eq!(id.to_string(), "R1");
        assert_eq!(id, "R1");
    }

    #[test]
    fn direction_id_wraps_i64() {
        let id = DirectionId::from(0i64);
        assert_eq!(id.as_i64(), 0);
        assert_eq!(id, 0i64);
    }

    #[test]
    fn direction_id_display() {
        let id = DirectionId(1);
        assert_eq!(id.to_string(), "1");
    }

    #[test]
    fn variant_id_from_str() {
        let id = VariantId::from("abc123");
        assert_eq!(&*id, "abc123");
    }
}
