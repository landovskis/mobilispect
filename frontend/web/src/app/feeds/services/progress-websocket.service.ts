import { Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable, Observer, Subject } from 'rxjs';
import { filter, takeUntil } from 'rxjs/operators';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { ActiveImportsResponse, ImportProgress } from '../models/import-progress.model';

@Injectable({
  providedIn: 'root'
})
export class ProgressWebSocketService implements OnDestroy {
  private stompClient: Client | null = null;
  private connectionStatus$ = new BehaviorSubject<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'>('DISCONNECTED');
  private destroy$ = new Subject<void>();
  private subscriptions = new Map<string, StompSubscription>();

  private readonly baseUrl = 'http://localhost:8080'; // Backend URL
  private readonly endpoint = '/feed-progress-websocket';

  constructor() {
    this.connect();
  }

  /**
   * Establishes STOMP connection over SockJS
   */
  private connect(): void {
    if (this.stompClient && this.stompClient.connected) {
      return;
    }

    this.connectionStatus$.next('CONNECTING');

    this.stompClient = new Client({
      webSocketFactory: () => new SockJS(`${this.baseUrl}${this.endpoint}`),
      connectHeaders: {
        // Add authentication headers here if needed
      },
      debug: (str) => {
        console.log('STOMP Debug:', str);
      },
      onConnect: (frame) => {
        console.log('STOMP Connected:', frame);
        this.connectionStatus$.next('CONNECTED');
      },
      onDisconnect: (frame) => {
        console.log('STOMP Disconnected:', frame);
        this.connectionStatus$.next('DISCONNECTED');
        this.clearSubscriptions();
      },
      onStompError: (frame) => {
        console.error('STOMP Error:', frame);
        this.connectionStatus$.next('ERROR');
      },
      reconnectDelay: 2000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000
    });

    this.stompClient.activate();
  }

  /**
   * Disconnects from STOMP server
   */
  disconnect(): void {
    if (this.stompClient) {
      this.clearSubscriptions();
      this.stompClient.deactivate();
      this.stompClient = null;
    }
    this.connectionStatus$.next('DISCONNECTED');
  }

  /**
   * Subscribes to progress updates for a specific import
   */
  subscribeToImportProgress(importId: string): Observable<ImportProgress> {
    return new Observable(observer => {
      if (!this.stompClient || !this.stompClient.connected) {
        console.warn('STOMP client not connected, waiting...');
        // Wait for connection then subscribe
        this.connectionStatus$.pipe(
          filter(status => status === 'CONNECTED'),
          takeUntil(this.destroy$)
        ).subscribe(() => {
          this.performProgressSubscription(importId, observer);
        });
      } else {
        this.performProgressSubscription(importId, observer);
      }

      // Cleanup function
      return () => {
        this.unsubscribeFromImportProgress(importId);
      };
    });
  }

  private performProgressSubscription(importId: string, observer: Observer<ImportProgress>): void {
    if (!this.stompClient) return;

    const topic = `/topic/import/progress/${importId}`;
    const subscriptionKey = `progress-${importId}`;

    try {
      const subscription = this.stompClient.subscribe(topic, (message: IMessage) => {
        try {
          const data = JSON.parse(message.body);

          if (data.progress) {
            observer.next(data.progress);
          } else if (data.completed) {
            observer.complete();
          } else if (data.error) {
            observer.error(new Error(data.error));
          }
        } catch (error) {
          console.error('Error parsing progress message:', error);
          observer.error(error);
        }
      });

      this.subscriptions.set(subscriptionKey, subscription);
      console.log(`Subscribed to progress for import: ${importId}`);

      // Request current progress state
      this.requestProgressUpdate(importId);

    } catch (error) {
      console.error('Error subscribing to import progress:', error);
      observer.error(error);
    }
  }

  /**
   * Requests current progress for an import
   */
  private requestProgressUpdate(importId: string): void {
    if (!this.stompClient || !this.stompClient.connected) return;

    this.stompClient.publish({
      destination: `/app/import/progress/${importId}/request`,
      body: JSON.stringify({ importId })
    });
  }

  /**
   * Unsubscribes from import progress updates
   */
  private unsubscribeFromImportProgress(importId: string): void {
    const subscriptionKey = `progress-${importId}`;
    const subscription = this.subscriptions.get(subscriptionKey);

    if (subscription) {
      subscription.unsubscribe();
      this.subscriptions.delete(subscriptionKey);
      console.log(`Unsubscribed from progress for import: ${importId}`);
    }
  }

  /**
   * Gets all active imports
   */
  getActiveImports(): Observable<string[]> {
    return new Observable(observer => {
      if (!this.stompClient || !this.stompClient.connected) {
        observer.error(new Error('WebSocket not connected'));
        return;
      }

      // Subscribe to active imports topic
      const subscription = this.stompClient.subscribe('/topic/import/progress/active', (message: IMessage) => {
        try {
          const data: ActiveImportsResponse = JSON.parse(message.body);
          if (data.error) {
            observer.error(new Error(data.error));
          } else {
            observer.next(data.activeImports);
          }
        } catch (error) {
          observer.error(error);
        }
      });

      // Request active imports
      this.stompClient.publish({
        destination: '/app/import/progress/active',
        body: JSON.stringify({})
      });

      // Cleanup
      return () => subscription.unsubscribe();
    });
  }

  /**
   * Gets the current connection status
   */
  getConnectionStatus(): Observable<'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR'> {
    return this.connectionStatus$.asObservable();
  }

  /**
   * Checks if WebSocket is connected
   */
  isConnected(): boolean {
    return this.stompClient?.connected ?? false;
  }

  /**
   * Clears all active subscriptions
   */
  private clearSubscriptions(): void {
    this.subscriptions.forEach((subscription, key) => {
      subscription.unsubscribe();
      console.log(`Cleared subscription: ${key}`);
    });
    this.subscriptions.clear();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.disconnect();
  }
}
