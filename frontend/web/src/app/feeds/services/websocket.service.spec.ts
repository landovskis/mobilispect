import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { WebSocketService } from './websocket.service';
import { environment } from '../../../environments/environment';

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  onopen: ((event: Event) => void) | null = null;
  onclose: ((event: CloseEvent) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  readyState = 0;
  sent: any[] = [];

  constructor(public url: string) {
    MockWebSocket.instances.push(this);
  }

  send(data: any): void {
    this.sent.push(data);
  }

  close(): void {
    this.readyState = 3;
    if (this.onclose) {
      this.onclose({ wasClean: true } as CloseEvent);
    }
  }

  triggerOpen(): void {
    this.readyState = 1;
    if (this.onopen) {
      this.onopen({} as Event);
    }
  }

  triggerClose(wasClean: boolean = true): void {
    this.readyState = 3;
    if (this.onclose) {
      this.onclose({ wasClean } as CloseEvent);
    }
  }

  triggerMessage(data: any): void {
    if (this.onmessage) {
      this.onmessage({ data } as MessageEvent);
    }
  }
}

describe('WebSocketService', () => {
  let service: WebSocketService;
  let socket$: Subject<any>;
  let originalWebSocket: any;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
    socket$ = new Subject<any>();
    Object.defineProperty(socket$, 'closed', { value: false, writable: true });
    originalWebSocket = (window as any).WebSocket;
    MockWebSocket.instances = [];
    (window as any).WebSocket = MockWebSocket;
    spyOn(console, 'log');
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  afterEach(() => {
    (window as any).WebSocket = originalWebSocket;
  });

  it('connects and updates status on open', () => {
    (service as any).reconnectAttempts = 3;

    service.connect();

    expect(MockWebSocket.instances.length).toBe(1);
    expect(MockWebSocket.instances[0].url).toBe(`${environment.wsUrl}/ws/feeds`);
    expect((service as any).connectionStatus$.value).toBe('CONNECTING');

    MockWebSocket.instances[0].triggerOpen();

    expect((service as any).connectionStatus$.value).toBe('CONNECTED');
    expect((service as any).reconnectAttempts).toBe(0);
  });

  it('skips connect when socket is already open', () => {
    (service as any).socket$ = { closed: false, complete: jasmine.createSpy('complete') };

    service.connect();

    expect(MockWebSocket.instances.length).toBe(0);
  });

  it('attempts reconnection on close', fakeAsync(() => {
    service.connect();
    MockWebSocket.instances[0].triggerOpen();
    (socket$ as any).closed = true;
    const reconnectSpy = spyOn(service, 'connect').and.callFake(() => {});

    MockWebSocket.instances[0].triggerClose();
    tick(1000);

    expect(reconnectSpy).toHaveBeenCalled();
  }));

  it('marks error when reconnection limit is reached', () => {
    service.connect();
    (service as any).reconnectAttempts = (service as any).maxReconnectAttempts;
    MockWebSocket.instances[0].triggerClose();

    expect((service as any).connectionStatus$.value).toBe('ERROR');
  });

  it('disconnects and clears socket reference', () => {
    const completeSpy = spyOn(socket$, 'complete');
    (service as any).socket$ = socket$ as any;

    service.disconnect();

    expect(completeSpy).toHaveBeenCalled();
    expect((service as any).socket$).toBeNull();
    expect((service as any).connectionStatus$.value).toBe('DISCONNECTED');
  });

  it('sends messages only when connected', () => {
    service.connect();
    const instance = MockWebSocket.instances[0];
    instance.triggerOpen();
    service.send({ type: 'PING' });

    (service as any).connectionStatus$.next('DISCONNECTED');
    service.send({ type: 'PING' });

    expect(instance.sent.length).toBe(1);
    expect(console.warn).toHaveBeenCalled();
  });

  it('filters import progress messages by id', () => {
    const sendSpy = spyOn(service, 'send');
    const received: any[] = [];

    service.subscribeToImportProgress('import-1').subscribe(message => received.push(message));

    expect(sendSpy).toHaveBeenCalledWith({
      type: 'SUBSCRIBE_IMPORT_PROGRESS',
      importId: 'import-1'
    });

    (service as any).messagesSubject$.next({
      type: 'PROGRESS_UPDATE',
      data: {
        importId: 'import-1',
        progressPercentage: 50,
        currentStep: 'Downloading'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    (service as any).messagesSubject$.next({
      type: 'PROGRESS_UPDATE',
      data: {
        importId: 'import-2',
        progressPercentage: 10,
        currentStep: 'Queued'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    expect(received.length).toBe(1);
    expect(received[0].data.importId).toBe('import-1');
  });

  it('filters import status messages by id', () => {
    const sendSpy = spyOn(service, 'send');
    const received: any[] = [];

    service.subscribeToImportStatus('import-3').subscribe(message => received.push(message));

    expect(sendSpy).toHaveBeenCalledWith({
      type: 'SUBSCRIBE_IMPORT_STATUS',
      importId: 'import-3'
    });

    (service as any).messagesSubject$.next({
      type: 'IMPORT_STATUS',
      data: {
        importId: 'import-3',
        status: 'COMPLETED'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    (service as any).messagesSubject$.next({
      type: 'IMPORT_STATUS',
      data: {
        importId: 'import-4',
        status: 'FAILED'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    expect(received.length).toBe(1);
    expect(received[0].data.importId).toBe('import-3');
  });

  it('filters system alert messages', () => {
    const received: any[] = [];

    service.subscribeToSystemAlerts().subscribe(message => received.push(message));

    (service as any).messagesSubject$.next({
      type: 'SYSTEM_ALERT',
      data: {
        level: 'WARNING',
        message: 'Lag detected'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    (service as any).messagesSubject$.next({
      type: 'IMPORT_STATUS',
      data: {
        importId: 'import-5',
        status: 'FAILED'
      },
      timestamp: '2024-01-01T00:00:00Z'
    });

    expect(received.length).toBe(1);
    expect(received[0].type).toBe('SYSTEM_ALERT');
  });

  it('exposes connection status and message streams', () => {
    const statuses: string[] = [];
    const messages: any[] = [];

    service.getConnectionStatus().subscribe(status => statuses.push(status));
    service.getAllMessages().subscribe(message => messages.push(message));

    (service as any).connectionStatus$.next('CONNECTED');
    (service as any).messagesSubject$.next({
      type: 'SYSTEM_ALERT',
      data: { level: 'INFO', message: 'Ready' },
      timestamp: '2024-01-01T00:00:00Z'
    });

    expect(statuses[statuses.length - 1]).toBe('CONNECTED');
    expect(messages.length).toBe(1);
  });

  it('emits heartbeat when connected', fakeAsync(() => {
    const sendSpy = spyOn(service, 'send');
    (service as any).connectionStatus$.next('CONNECTED');

    service.startHeartbeat();
    tick(0);

    expect(sendSpy).toHaveBeenCalledWith(jasmine.objectContaining({
      type: 'HEARTBEAT'
    }));
  }));

  it('cleans up on destroy', () => {
    const disconnectSpy = spyOn(service, 'disconnect');

    service.ngOnDestroy();

    expect(disconnectSpy).toHaveBeenCalled();
  });

  it('calculates exponential reconnect delay', () => {
    expect((service as any).getReconnectDelay(1)).toBe(1000);
    expect((service as any).getReconnectDelay(2)).toBe(2000);
    expect((service as any).getReconnectDelay(5)).toBe(16000);
  });
});
