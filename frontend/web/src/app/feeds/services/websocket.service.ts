import { Injectable, OnDestroy } from '@angular/core';
import { Observable, Subject, BehaviorSubject } from 'rxjs';
import { filter, tap } from 'rxjs/operators';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { environment } from '../../../environments/environment';

export interface WebSocketMessage {
  type: string;
  data: unknown;
  timestamp: string;
}

export interface ProgressUpdateMessage {
  progress?: {
    importId: string;
    feedOnestopId: string;
    progressPercentage: number;
    currentStep: string;
    currentStepNumber: number;
    totalSteps: number;
    startedAt: string;
    lastUpdatedAt: string;
    estimatedTimeRemainingSeconds?: number;
    processingRate?: number;
  };
  completed?: boolean;
  error?: string;
  finishedAt?: string;
  durationSeconds?: number;
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
    details?: unknown;
  };
}

/**
 * WebSocket Service for Real-Time Import Updates
 *
 * Provides real-time communication with the backend using STOMP over WebSocket.
 * Handles connection management, automatic reconnection, and message routing.
 *
 * Uses STOMP protocol to match the backend Spring WebSocket configuration.
 */
@Injectable({
  providedIn: 'root'
})
export class WebSocketService implements OnDestroy {
  private stompClient: Client | null = null;
  private messagesSubject$ = new Subject<WebSocketMessage>();
  private connectionStatus$ = new BehaviorSubject<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'>('DISCONNECTED');
  private destroy$ = new Subject<void>();
  private subscriptions = new Map<string, StompSubscription>();

  constructor() {}

  /**
   * Connects to the STOMP WebSocket endpoint
   */
  connect(): void {
    if (this.stompClient && this.stompClient.connected) {
      console.log('STOMP client already connected');
      return;
    }

    this.connectionStatus$.next('CONNECTING');
    console.log('Connecting to STOMP WebSocket:', `${environment.wsUrl}/ws/feeds`);
    void this.createClient();
  }

  private async createClient(): Promise<void> {
    const { default: SockJS } = await import('sockjs-client');

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(`${environment.wsUrl}/ws/feeds`),

      connectHeaders: {},

      debug: (str) => {
        console.log('STOMP Debug:', str);
      },

      reconnectDelay: 5000, // 5 seconds
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,

      onConnect: (frame) => {
        console.log('STOMP connected successfully:', frame);
        this.connectionStatus$.next('CONNECTED');
      },

      onStompError: (frame) => {
        console.error('STOMP error:', frame);
        this.connectionStatus$.next('ERROR');
      },

      onWebSocketClose: (event) => {
        console.log('WebSocket connection closed:', event);
        this.connectionStatus$.next('DISCONNECTED');
      },

      onWebSocketError: (event) => {
        console.error('WebSocket error:', event);
        this.connectionStatus$.next('ERROR');
      },

      onDisconnect: (frame) => {
        console.log('STOMP disconnected:', frame);
        this.connectionStatus$.next('DISCONNECTED');
      }
    });

    this.stompClient.activate();
  }

  /**
   * Disconnects from the STOMP WebSocket
   */
  disconnect(): void {
    if (this.stompClient) {
      console.log('Disconnecting STOMP client');

      // Unsubscribe all active subscriptions
      this.subscriptions.forEach((subscription, topic) => {
        console.log('Unsubscribing from:', topic);
        subscription.unsubscribe();
      });
      this.subscriptions.clear();

      this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.connectionStatus$.next('DISCONNECTED');
  }

  /**
   * Sends a message to the STOMP server
   */
  send(destination: string, body: unknown): void {
    if (this.stompClient && this.stompClient.connected) {
      this.stompClient.publish({
        destination: destination,
        body: JSON.stringify(body)
      });
    } else {
      console.warn('Cannot send message: STOMP client not connected', destination, body);
    }
  }

  /**
   * Subscribes to import progress updates for a specific import
   *
   * Backend publishes to: /topic/import/progress/{importId}
   * Message format: ProgressUpdate { progress?: ImportProgress, completed?: boolean, error?: string }
   */
  subscribeToImportProgress(importId: string): Observable<ProgressUpdateMessage> {
    const topic = `/topic/import/progress/${importId}`;
    const progressSubject = new Subject<ProgressUpdateMessage>();

    // Wait for connection before subscribing
    this.connectionStatus$.pipe(
      filter(status => status === 'CONNECTED'),
      tap(() => {
        if (!this.stompClient) {
          console.error('STOMP client not available');
          return;
        }

        // Check if already subscribed
        if (this.subscriptions.has(topic)) {
          console.log('Already subscribed to:', topic);
          return;
        }

        console.log('Subscribing to STOMP topic:', topic);

        const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
          try {
            const progressUpdate: ProgressUpdateMessage = JSON.parse(message.body);
            console.log('Received progress update:', progressUpdate);
            progressSubject.next(progressUpdate);
          } catch (error) {
            console.error('Error parsing progress update:', error, message.body);
          }
        });

        this.subscriptions.set(topic, subscription);
      })
    ).subscribe();

    return progressSubject.asObservable();
  }

  /**
   * Subscribes to import status updates for a specific import
   */
  subscribeToImportStatus(importId: string): Observable<ImportStatusMessage> {
    const topic = `/topic/import/status/${importId}`;
    const statusSubject = new Subject<ImportStatusMessage>();

    this.connectionStatus$.pipe(
      filter(status => status === 'CONNECTED'),
      tap(() => {
        if (!this.stompClient) {
          console.error('STOMP client not available');
          return;
        }

        if (this.subscriptions.has(topic)) {
          console.log('Already subscribed to:', topic);
          return;
        }

        console.log('Subscribing to STOMP topic:', topic);

        const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
          try {
            const statusUpdate: ImportStatusMessage = JSON.parse(message.body);
            console.log('Received status update:', statusUpdate);
            statusSubject.next(statusUpdate);
          } catch (error) {
            console.error('Error parsing status update:', error, message.body);
          }
        });

        this.subscriptions.set(topic, subscription);
      })
    ).subscribe();

    return statusSubject.asObservable();
  }

  /**
   * Subscribes to system alerts
   */
  subscribeToSystemAlerts(): Observable<SystemAlertMessage> {
    const topic = '/topic/system/alerts';
    const alertsSubject = new Subject<SystemAlertMessage>();

    this.connectionStatus$.pipe(
      filter(status => status === 'CONNECTED'),
      tap(() => {
        if (!this.stompClient) {
          console.error('STOMP client not available');
          return;
        }

        if (this.subscriptions.has(topic)) {
          console.log('Already subscribed to:', topic);
          return;
        }

        console.log('Subscribing to STOMP topic:', topic);

        const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
          try {
            const alert: SystemAlertMessage = JSON.parse(message.body);
            console.log('Received system alert:', alert);
            alertsSubject.next(alert);
          } catch (error) {
            console.error('Error parsing system alert:', error, message.body);
          }
        });

        this.subscriptions.set(topic, subscription);
      })
    ).subscribe();

    return alertsSubject.asObservable();
  }

  /**
   * Unsubscribes from a specific topic
   */
  unsubscribeFromTopic(topic: string): void {
    const subscription = this.subscriptions.get(topic);
    if (subscription) {
      console.log('Unsubscribing from topic:', topic);
      subscription.unsubscribe();
      this.subscriptions.delete(topic);
    }
  }

  /**
   * Unsubscribes from import updates
   */
  unsubscribeFromImport(importId: string): void {
    this.unsubscribeFromTopic(`/topic/import/progress/${importId}`);
    this.unsubscribeFromTopic(`/topic/import/status/${importId}`);
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
   * Checks if currently connected
   */
  isConnected(): boolean {
    return this.stompClient?.connected || false;
  }

  /**
   * Sends a heartbeat to keep the connection alive
   * Note: STOMP handles heartbeats automatically via heartbeatIncoming/heartbeatOutgoing
   */
  startHeartbeat(): void {
    // STOMP client handles heartbeats automatically
    console.log('STOMP heartbeat is managed automatically by the client');
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }
}
