-- Benchmarks table for global high-performer comparison data

CREATE TABLE IF NOT EXISTS benchmarks (
    id                     INTEGER PRIMARY KEY,
    system_name            TEXT NOT NULL UNIQUE,
    city                   TEXT NOT NULL,
    on_time_pct            REAL NOT NULL,
    speed_vs_scheduled_pct REAL NOT NULL,  -- deficit: positive = slower than schedule
    source_url             TEXT NOT NULL,
    year                   INTEGER NOT NULL
);

INSERT OR IGNORE INTO benchmarks (system_name, city, on_time_pct, speed_vs_scheduled_pct, source_url, year) VALUES
  ('Helsinki (HSL)',         'Helsinki',  89.0, 3.0, 'https://www.hsl.fi/en/hsl/statistics-and-research', 2023),
  ('Zurich (ZVV)',           'Zurich',    92.0, 1.8, 'https://www.zvv.ch/zvv/en/about-zvv/facts-and-figures.html', 2023),
  ('Singapore (SBS Transit)','Singapore', 92.0, 2.0, 'https://www.lta.gov.sg/content/ltagov/en/getting_around/public_transport/bus.html', 2023),
  ('Tokyo (Toei Bus)',       'Tokyo',     96.0, 1.5, 'https://www.kotsu.metro.tokyo.jp/eng/services/bus.html', 2023);
