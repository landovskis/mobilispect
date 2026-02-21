---
name: new-component
description: Scaffold a new Angular component following project conventions. Use when user says "new component", "create component", "add component", or "scaffold component".
disable-model-invocation: true
---

# Scaffold Angular Component

Create a new Angular component following Mobilispect frontend conventions.

## Workflow

### Step 1: Gather Information

Ask the user for:
1. **Feature module**: Which module? (agencies, feeds, regions, routes, shared)
2. **Component type**: Is this a page or a reusable component?
3. **Name**: Component name in kebab-case (e.g., `feed-status-card`)

### Step 2: Determine File Location

Based on the answers:
- **Page**: `frontend/web/src/app/{module}/pages/{name}/`
- **Component**: `frontend/web/src/app/{module}/components/{name}/`
- **Shared component**: `frontend/web/src/app/shared/components/{name}/`

### Step 3: Create the Component File

Create `{name}.component.ts`:

```typescript
import { ChangeDetectionStrategy, Component } from '@angular/core';

@Component({
  selector: 'app-{name}',
  standalone: true,
  template: `
    <div>
      <!-- TODO: implement template -->
    </div>
  `,
  styles: [],
  imports: [],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class {PascalName}Component {
}
```

Project conventions to follow:
- **Standalone components** (no NgModules)
- **Inline templates** for small components (single file)
- **OnPush change detection** always
- **`app-` prefix** on selectors
- **kebab-case** filenames and directories
- **PascalCase** class names
- Import from `@angular/material` for Material Design components
- Use Tailwind CSS utility classes for styling

### Step 4: Create the Test File

Create `{name}.component.spec.ts`:

```typescript
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { {PascalName}Component } from './{name}.component';

describe('{PascalName}Component', () => {
  let component: {PascalName}Component;
  let fixture: ComponentFixture<{PascalName}Component>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [{PascalName}Component],
    }).compileComponents();

    fixture = TestBed.createComponent({PascalName}Component);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

### Step 5: Wire Up (if page)

If creating a page component:
1. Add a route entry to the module's routes file (`{module}/{module}.routes.ts` or `app.routes.ts`)
2. Add a resolver if the page needs data pre-loading (follow `resolvers/` pattern)

### Step 6: Confirm

Report the created files and remind the user to:
1. Run `cd frontend/web && npm test` to verify the test passes
2. Run `cd frontend/web && npm run lint` to verify lint compliance