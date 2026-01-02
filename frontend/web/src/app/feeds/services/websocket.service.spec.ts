import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';

class MockStompClient {
  connected = false;
  activate = jasmine.createSpy('activate');
  deactivate = jasmine.createSpy('deactivate');
  publish = jasmine.createSpy('publish');
  subscribe = jasmine.createSpy('subscribe').and.callFake((_topic: string, callback: (message: any) => void) => {
    this.lastSubscriptionCallback = callback;
    const subscription = { unsubscribe: jasmine.createSpy('unsubscribe') };
    this.subscriptions.push(subscription);
    return subscription as any;
  });
  lastSubscriptionCallback?: (message: any) => void;
  subscriptions: Array<{ unsubscribe: jasmine.Spy }> = [];

  constructor(public config: any) {}

  triggerConnect(frame: any = {}): void {
    this.connected = true;
    this.config.onConnect?.(frame);
  }

  triggerStompError(frame: any = {}): void {
    this.config.onStompError?.(frame);
  }

  triggerWebSocketClose(event: any = {}): void {
    this.config.onWebSocketClose?.(event);
  }

  triggerWebSocketError(event: any = {}): void {
    this.config.onWebSocketError?.(event);
  }

  triggerDisconnect(frame: any = {}): void {
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

    spyOn(service as any, 'buildStompClient').and.callFake((config: any) => {
      createdClient = new MockStompClient(config);
      return createdClient as any;
    });

    spyOn(console, 'log');
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  it('connects and activates a stomp client', () => {
    service.connect();

    expect(createdClient).not.toBeNull();
    expect((service as any).connectionStatus$.value).toBe('CONNECTING');
    expect(createdClient?.activate).toHaveBeenCalled();

    createdClient?.triggerConnect();

    expect((service as any).connectionStatus$.value).toBe('CONNECTED');
  });

  it('skips connect when already connected', () => {
    (service as any).stompClient = { connected: true };

    service.connect();

    expect((service as any).buildStompClient).not.toHaveBeenCalled();
  });

  it('disconnects and clears subscriptions', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (service as any).stompClient = mockClient as any;
    const progressSub = { unsubscribe: jasmine.createSpy('unsubscribe') };
    const statusSub = { unsubscribe: jasmine.createSpy('unsubscribe') };
    (service as any).subscriptions = new Map([
      ['/topic/import/progress/import-1', progressSub],
      ['/topic/import/status/import-1', statusSub]
    ]);

    service.disconnect();

    expect(progressSub.unsubscribe).toHaveBeenCalled();
    expect(statusSub.unsubscribe).toHaveBeenCalled();
    expect(mockClient.deactivate).toHaveBeenCalled();
    expect((service as any).stompClient).toBeNull();
    expect((service as any).connectionStatus$.value).toBe('DISCONNECTED');
  });

  it('publishes messages when connected', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (service as any).stompClient = mockClient as any;

    service.send('/app/ping', { type: 'PING' });

    expect(mockClient.publish).toHaveBeenCalledWith({
      destination: '/app/ping',
      body: JSON.stringify({ type: 'PING' })
    });
  });

  it('warns when sending while disconnected', () => {
    const mockClient = new MockStompClient({});
    (service as any).stompClient = mockClient as any;

    service.send('/app/ping', { type: 'PING' });

    expect(console.warn).toHaveBeenCalled();
  });

  it('subscribes to import progress after connection', () => {
    const mockClient = new MockStompClient({});
    (service as any).stompClient = mockClient as any;
    (service as any).connectionStatus$.next('CONNECTED');
    const received: any[] = [];

    service.subscribeToImportProgress('import-1').subscribe(message => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith('/topic/import/progress/import-1', jasmine.any(Function));

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    callback({ body: JSON.stringify({ progress: { importId: 'import-1' } }) });

    expect(received.length).toBe(1);
    expect(received[0].progress.importId).toBe('import-1');
  });

  it('subscribes to import status after connection', () => {
    const mockClient = new MockStompClient({});
    (service as any).stompClient = mockClient as any;
    (service as any).connectionStatus$.next('CONNECTED');
    const received: any[] = [];

    service.subscribeToImportStatus('import-3').subscribe(message => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith('/topic/import/status/import-3', jasmine.any(Function));

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    callback({ body: JSON.stringify({ type: 'IMPORT_STATUS', data: { importId: 'import-3', status: 'COMPLETED' } }) });

    expect(received.length).toBe(1);
    expect(received[0].data.importId).toBe('import-3');
  });

  it('subscribes to system alerts after connection', () => {
    const mockClient = new MockStompClient({});
    (service as any).stompClient = mockClient as any;
    (service as any).connectionStatus$.next('CONNECTED');
    const received: any[] = [];

    service.subscribeToSystemAlerts().subscribe(message => received.push(message));

    expect(mockClient.subscribe).toHaveBeenCalledWith('/topic/system/alerts', jasmine.any(Function));

    const callback = mockClient.subscribe.calls.mostRecent().args[1];
    callback({ body: JSON.stringify({ type: 'SYSTEM_ALERT', data: { level: 'INFO', message: 'Ready' } }) });

    expect(received.length).toBe(1);
    expect(received[0].type).toBe('SYSTEM_ALERT');
  });

  it('unsubscribes from import topics', () => {
    const progressSub = { unsubscribe: jasmine.createSpy('unsubscribe') };
    const statusSub = { unsubscribe: jasmine.createSpy('unsubscribe') };
    (service as any).subscriptions = new Map([
      ['/topic/import/progress/import-1', progressSub],
      ['/topic/import/status/import-1', statusSub]
    ]);

    service.unsubscribeFromImport('import-1');

    expect(progressSub.unsubscribe).toHaveBeenCalled();
    expect(statusSub.unsubscribe).toHaveBeenCalled();
  });

  it('returns connection status from isConnected', () => {
    const mockClient = new MockStompClient({});
    mockClient.connected = true;
    (service as any).stompClient = mockClient as any;

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
