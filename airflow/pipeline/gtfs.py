import json
import os
import pickle
import shutil
import zipfile
from dataclasses import dataclass
from datetime import datetime
from typing import Dict, Optional

import pandas as pd
import requests

from .db import get_gtfs_storage_root


REQUIRED_GTFS_FILES = [
    "agency.txt",
    "stops.txt",
    "routes.txt",
    "trips.txt",
    "stop_times.txt",
]


@dataclass
class ParsedGTFS:
    agencies: pd.DataFrame
    routes: pd.DataFrame
    stops: pd.DataFrame
    trips: pd.DataFrame
    stop_times: pd.DataFrame
    shapes: pd.DataFrame


def ensure_import_dir(import_id: str) -> str:
    root = get_gtfs_storage_root()
    path = os.path.join(root, import_id)
    os.makedirs(path, exist_ok=True)
    return path


def download_gtfs_zip(url: str, import_id: str) -> str:
    import_dir = ensure_import_dir(import_id)
    zip_path = os.path.join(import_dir, "feed.zip")
    with requests.get(url, stream=True, timeout=120) as resp:
        resp.raise_for_status()
        with open(zip_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=1024 * 1024):
                if chunk:
                    f.write(chunk)
    return zip_path


def extract_gtfs_zip(zip_path: str, import_id: str) -> str:
    import_dir = ensure_import_dir(import_id)
    extract_dir = os.path.join(import_dir, "extracted")
    if os.path.exists(extract_dir):
        shutil.rmtree(extract_dir)
    os.makedirs(extract_dir, exist_ok=True)
    with zipfile.ZipFile(zip_path) as zf:
        zf.extractall(extract_dir)
    return extract_dir


def validate_gtfs_files(extract_dir: str) -> None:
    missing = [name for name in REQUIRED_GTFS_FILES if not os.path.exists(os.path.join(extract_dir, name))]
    if missing:
        raise RuntimeError(f"Missing required GTFS files: {', '.join(missing)}")


def _read_csv(extract_dir: str, filename: str, usecols=None) -> pd.DataFrame:
    path = os.path.join(extract_dir, filename)
    if not os.path.exists(path):
        return pd.DataFrame()
    return pd.read_csv(
        path,
        dtype=str,
        usecols=usecols,
        keep_default_na=False,
        na_values=[""],
        low_memory=False,
    )


def parse_gtfs(extract_dir: str) -> ParsedGTFS:
    agencies = _read_csv(extract_dir, "agency.txt")
    routes = _read_csv(extract_dir, "routes.txt")
    stops = _read_csv(extract_dir, "stops.txt")
    trips = _read_csv(extract_dir, "trips.txt")
    stop_times = _read_csv(extract_dir, "stop_times.txt")
    shapes = _read_csv(extract_dir, "shapes.txt")

    if "stop_sequence" in stop_times.columns:
        stop_times["stop_sequence"] = pd.to_numeric(stop_times["stop_sequence"], errors="coerce")

    return ParsedGTFS(
        agencies=agencies,
        routes=routes,
        stops=stops,
        trips=trips,
        stop_times=stop_times,
        shapes=shapes,
    )


def save_parsed(parsed: ParsedGTFS, import_id: str) -> str:
    import_dir = ensure_import_dir(import_id)
    path = os.path.join(import_dir, "parsed.pkl")
    with open(path, "wb") as f:
        pickle.dump(parsed, f, protocol=4)
    return path


def load_parsed(path: str) -> ParsedGTFS:
    with open(path, "rb") as f:
        return pickle.load(f)


def parse_time_to_seconds(value: Optional[str]) -> Optional[int]:
    if value is None:
        return None
    text = str(value).strip()
    if not text:
        return None
    parts = text.split(":")
    if len(parts) != 3:
        return None
    try:
        hours = int(parts[0])
        minutes = int(parts[1])
        seconds = int(float(parts[2]))
        total = hours * 3600 + minutes * 60 + seconds
        return total % 86400
    except ValueError:
        return None


def snapshot_metadata(parsed: ParsedGTFS) -> Dict[str, int]:
    return {
        "agencies": len(parsed.agencies),
        "routes": len(parsed.routes),
        "stops": len(parsed.stops),
        "trips": len(parsed.trips),
        "stop_times": len(parsed.stop_times),
    }


def write_metadata(import_id: str, data: Dict[str, int]) -> str:
    import_dir = ensure_import_dir(import_id)
    path = os.path.join(import_dir, "metadata.json")
    payload = {"generated_at": datetime.utcnow().isoformat(), "summary": data}
    with open(path, "w", encoding="utf-8") as f:
        json.dump(payload, f)
    return path
