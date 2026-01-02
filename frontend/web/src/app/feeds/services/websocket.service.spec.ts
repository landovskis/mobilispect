import { TestBed } from '@angular/core/testing';
import { IMessage, StompSubscription } from '@stomp/stompjs';
import {
  ImportStatusMessage,
  ProgressUpdateMessage,
  SystemAlertMessage,
  WebSocketService,
} from './websocket.service';

interface StompConfig {
  onConnect?: (frame: unknown) => void;
  onStompError?: (frame: unknown) => void;
  onWebSocketClose?: (event: unknown) => void;
  onWebSocketError?: (event: unknown) => void;
  onDisconnect?: (frame: unknown) => void;
}

interface MockSubscription {
  id: string;
  unsubscribe: jasmine.Spy;
}

class MockStompClient {
  connected = false;
  activate = jasmine.createSpy('activate');
  deactivate = jasmine.createSpy('deactivate');
  publish = jasmine.createSpy('publish');
  subscribe = jasmine
    .createSpy('subscribe')
    .and.callFake((_topic: string, callback: (message: IMessage) => void) => {
      this.lastSubscriptionCallback = callback;
      const subscription: MockSubscription = {
        id: `sub-${this.subscriptions.length + 1}`,
        unsubscribe: jasmine.createSpy('unsubscribe'),
      };
      this.subscriptions.push(subscription);
      return subscription as StompSubscription;
    });
  lastSubscriptionCallback?: (message: IMessage) => void;
  subscriptions: MockSubscription[] = [];

  constructor(public config: StompConfig) {}

  triggerConnect(frame: unknown = {}): void {
    this.connected = true;
    this.config.onConnect?.(frame);
  }

  triggerStompError(frame: unknown = {}): void {
    this.config.onStompError?.(frame);
  }

  triggerWebSocketClose(event: unknown = {}): void {
    this.config.onWebSocketClose?.(event);
  }

  triggerWebSocketError(event: unknown = {}): void {
    this.config.onWebSocketError?.(event);
  }

  triggerDisconnect(frame: unknown = {}): void {
    this.config.onDisconnect?.(frame);
  }
}

describe('WebSocketService', () => {
  let service: WebSocketService;
  let createdClient: MockStompClient | null;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
    createdClient = null;

    const serviceHarness = service as unknown as {
      buildStompClient: (config: StompConfig) => MockStompClient;
    };
    spyOn(serviceHarness, 'buildStompClient').and.callFake(
      (config: StompConfig) => {
        createdClient = new MockStompClient(config);
        return createdClient;
      },
    );

    spyOn(console, 'log');
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  it('connects and activates a stomp client', () => {
    service.connect();

    expect(createdClient).not.toBeNull();
    expect(
      (service as unknown as { connectionStatus$: { value: string } })
        .connectionStatus$.value,
    ).toBe('CONNECTING');
    expect(createdClient?.activate).toHaveBeenCalled();

    createdClient?.triggerConnect();

    expect(
      (service as unknown as { connectionStatus$: { value: string } })
        .connectionStatus$.value,
    ).toBe('CONNECTED');
  });

  it('skips connect when already connected', () => {
    (
      service as unknown as {
        stompClient: { connected: boolean; deactivate: jasmine.Spy } | null;
      }
    ).stompClient = {
      connected: true,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.connect();

    expect(
      (service as unknown as { buildStompClient: jasmine.Spy })
        .buildStompClient,
    ).not.toHaveBeenCalled();
  });

  it('disconnects and clears subscriptions', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;
    const progressSub: MockSubscription = {
      id: 'sub-progress',
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    const statusSub: MockSubscription = {
      id: 'sub-status',
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    (
      service as unknown as { subscriptions: Map<string, MockSubscription> }
    ).subscriptions = new Map([
      ['/topic/import/progress/import-1', progressSub],
      ['/topic/import/status/import-1', statusSub],
    ]);

    service.disconnect();

    expect(progressSub.unsubscribe).toHaveBeenCalled();
    expect(statusSub.unsubscribe).toHaveBeenCalled();
    expect(mockClient.deactivate).toHaveBeenCalled();
    expect(
      (service as unknown as { stompClient: MockStompClient | null })
        .stompClient,
    ).toBeNull();
    expect(
      (service as unknown as { connectionStatus$: { value: string } })
        .connectionStatus$.value,
    ).toBe('DISCONNECTED');
  });

  it('publishes messages when connected', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;

    service.send('/app/ping', { type: 'PING' });

    expect(mockClient.publish).toHaveBeenCalledWith({
      destination: '/app/ping',
      body: JSON.stringify({ type: 'PING' }),
    });
  });

  it('warns when sending while disconnected', () => {
    const mockClient = new MockStompClient({});
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;

    service.send('/app/ping', { type: 'PING' });

    expect(console.warn).toHaveBeenCalled();
  });

  it('subscribes to import progress after connection', () => {
    const mockClient = new MockStompClient({});
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;
    (
      service as unknown as {
        connectionStatus$: { next: (value: string) => void };
      }
    ).connectionStatus$.next('CONNECTED');
    const received: ProgressUpdateMessage[] = [];

    service
      .subscribeToImportProgress('import-1')
      .subscribe((message) => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith(
      '/topic/import/progress/import-1',
      jasmine.any(Function),
    );

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    const message = {
      body: JSON.stringify({ progress: { importId: 'import-1' } }),
    } as IMessage;
    callback(message);

    expect(received.length).toBe(1);
    expect(received[0].progress?.importId).toBe('import-1');
  });

  it('subscribes to import status after connection', () => {
    const mockClient = new MockStompClient({});
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;
    (
      service as unknown as {
        connectionStatus$: { next: (value: string) => void };
      }
    ).connectionStatus$.next('CONNECTED');
    const received: ImportStatusMessage[] = [];

    service
      .subscribeToImportStatus('import-3')
      .subscribe((message) => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith(
      '/topic/import/status/import-3',
      jasmine.any(Function),
    );

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    const message = {
      body: JSON.stringify({
        type: 'IMPORT_STATUS',
        data: { importId: 'import-3', status: 'COMPLETED' },
      }),
    } as IMessage;
    callback(message);

    expect(received.length).toBe(1);
    expect(received[0].data.importId).toBe('import-3');
  });

  it('subscribes to system alerts after connection', () => {
    const mockClient = new MockStompClient({});
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;
    (
      service as unknown as {
        connectionStatus$: { next: (value: string) => void };
      }
    ).connectionStatus$.next('CONNECTED');
    const received: SystemAlertMessage[] = [];

    service
      .subscribeToSystemAlerts()
      .subscribe((message) => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith(
      '/topic/system/alerts',
      jasmine.any(Function),
    );

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    const message = {
      body: JSON.stringify({
        type: 'SYSTEM_ALERT',
        data: { level: 'INFO', message: 'Ready' },
      }),
    } as IMessage;
    callback(message);

    expect(received.length).toBe(1);
    expect(received[0].type).toBe('SYSTEM_ALERT');
  });

  it('unsubscribes from import topics', () => {
    const progressSub: MockSubscription = {
      id: 'sub-progress',
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    const statusSub: MockSubscription = {
      id: 'sub-status',
      unsubscribe: jasmine.createSpy('unsubscribe'),
    };
    (
      service as unknown as { subscriptions: Map<string, MockSubscription> }
    ).subscriptions = new Map([
      ['/topic/import/progress/import-1', progressSub],
      ['/topic/import/status/import-1', statusSub],
    ]);

    service.unsubscribeFromImport('import-1');

    expect(progressSub.unsubscribe).toHaveBeenCalled();
    expect(statusSub.unsubscribe).toHaveBeenCalled();
  });

  it('returns connection status from isConnected', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (
      service as unknown as { stompClient: MockStompClient | null }
    ).stompClient = mockClient;

    expect(service.isConnected()).toBe(true);
  });

  it('logs when starting heartbeat', () => {
    service.startHeartbeat();

    expect(console.log).toHaveBeenCalled();
  });

  it('cleans up on destroy', () => {
    const disconnectSpy = spyOn(service, 'disconnect');

    service.ngOnDestroy();

    expect(disconnectSpy).toHaveBeenCalled();
  });
});
