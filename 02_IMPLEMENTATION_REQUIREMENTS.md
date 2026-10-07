# MedMaint — Implementation Requirements

## 1. Technology and Architecture

Use Freebuff's supported full-stack architecture.

Preferred:

- React
- TypeScript
- Professional component/UI system
- Freebuff-supported backend
- Freebuff-supported database
- Secure authentication
- File storage
- Responsive frontend

Keep the architecture modular.

Do not put the entire application into one component.

Use:

- Reusable components
- Reusable forms
- Reusable tables
- Reusable dialogs
- Data/service layers
- Validation
- Clear folder structure
- Consistent naming

The application must work well on desktop, laptop and tablet.

---

## 2. Authentication and Authorization

Implement real authentication.

Login page:

- MedMaint branding
- Email/username
- Password
- Show/hide password
- Remember me
- Login
- Validation
- Error messages

Roles:

- Administrateur
- Responsable Maintenance
- Ingénieur Biomédical
- Technicien
- Responsable de Service
- Demandeur / Utilisateur

Authorization must be enforced at the backend/data level where supported.

Do not rely only on hiding frontend buttons.

Protect routes.

---

## 3. Application Layout

Use:

### Sidebar

- Tableau de bord
- Équipements
- Demandes d'intervention
- Interventions
- Maintenance préventive
- Techniciens
- Services
- Pièces de rechange
- Fournisseurs
- Contrats
- Documents
- Rapports
- Notifications
- Utilisateurs
- Journal d'audit
- Paramètres

### Top bar

- Page title
- Breadcrumb
- Global search
- Notifications
- User menu
- Role

Sidebar must be collapsible.

---

## 4. Visual Design

Create a modern enterprise healthcare interface.

Style:

- Professional
- Clean
- Technical
- Minimal
- Trustworthy
- Easy to navigate

Avoid:

- Excessive gradients
- Excessive animation
- Childish graphics
- Overly decorative cards
- Excessive rounded elements

Use consistent:

- Typography
- Spacing
- Buttons
- Form fields
- Tables
- Status badges
- Dialogs
- Cards

Do not use color alone to communicate important status.

---

## 5. Equipment UI

Create:

### Equipment list

Features:

- Search
- Filters
- Sorting
- Pagination
- Column visibility
- Export
- Bulk selection

Columns:

- Code
- Equipment
- Category
- Brand
- Model
- Service
- Location
- Status
- Criticality
- Last maintenance
- Next maintenance
- Actions

Actions:

- View
- Edit
- Duplicate
- Archive
- Delete where permitted
- Create intervention
- Create maintenance
- Print/export equipment sheet

### Equipment detail

Use tabs:

1. Informations générales
2. Maintenance
3. Interventions
4. Maintenance préventive
5. Pièces de rechange
6. Documents
7. Coûts
8. Activité

Include a timeline.

---

## 6. Intervention Request UI

Create a professional request form.

Fields:

- Equipment
- Department
- Requester
- Problem
- Symptoms
- Priority
- Safety impact
- Equipment availability
- Attachments

Show request status prominently.

Create filters for:

- Status
- Priority
- Department
- Equipment
- Date
- Technician

Critical requests should have a strong visual warning.

---

## 7. Work Order UI

Create a dedicated work-order page.

Sections:

### Identification

- Work order number
- Request
- Equipment
- Priority
- Status

### Assignment

- Technician
- Team
- Scheduled date

### Intervention

- Diagnosis
- Failure cause
- Corrective action
- Work performed
- Labor time
- Downtime
- Cost

### Parts

Allow selecting parts used.

### Documents

Allow attachments.

### Validation

Allow authorized roles to validate and close.

Display a status timeline:

**Nouvelle → Assignée → Planifiée → En cours → En attente de pièce → Résolue → Validée → Clôturée**

---

## 8. Preventive Maintenance UI

Create:

- Maintenance plans table
- Calendar
- Upcoming maintenance
- Overdue maintenance
- Maintenance detail page
- Checklist editor
- Checklist execution page

Use status indicators:

- À venir
- Due
- En retard
- En cours
- Terminée

---

## 9. Technician UI

Technician dashboard should prioritize:

- My assigned interventions
- Urgent requests
- Today's maintenance
- Overdue work
- Parts needed

Technician profile should show:

- Skills
- Certifications
- Availability
- Workload
- Completed interventions
- Average intervention time

---

## 10. Spare Parts UI

Create:

### Inventory table

Columns:

- Part number
- Name
- Category
- Manufacturer
- Quantity
- Minimum stock
- Unit cost
- Supplier
- Storage location
- Status

Add:

- Stock entry
- Stock exit
- Adjustment
- Usage history

Low stock must be visually obvious.

Do not allow negative stock by default.

---

## 11. Supplier and Contract UI

Supplier list:

- Company
- Contact
- Category
- Contract status
- Equipment count
- Spare parts

Supplier detail:

- Information
- Equipment
- Parts
- Contracts
- History

Contract list:

- Contract number
- Supplier
- Start
- End
- Cost
- SLA
- Status

Show upcoming expiration warnings.

---

## 12. Document Handling

Implement secure file upload where supported.

Validate:

- File type
- File size

Display:

- File name
- Type
- Category
- Uploader
- Upload date

Allow:

- Preview where possible
- Download
- Delete if authorized

Never expose private files through insecure public paths.

---

## 13. Notifications

Create an in-app notification center.

Notification examples:

- Nouvelle demande
- Panne critique
- Intervention assignée
- Maintenance préventive imminente
- Maintenance en retard
- Stock faible
- Contrat bientôt expiré
- Garantie bientôt expirée
- Intervention terminée
- Validation nécessaire

Use read/unread states.

---

## 14. Dashboard Implementation

Create KPI cards.

Use interactive charts.

Charts should react to selected date/department/category filters.

Required charts:

1. Equipment by category
2. Equipment by department
3. Corrective vs preventive maintenance
4. Requests by priority
5. Maintenance activity by month
6. Failures by month
7. Maintenance cost by month
8. Downtime by department
9. MTTR by category
10. MTBF by category

Do not fabricate KPI calculations.

Use actual database data.

If there is insufficient data, display an appropriate empty state.

---

## 15. Reports

Build report pages with filters.

Reports:

- Equipment inventory
- Maintenance history
- Preventive maintenance
- Corrective maintenance
- Breakdown statistics
- Costs
- Parts consumption
- Technician activity
- Availability
- Downtime
- Contracts

Where technically supported, implement export to:

- PDF
- CSV
- Excel

---

## 16. Database Model

Create normalized relationships between:

- Users
- Roles
- Permissions
- Departments
- Locations
- Equipment
- Categories
- Manufacturers
- Suppliers
- Maintenance plans
- Checklists
- Checklist items
- Maintenance records
- Intervention requests
- Work orders
- Technicians
- Skills
- Spare parts
- Stock transactions
- Contracts
- Documents
- Notifications
- Audit logs

Use foreign keys/relations appropriately.

Unique identifiers:

- Inventory code
- Serial number where appropriate
- Request number
- Work order number
- Contract number
- Part number

---

## 17. Audit Trail

Record important mutations.

Example:

```text
User: technician@medmaint.demo
Action: UPDATE
Entity: Work Order
Entity ID: WO-00024
Timestamp: ...
Changes:
Status: "En cours" → "Résolue"
```

Do not expose audit logs to unauthorized users.

---

## 18. Validation

Validate:

- Required fields
- Email
- Dates
- Numbers
- Prices
- Quantities
- Unique identifiers

Examples:

- Next maintenance cannot normally precede last maintenance.
- Spare part quantity cannot normally be negative.
- Contract end date cannot precede start date.
- Work-order completion should not precede its start.

Show clear French validation messages.

---

## 19. Error Handling

Handle:

- Network errors
- Authentication failures
- Authorization failures
- Validation errors
- Database errors
- Missing records
- File upload errors

Normal users should not see raw stack traces.

Show useful French messages.

Example:

> Impossible d'enregistrer l'intervention. Veuillez vérifier les champs obligatoires.

---

## 20. Loading and Empty States

Implement loading states for:

- Dashboard
- Tables
- Detail pages
- Forms
- File uploads

Every empty table must have a useful message and, where appropriate, an action.

Examples:

> Aucun équipement trouvé.

> Aucune demande d'intervention ne correspond aux filtres.

> Aucune maintenance préventive programmée.

---

## 21. Search

Implement global search.

Search fields should include:

- Equipment name
- Inventory code
- Serial number
- Request number
- Work order number
- Technician
- Part number
- Supplier
- Contract number

Group results.

---

## 22. Responsive Design

Desktop first.

Support:

- 1280px+
- 1024px
- 768px
- Basic mobile responsiveness

Tables should remain usable on smaller screens.

Sidebar should collapse.

---

## 23. Accessibility

Implement:

- Semantic HTML
- Labels for inputs
- Keyboard navigation
- Focus states
- Accessible buttons
- Sufficient contrast
- Descriptive icons/tooltips

---

## 24. Performance

Design for:

- Hundreds/thousands of equipment
- Thousands of requests
- Large intervention histories

Use:

- Pagination
- Efficient queries
- Lazy loading where appropriate
- Optimized rendering

Do not fetch entire tables unnecessarily.

---

## 25. Demo Data

Seed realistic fictional data.

Minimum:

- 30–50 equipment
- 10+ services
- 8 technicians
- 25 intervention requests
- 20 preventive maintenance records
- 30 spare parts
- 10 suppliers
- 10 contracts

Make dashboard statistics meaningful.

No real patient data.

---

## 26. Demo Accounts

Create demonstration roles such as:

- admin@medmaint.demo
- manager@medmaint.demo
- engineer@medmaint.demo
- technician@medmaint.demo
- service@medmaint.demo

Use secure demonstration passwords and document them in the README.

Do not expose production secrets.

---

## 27. Business Logic

### Critical breakdown

When a critical request is submitted:

1. Create request
2. Set priority to Critique
3. Notify maintenance manager
4. Notify appropriate biomedical team where supported
5. Highlight the request
6. Allow immediate technician assignment

### Preventive maintenance

When maintenance is completed:

1. Save checklist
2. Save observations
3. Save technician
4. Save completion date
5. Update equipment's last maintenance
6. Calculate next maintenance
7. Add maintenance history entry

### Spare parts

When parts are used:

1. Verify stock
2. Record transaction
3. Decrease quantity
4. Associate transaction with work order
5. Calculate part cost
6. Recalculate intervention cost
7. Trigger low-stock notification if threshold is reached

### Work order closure

Do not allow closure until required fields are completed.

Required fields should include at least:

- Diagnosis
- Work performed
- Technician
- Completion date

Where applicable:

- Parts used
- Cost
- Validation

---

## 28. Security

Implement:

- Protected routes
- Role-based permissions
- Backend authorization
- Input validation
- Safe file handling
- Secure authentication
- Audit logs
- Environment variables for secrets

Never hard-code API keys or passwords.

Do not trust client-side role information.

---

## 29. Code Quality

Before completion:

- Remove unused code
- Fix TypeScript errors
- Fix console errors
- Fix broken routes
- Fix missing imports
- Test authentication
- Test permissions
- Test CRUD
- Test forms
- Test filters
- Test pagination
- Test file handling
- Test maintenance workflow
- Test dashboard calculations
- Test responsive behavior

Do not hide errors.

Fix the underlying problem.

---

## 30. UI Quality Gate

Before declaring the project complete:

Every main sidebar item must open a working page.

Every primary action button must do something meaningful.

Do not leave:

- Empty placeholder pages
- "Coming soon" screens
- Fake buttons
- Dummy charts disconnected from data
- Lorem ipsum
- Broken links

The dashboard must contain realistic demonstration data.

---

## 31. README

Create a professional README with:

# MedMaint

Hospital Biomedical Equipment Maintenance Management System.

## Overview

Explain the problem solved.

## Features

List all implemented modules.

## Technology

List actual technologies used.

## Architecture

Explain:

- Frontend
- Backend
- Database
- Authentication
- File storage

## Screenshots

Include placeholders/sections for screenshots.

## Demo

Include deployed URL if available.

## Demo Accounts

Document demonstration accounts.

## Installation

Provide clear installation instructions.

## Database

Explain the main entities and relationships.

## Workflow

Explain breakdown and preventive maintenance workflows.

## Security

Explain authentication, authorization and audit logging.

## Future Improvements

Include realistic future enhancements.

---

## 32. Final Acceptance Criteria

The project is considered complete only when:

- Authentication works
- Role-based access works
- Equipment CRUD works
- Equipment detail pages work
- Intervention requests work
- Work orders work
- Technician assignment works
- Preventive maintenance works
- Checklists work
- Spare-part inventory works
- Stock transactions work
- Suppliers work
- Contracts work
- Documents work
- Notifications work
- Reports work
- Dashboard uses real data
- KPI calculations use real data
- Audit logging works
- Search works
- Filters work
- Pagination works
- Demo data exists
- No patient data exists
- No major console errors exist
- No major broken routes exist
- The application is responsive
- The UI looks professional

---

## 33. Development Order

Implement in this order:

1. Architecture
2. Database
3. Authentication
4. Roles and permissions
5. Main layout
6. Dashboard
7. Services
8. Equipment
9. Intervention requests
10. Work orders
11. Preventive maintenance
12. Technicians
13. Spare parts
14. Suppliers
15. Contracts
16. Documents
17. Notifications
18. Reports
19. Analytics
20. Audit logs
21. Settings
22. Demo data
23. Testing
24. UI polish
25. README

Do not skip the database/business logic just to make the interface look complete.

---

## 34. Final Product Standard

The result must feel like a serious hospital CMMS/GMAO product.

It should demonstrate:

- Business analysis
- Database design
- Full-stack development
- Authentication
- Authorization
- Complex workflows
- File management
- Dashboard development
- KPI calculations
- Reporting
- Professional UX
- Maintainable architecture

Prioritize functionality, consistency, reliability and professional UX over unnecessary visual effects.
