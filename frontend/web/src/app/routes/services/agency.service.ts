import { AgencyDTO } from '../../agencies/models/agency.model';
import { AgencySummary } from '../../agencies/models/agency-summary.model';

export interface AgencyListResponse {
  content: AgencyDTO[];
  totalElements: number;
  totalPages: number;
}

export interface AgencySummaryListResponse {
  content: AgencySummary[];
  totalElements: number;
  totalPages: number;
}
