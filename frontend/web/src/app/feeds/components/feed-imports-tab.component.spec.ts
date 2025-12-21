import { FeedImportsTabComponent } from './feed-imports-tab.component';

describe('FeedImportsTabComponent', () => {
  let component: FeedImportsTabComponent;

  beforeEach(() => {
    component = new FeedImportsTabComponent();
  });

  it('emits page changes', () => {
    const spy = spyOn(component.pageChange, 'emit');

    component.onPageChange(2);

    expect(spy).toHaveBeenCalledWith(2);
  });

  it('emits cancel events', () => {
    const spy = spyOn(component.cancelImport, 'emit');

    component.onCancelImport('imp-2');

    expect(spy).toHaveBeenCalledWith('imp-2');
  });

  it('reports active imports as false by default', () => {
    expect(component.hasActiveImports).toBeFalse();
  });
});
