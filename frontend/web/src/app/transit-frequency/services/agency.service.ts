import {AgencyDTO, AgencySummary} from '../models/agency.model';

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
