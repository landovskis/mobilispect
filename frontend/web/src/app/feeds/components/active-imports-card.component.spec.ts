import { vi } from 'vitest';
import { ActiveImportsCardComponent } from './active-imports-card.component';

describe('ActiveImportsCardComponent', () => {
  it('emits cancel events', () => {
    const component = new ActiveImportsCardComponent();
    const spy = vi.spyOn(component.cancelImport, 'emit');

    component.onCancelImport('imp-1');

    expect(spy).toHaveBeenCalledWith('imp-1');
  });
});
