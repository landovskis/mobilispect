import { RegionSelectorComponent } from './region-selector.component';
import { MetropolitanRegion } from '../../feeds/models/region.models';
import { SimpleChange } from '@angular/core';

describe('RegionSelectorComponent', () => {
  let component: RegionSelectorComponent;

  const baseRegion: MetropolitanRegion = {
    regionOnestopId: 'r-1',
    name: 'Test Region',
    adm0Name: 'United States',
    adm1Name: 'California',
    autoUpdateEnabled: false,
    feedCount: 2,
    lastCheckAt: null,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
  };

  beforeEach(() => {
    component = new RegionSelectorComponent();
    component.regions = [
      baseRegion,
      { ...baseRegion, regionOnestopId: 'r-2', name: 'Austin', adm1Name: 'Texas' },
    ];
  });

  it('filters regions by search term', () => {
    const result = (component as any)._filterRegions('aus') as MetropolitanRegion[];

    expect(result.length).toBe(1);
    expect(result[0].regionOnestopId).toBe('r-2');
  });

  it('selects regions and emits changes', () => {
    const emitSpy = spyOn(component.regionChange, 'emit');

    component.onRegionSelected(baseRegion);

    expect(component.searchControl.value).toBe('Test Region, California, United States');
    expect(emitSpy).toHaveBeenCalledWith('r-1');
  });

  it('clears search and emits empty selection', () => {
    const emitSpy = spyOn(component.regionChange, 'emit');

    component.clearSearch();

    expect(component.searchControl.value).toBe('');
    expect(emitSpy).toHaveBeenCalledWith('');
  });

  it('syncs selected region label on changes', () => {
    component.selectedRegionId = 'r-1';
    component.ngOnInit();

    component.ngOnChanges({
      selectedRegionId: new SimpleChange(null, 'r-1', false),
    });

    expect(component.searchControl.value).toBe('Test Region, California, United States');
  });

  it('updates control state when disabled toggles', () => {
    component.disabled = true;
    component.ngOnChanges({
      disabled: new SimpleChange(false, true, false),
    });

    expect(component.searchControl.disabled).toBeTrue();

    component.disabled = false;
    component.ngOnChanges({
      disabled: new SimpleChange(true, false, false),
    });

    expect(component.searchControl.enabled).toBeTrue();
  });
});
