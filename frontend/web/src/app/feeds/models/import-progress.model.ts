export interface ImportProgress {
  importId: string;
  progressPercentage: number;
  totalSteps: number;
  currentStep: string;
  estimatedTimeRemainingSeconds?: number;
  startedAt: string; // ISO string
  lastUpdatedAt: string; // ISO string
}

export interface ImportProgressUpdate {
  importId: string;
  progress?: ImportProgress;
  completed?: boolean;
  error?: string;
  timestamp?: number;
}

export interface ActiveImportsResponse {
  activeImports: string[];
  count: number;
  timestamp: number;
  error?: string;
}

export interface ProgressSubscriptionResponse {
  importId: string;
  currentProgress?: ImportProgress;
  subscribed: boolean;
  error?: string;
}

export interface ProgressWebSocketMessage {
  type: 'progress' | 'completion' | 'error' | 'subscription';
  data:
    | ImportProgressUpdate
    | ActiveImportsResponse
    | ProgressSubscriptionResponse;
}

export interface ProgressDisplayData {
  progress: ImportProgress;
  duration: number; // in seconds
  estimatedCompletion?: Date;
  progressBarColor: 'primary' | 'accent' | 'warn';
}

// Utility type for progress status
export type ProgressStatus =
  | 'pending'
  | 'active'
  | 'completed'
  | 'error'
  | 'cancelled';

export interface ProgressSummary {
  importId: string;
  status: ProgressStatus;
  progressPercentage: number;
  currentStep: string;
  duration?: number;
  error?: string;
}
