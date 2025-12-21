import { BrandSectionComponent } from './brand-section.component';

describe('BrandSectionComponent', () => {
  it('does nothing when toggled while not collapsible', () => {
    const component = new BrandSectionComponent();
    component.collapsible = false;
    component.expanded = true;

    const emitSpy = spyOn(component.expandedChange, 'emit');
    component.toggle();

    expect(component.expanded).toBeTrue();
    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('toggles expanded state when collapsible', () => {
    const component = new BrandSectionComponent();
    component.collapsible = true;
    component.expanded = true;

    const emitSpy = spyOn(component.expandedChange, 'emit');
    component.toggle();

    expect(component.expanded).toBeFalse();
    expect(emitSpy).toHaveBeenCalledWith(false);
  });
});
