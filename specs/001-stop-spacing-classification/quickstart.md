# Quickstart: Average Stop Spacing

## Goal

Verify that route variants display average stop spacing (km) and classification
on the route detail page.

## Steps

1. Import a GTFS feed that includes stop sequences and shape distances.
2. Open the route detail page for a route with multiple variants.
3. Confirm each variant shows:
   - Average stop spacing in kilometers (two decimal places), or "Not
     available" if missing data.
   - Classification label (local, rapid, express) derived from spacing
     thresholds.
