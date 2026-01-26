import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { RouteVariantCardComponent } from './route-variant-card.component';
import { RouteVariantDto } from '../../services/frequency.service';

describe('RouteVariantCardComponent', () => {
  let component: RouteVariantCardComponent;

  const createVariant = (overrides: Partial<RouteVariantDto> = {}): RouteVariantDto => ({
    id: 'variant-1',
    routeId: 'route-1',
    directionId: 0,
    headsign: 'Downtown',
    stopCount: 5,
    stopPattern: 'A|B|C|D|E',
    stopNames: ['Stop A', 'Stop B', 'Stop C', 'Stop D', 'Stop E'],
    stopSpacingsMeters: [500, 600, 700, 800],
    firstStopId: 'stop-a',
    lastStopId: 'stop-e',
    firstDepartureTime: '06:00',
    lastDepartureTime: '22:00',
    scheduleTripCount: 32,
    classification: 'LOCAL',
    averageStopSpacingMeters: 650,
    clockFaceIntervalMinutes: null,
    ...overrides,
  });

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RouteVariantCardComponent, HttpClientTestingModule],
    }).compileComponents();

    const fixture = TestBed.createComponent(RouteVariantCardComponent);
    component = fixture.componentInstance;
    component.variant = createVariant();
    fixture.detectChanges();
  });

  describe('clock-face badge', () => {
    it('returns null when clockFaceIntervalMinutes is null', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: null });
      expect(component.clockFaceLabel).toBeNull();
    });

    it('returns null when clockFaceIntervalMinutes is undefined', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: undefined });
      expect(component.clockFaceLabel).toBeNull();
    });

    it('returns formatted label for 10-minute interval', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: 10 });
      expect(component.clockFaceLabel).toBe('Every 10 min');
    });

    it('returns formatted label for 15-minute interval', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: 15 });
      expect(component.clockFaceLabel).toBe('Every 15 min');
    });

    it('returns formatted label for 30-minute interval', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: 30 });
      expect(component.clockFaceLabel).toBe('Every 30 min');
    });

    it('returns formatted label for 60-minute interval', () => {
      component.variant = createVariant({ clockFaceIntervalMinutes: 60 });
      expect(component.clockFaceLabel).toBe('Every 60 min');
    });
  });

  describe('stopNames', () => {
    it('returns stop names from variant', () => {
      component.variant = createVariant({
        stopNames: ['First', 'Second', 'Third'],
      });
      expect(component.stopNames).toEqual(['First', 'Second', 'Third']);
    });

    it('falls back to stop pattern when stopNames is empty', () => {
      component.variant = createVariant({
        stopNames: [],
        stopPattern: 'A|B|C',
      });
      expect(component.stopNames).toEqual(['A', 'B', 'C']);
    });
  });

  describe('stopSpacingLabel', () => {
    it('returns meters for small distances', () => {
      component.variant = createVariant({
        stopSpacingsMeters: [500, 750],
      });
      expect(component.stopSpacingLabel(0)).toBe('500 m');
      expect(component.stopSpacingLabel(1)).toBe('750 m');
    });

    it('returns kilometers for large distances', () => {
      component.variant = createVariant({
        stopSpacingsMeters: [1500, 2500],
      });
      expect(component.stopSpacingLabel(0)).toBe('1.50 km');
      expect(component.stopSpacingLabel(1)).toBe('2.50 km');
    });

    it('returns null for invalid spacing', () => {
      component.variant = createVariant({
        stopSpacingsMeters: [0, -100, NaN],
      });
      expect(component.stopSpacingLabel(0)).toBeNull();
      expect(component.stopSpacingLabel(1)).toBeNull();
      expect(component.stopSpacingLabel(2)).toBeNull();
    });
  });

  describe('formatSchedule', () => {
    it('formats schedule with times and trip count', () => {
      component.variant = createVariant({
        firstDepartureTime: '06:30',
        lastDepartureTime: '22:15',
        scheduleTripCount: 48,
      });
      expect(component.formatSchedule(component.variant)).toBe('6:30 AM - 10:15 PM (48 trips)');
    });

    it('returns not available when times are missing', () => {
      component.variant = createVariant({
        firstDepartureTime: null,
        lastDepartureTime: null,
      });
      expect(component.formatSchedule(component.variant)).toBe('Schedule: Not available');
    });
  });

  describe('formatClassification', () => {
    it('formats single word classification', () => {
      expect(component.formatClassification('LOCAL')).toBe('Local');
      expect(component.formatClassification('EXPRESS')).toBe('Express');
    });

    it('formats multi-word classification', () => {
      expect(component.formatClassification('REGIONAL_EXPRESS')).toBe('Regional Express');
    });
  });
});
