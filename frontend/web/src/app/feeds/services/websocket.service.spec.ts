import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';

describe('WebSocketService', () => {
  let service: WebSocketService;
  let internals: WebSocketServiceInternals;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
    internals = service as unknown as WebSocketServiceInternals;
    spyOn(console, 'log');
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  it('publishes messages when connected', () => {
    const publishSpy = jasmine.createSpy('publish');
    internals.stompClient = {
      connected: true,
      publish: publishSpy,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.send('/topic/test', { type: 'PING' });

    expect(publishSpy).toHaveBeenCalledWith({
      destination: '/topic/test',
      body: JSON.stringify({ type: 'PING' }),
    });
  });

  it('warns when publishing while disconnected', () => {
    const publishSpy = jasmine.createSpy('publish');
    internals.stompClient = {
      connected: false,
      publish: publishSpy,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.send('/topic/test', { type: 'PING' });

    expect(publishSpy).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalled();
  });

  it('subscribes to import progress when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine
      .createSpy('subscribe')
      .and.returnValue({ unsubscribe: unsubscribeSpy });
    internals.stompClient = {
      subscribe: subscribeSpy,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.subscribeToImportProgress('import-1');
    internals.connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith(
      '/topic/import/progress/import-1',
      jasmine.any(Function),
    );
  });

  it('subscribes to import status when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine
      .createSpy('subscribe')
      .and.returnValue({ unsubscribe: unsubscribeSpy });
    internals.stompClient = {
      subscribe: subscribeSpy,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.subscribeToImportStatus('import-2');
    internals.connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith(
      '/topic/import/status/import-2',
      jasmine.any(Function),
    );
  });

  it('subscribes to system alerts when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine
      .createSpy('subscribe')
      .and.returnValue({ unsubscribe: unsubscribeSpy });
    internals.stompClient = {
      subscribe: subscribeSpy,
      deactivate: jasmine.createSpy('deactivate'),
    };

    service.subscribeToSystemAlerts();
    internals.connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith(
      '/topic/system/alerts',
      jasmine.any(Function),
    );
  });

  it('unsubscribes from import topics', () => {
    const unsubscribeProgress = jasmine.createSpy('unsubscribe');
    const unsubscribeStatus = jasmine.createSpy('unsubscribe');
    internals.subscriptions.set('/topic/import/progress/import-9', {
      unsubscribe: unsubscribeProgress,
    });
    internals.subscriptions.set('/topic/import/status/import-9', {
      unsubscribe: unsubscribeStatus,
    });

    service.unsubscribeFromImport('import-9');

    expect(unsubscribeProgress).toHaveBeenCalled();
    expect(unsubscribeStatus).toHaveBeenCalled();
  });
});

type WebSocketServiceInternals = {
  stompClient: {
    connected?: boolean;
    publish?: (args: { destination: string; body: string }) => void;
    subscribe?: (
      topic: string,
      callback: (message: unknown) => void,
    ) => { unsubscribe: () => void };
    deactivate?: () => void;
  } | null;
  connectionStatus$: {
    next: (
      status: 'CONNECTING' | 'CONNECTED' | 'DISCONNECTED' | 'ERROR',
    ) => void;
  };
  subscriptions: Map<string, { unsubscribe: () => void }>;
};
