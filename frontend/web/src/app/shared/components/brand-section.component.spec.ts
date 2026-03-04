import { vi } from 'vitest';
import { BrandSectionComponent } from './brand-section.component';

describe('BrandSectionComponent', () => {
  it('does nothing when toggled while not collapsible', () => {
    const component = new BrandSectionComponent();
    component.collapsible = false;
    component.expanded = true;

    const emitSpy = vi.spyOn(component.expandedChange, 'emit');
    component.toggle();

    expect(component.expanded).toBe(true);
    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('toggles expanded state when collapsible', () => {
    const component = new BrandSectionComponent();
    component.collapsible = true;
    component.expanded = true;

    const emitSpy = vi.spyOn(component.expandedChange, 'emit');
    component.toggle();

    expect(component.expanded).toBe(false);
    expect(emitSpy).toHaveBeenCalledWith(false);
  });
});
