import { TestBed } from '@angular/core/testing';
import { WebSocketService } from './websocket.service';

describe('WebSocketService', () => {
  let service: WebSocketService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(WebSocketService);
    spyOn(console, 'log');
    spyOn(console, 'warn');
    spyOn(console, 'error');
  });

  it('publishes messages when connected', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).stompClient = { connected: true, publish: publishSpy };

    service.send('/topic/test', { type: 'PING' });

    expect(publishSpy).toHaveBeenCalledWith({
      destination: '/topic/test',
      body: JSON.stringify({ type: 'PING' })
    });
  });

  it('warns when publishing while disconnected', () => {
    const publishSpy = jasmine.createSpy('publish');
    (service as any).stompClient = { connected: false, publish: publishSpy };

    service.send('/topic/test', { type: 'PING' });

    expect(publishSpy).not.toHaveBeenCalled();
    expect(console.warn).toHaveBeenCalled();
  });

  it('subscribes to import progress when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine.createSpy('subscribe').and.returnValue({ unsubscribe: unsubscribeSpy });
    (service as any).stompClient = { subscribe: subscribeSpy };

    service.subscribeToImportProgress('import-1');
    (service as any).connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith('/topic/import/progress/import-1', jasmine.any(Function));
  });

  it('subscribes to import status when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine.createSpy('subscribe').and.returnValue({ unsubscribe: unsubscribeSpy });
    (service as any).stompClient = { subscribe: subscribeSpy };

    service.subscribeToImportStatus('import-2');
    (service as any).connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith('/topic/import/status/import-2', jasmine.any(Function));
  });

  it('subscribes to system alerts when connected', () => {
    const unsubscribeSpy = jasmine.createSpy('unsubscribe');
    const subscribeSpy = jasmine.createSpy('subscribe').and.returnValue({ unsubscribe: unsubscribeSpy });
    (service as any).stompClient = { subscribe: subscribeSpy };

    service.subscribeToSystemAlerts();
    (service as any).connectionStatus$.next('CONNECTED');

    expect(subscribeSpy).toHaveBeenCalledWith('/topic/system/alerts', jasmine.any(Function));
  });

  it('unsubscribes from import topics', () => {
    const unsubscribeProgress = jasmine.createSpy('unsubscribe');
    const unsubscribeStatus = jasmine.createSpy('unsubscribe');
    (service as any).subscriptions.set('/topic/import/progress/import-9', { unsubscribe: unsubscribeProgress });
    (service as any).subscriptions.set('/topic/import/status/import-9', { unsubscribe: unsubscribeStatus });

    service.unsubscribeFromImport('import-9');

    expect(unsubscribeProgress).toHaveBeenCalled();
    expect(unsubscribeStatus).toHaveBeenCalled();
  });
});
