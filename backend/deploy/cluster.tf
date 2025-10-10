resource "google_compute_network" "mobilispect-net" {
  project = "mobilispect"
  name = "mobilispect-net"

  auto_create_subnetworks  = false
  enable_ula_internal_ipv6 = true
}

resource "google_compute_subnetwork" "mobilispect-subnet" {
  project = "mobilispect"
  name = "mobilispect-subnet"

  ip_cidr_range = "10.0.0.0/8"
  region        = "northamerica-northeast1"

  stack_type       = "IPV4_ONLY"

  network = google_compute_network.mobilispect-net.id
  secondary_ip_range {
    range_name    = "services-range"
    ip_cidr_range = "192.168.0.0/24"
  }

  secondary_ip_range {
    range_name    = "pod-ranges"
    ip_cidr_range = "172.16.0.0/16"
  }
}

resource "google_container_cluster" "mobilispect-api-prod" {
  project = "mobilispect"
  name = "mobilispect-api-prod"

  location                 = "northamerica-northeast1"
  enable_autopilot         = true
  enable_l4_ilb_subsetting = true

  network    = google_compute_network.mobilispect-net.id
  subnetwork = google_compute_subnetwork.mobilispect-subnet.id

  ip_allocation_policy {
    stack_type                    = "IPV4"
    services_secondary_range_name = google_compute_subnetwork.mobilispect-subnet.secondary_ip_range[0].range_name
    cluster_secondary_range_name  = google_compute_subnetwork.mobilispect-subnet.secondary_ip_range[1].range_name
  }

  # Set `deletion_protection` to `true` will ensure that one cannot
  # accidentally delete this instance by use of Terraform.
  deletion_protection = false
}
