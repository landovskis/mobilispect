import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class FeedsEventsService {
  private readonly refreshSubject = new Subject<void>();

  readonly refresh$ = this.refreshSubject.asObservable();

  triggerRefresh(): void {
    this.refreshSubject.next();
  }
}
