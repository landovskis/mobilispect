import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject, BehaviorSubject, timer } from 'rxjs';
import { webSocket, WebSocketSubject } from 'rxjs/webSocket';
import { takeUntil, retry, delayWhen, tap, filter, catchError } from 'rxjs/operators';
import { environment } from '../../../environments/environment';

export interface WebSocketMessage {
  type: string;
  data: any;
  timestamp: string;
}

export interface ProgressUpdateMessage extends WebSocketMessage {
  type: 'PROGRESS_UPDATE';
  data: {
    importId: string;
    progressPercentage: number;
    currentStep: string;
    estimatedTimeRemainingSeconds?: number;
  };
}

export interface ImportStatusMessage extends WebSocketMessage {
  type: 'IMPORT_STATUS';
  data: {
    importId: string;
    status: string;
    completedAt?: string;
    errorMessage?: string;
    fileSizeBytes?: number;
  };
}

export interface SystemAlertMessage extends WebSocketMessage {
  type: 'SYSTEM_ALERT';
  data: {
    level: 'INFO' | 'WARNING' | 'ERROR';
    message: string;
    details?: any;
  };
}

/**
 * WebSocket Service for Real-Time Import Updates
 *
 * Provides real-time communication with the backend for import progress,
 * status updates, and system alerts. Handles connection management,
 * automatic reconnection, and message routing.
 */
@Injectable({
  providedIn: 'root'
})
export class WebSocketService implements OnDestroy {
  private socket$: WebSocketSubject<any> | null = null;
  private messagesSubject$ = new Subject<WebSocketMessage>();
  private connectionStatus$ = new BehaviorSubject<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'>('DISCONNECTED');
  private destroy$ = new Subject<void>();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 5;
  private reconnectDelay = 1000; // Start with 1 second

  constructor() {}

  /**
   * Connects to the WebSocket endpoint
   */
  connect(): void {
    if (this.socket$ && !this.socket$.closed) {
      return; // Already connected
    }

    this.connectionStatus$.next('CONNECTING');
    console.log('Connecting to WebSocket:', environment.wsUrl);

    this.socket$ = webSocket({
      url: `${environment.wsUrl}/ws/feed-management`,
      openObserver: {
        next: () => {
          console.log('WebSocket connected successfully');
          this.connectionStatus$.next('CONNECTED');
          this.reconnectAttempts = 0;
          this.reconnectDelay = 1000; // Reset delay
        }
      },
      closeObserver: {
        next: () => {
          console.log('WebSocket connection closed');
          this.connectionStatus$.next('DISCONNECTED');
          this.handleReconnection();
        }
      },
      serializer: msg => JSON.stringify(msg),
      deserializer: msg => JSON.parse(msg.data)
    });

    // Subscribe to incoming messages
    this.socket$.pipe(
      takeUntil(this.destroy$),
      retry({
        count: this.maxReconnectAttempts,
        delay: (error, retryCount) => {
          console.warn(`WebSocket retry attempt ${retryCount}:`, error);
          this.connectionStatus$.next('CONNECTING');
          return timer(this.getReconnectDelay(retryCount));
        }
      }),
      catchError(error => {
        console.error('WebSocket error:', error);
        this.connectionStatus$.next('ERROR');
        throw error;
      })
    ).subscribe({
      next: (message) => {
        console.log('WebSocket message received:', message);
        this.messagesSubject$.next(message);
      },
      error: (error) => {
        console.error('WebSocket subscription error:', error);
        this.connectionStatus$.next('ERROR');
      }
    });
  }

  /**
   * Disconnects from the WebSocket
   */
  disconnect(): void {
    if (this.socket$) {
      this.socket$.complete();
      this.socket$ = null;
    }
    this.connectionStatus$.next('DISCONNECTED');
  }

  /**
   * Sends a message to the WebSocket server
   */
  send(message: any): void {
    if (this.socket$ && this.connectionStatus$.value === 'CONNECTED') {
      this.socket$.next(message);
    } else {
      console.warn('Cannot send message: WebSocket not connected', message);
    }
  }

  /**
   * Subscribes to import progress updates for a specific import
   */
  subscribeToImportProgress(importId: string): Observable<ProgressUpdateMessage> {
    // Send subscription message
    this.send({
      type: 'SUBSCRIBE_IMPORT_PROGRESS',
      importId: importId
    });

    return this.messagesSubject$.pipe(
      filter((msg): msg is ProgressUpdateMessage =>
        msg.type === 'PROGRESS_UPDATE' && msg.data.importId === importId
      ),
      tap(msg => console.log('Progress update for import:', importId, msg.data))
    );
  }

  /**
   * Subscribes to import status updates for a specific import
   */
  subscribeToImportStatus(importId: string): Observable<ImportStatusMessage> {
    // Send subscription message
    this.send({
      type: 'SUBSCRIBE_IMPORT_STATUS',
      importId: importId
    });

    return this.messagesSubject$.pipe(
      filter((msg): msg is ImportStatusMessage =>
        msg.type === 'IMPORT_STATUS' && msg.data.importId === importId
      ),
      tap(msg => console.log('Status update for import:', importId, msg.data))
    );
  }

  /**
   * Subscribes to system alerts
   */
  subscribeToSystemAlerts(): Observable<SystemAlertMessage> {
    return this.messagesSubject$.pipe(
      filter((msg): msg is SystemAlertMessage => msg.type === 'SYSTEM_ALERT'),
      tap(msg => console.log('System alert:', msg.data))
    );
  }

  /**
   * Unsubscribes from import updates
   */
  unsubscribeFromImport(importId: string): void {
    this.send({
      type: 'UNSUBSCRIBE_IMPORT',
      importId: importId
    });
  }

  /**
   * Gets the current connection status
   */
  getConnectionStatus(): Observable<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'> {
    return this.connectionStatus$.asObservable();
  }

  /**
   * Gets all WebSocket messages (for debugging)
   */
  getAllMessages(): Observable<WebSocketMessage> {
    return this.messagesSubject$.asObservable();
  }

  /**
   * Handles automatic reconnection with exponential backoff
   */
  private handleReconnection(): void {
    if (this.reconnectAttempts < this.maxReconnectAttempts && this.connectionStatus$.value !== 'ERROR') {
      this.reconnectAttempts++;
      const delay = this.getReconnectDelay(this.reconnectAttempts);

      console.log(`Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts}/${this.maxReconnectAttempts})`);

      timer(delay).subscribe(() => {
        if (this.connectionStatus$.value === 'DISCONNECTED') {
          this.connect();
        }
      });
    } else {
      console.error('Max reconnection attempts reached or connection error occurred');
      this.connectionStatus$.next('ERROR');
    }
  }

  /**
   * Calculates reconnection delay with exponential backoff
   */
  private getReconnectDelay(attempt: number): number {
    // Exponential backoff: 1s, 2s, 4s, 8s, 16s
    return Math.min(this.reconnectDelay * Math.pow(2, attempt - 1), 30000);
  }

  /**
   * Sends a heartbeat to keep the connection alive
   */
  startHeartbeat(): void {
    timer(0, 30000).pipe( // Every 30 seconds
      takeUntil(this.destroy$),
      filter(() => this.connectionStatus$.value === 'CONNECTED')
    ).subscribe(() => {
      this.send({ type: 'HEARTBEAT', timestamp: new Date().toISOString() });
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }
}
