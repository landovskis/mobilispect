import { ImportsHistoryCardComponent } from './imports-history-card.component';
import { PageEvent } from '@angular/material/paginator';

const makeEvent = (pageIndex: number): PageEvent => ({
  pageIndex,
  pageSize: 25,
  length: 100,
  previousPageIndex: pageIndex - 1
});

describe('ImportsHistoryCardComponent', () => {
  let component: ImportsHistoryCardComponent;

  beforeEach(() => {
    component = new ImportsHistoryCardComponent();
  });

  it('formats file sizes', () => {
    expect(component.formatFileSize(null)).toBe('-');
    expect(component.formatFileSize(1024)).toBe('1.0 KB');
    expect(component.formatFileSize(1048576)).toBe('1.0 MB');
  });

  it('maps status to badges', () => {
    expect(component.statusToBadge('completed')).toBe('good');
    expect(component.statusToBadge('failed')).toBe('bad');
    expect(component.statusToBadge('cancelled')).toBe('mixed');
    expect(component.statusToBadge('pending')).toBe('neutral');
  });

  it('emits page changes', () => {
    const spy = spyOn(component.pageChange, 'emit');

    component.onPageChange(makeEvent(3));

    expect(spy).toHaveBeenCalledWith(3);
  });
});
