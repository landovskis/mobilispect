import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterModule } from '@angular/router';
import { CorridorsListComponent } from './corridors-list.component';
import { CorridorDto } from '../../services/corridor.service';

describe('CorridorsListComponent', () => {
  let component: CorridorsListComponent;
  let fixture: ComponentFixture<CorridorsListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [CorridorsListComponent, RouterModule.forRoot([])],
    }).compileComponents();

    fixture = TestBed.createComponent(CorridorsListComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  it('should show empty message when no corridors', () => {
    component.corridors = [];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.empty')?.textContent).toContain(
      'No corridors detected',
    );
  });

  it('should render corridor cards for each corridor', () => {
    const corridors: CorridorDto[] = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: '10', longName: 'Route 10' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
      {
        id: '2',
        stopPattern: 's4|s5|s6|s7',
        stopCount: 4,
        firstStopId: 's4',
        lastStopId: 's7',
        routes: [
          { routeId: 'r-3', shortName: '30', longName: 'Route 30' },
          { routeId: 'r-4', shortName: null, longName: 'Route 40' },
        ],
      },
    ];

    component.corridors = corridors;
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const cards = compiled.querySelectorAll('.corridor-card');
    expect(cards.length).toBe(2);
  });

  it('should display stop count and route count', () => {
    component.corridors = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: '10', longName: 'Route 10' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
    ];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.title')?.textContent).toContain(
      '3 shared stops',
    );
    expect(compiled.querySelector('.chip')?.textContent).toContain('2 routes');
  });

  it('should render route tags with links', () => {
    component.corridors = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: '10', longName: 'Route 10' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
    ];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const tags = compiled.querySelectorAll('.route-tag');
    expect(tags.length).toBe(2);
    expect(tags[0].textContent?.trim()).toBe('10');
    expect(tags[1].textContent?.trim()).toBe('20');
  });

  it('should use longName when shortName is null', () => {
    component.corridors = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: null, longName: 'Route Express' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
    ];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const tags = compiled.querySelectorAll('.route-tag');
    expect(tags[0].textContent?.trim()).toBe('Route Express');
  });

  it('should display stop pattern', () => {
    component.corridors = [
      {
        id: '1',
        stopPattern: 's1|s2|s3',
        stopCount: 3,
        firstStopId: 's1',
        lastStopId: 's3',
        routes: [
          { routeId: 'r-1', shortName: '10', longName: 'Route 10' },
          { routeId: 'r-2', shortName: '20', longName: 'Route 20' },
        ],
      },
    ];
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.pattern')?.textContent).toContain(
      's1|s2|s3',
    );
  });
});
