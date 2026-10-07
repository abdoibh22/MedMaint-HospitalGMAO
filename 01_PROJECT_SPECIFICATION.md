# MedMaint — Hospital GMAO / Biomedical Equipment Maintenance Management System

## 1. Project Overview

Build a complete, professional, production-style web application called:

**MedMaint — Hospital Biomedical Equipment Maintenance Management System**

MedMaint is a hospital GMAO/CMMS platform designed to manage biomedical and technical equipment throughout its lifecycle:

**Acquisition → Inventory → Installation → Preventive Maintenance → Breakdown Reporting → Corrective Maintenance → Technician Intervention → Spare Parts → Maintenance History → Analytics → Reporting**

This is a portfolio-quality project intended to demonstrate real-world software engineering ability. It must not look like a simple CRUD demo or an MVP.

The application should be suitable for demonstrating to:

- Hospitals
- Private clinics
- Biomedical engineering departments
- Maintenance departments
- Hospital IT departments
- Healthcare organizations

## 2. Main Objectives

The system must allow a hospital to:

- Maintain a complete equipment inventory
- Track equipment locations and departments
- Report equipment breakdowns
- Create and manage intervention requests
- Assign technicians
- Manage corrective maintenance
- Schedule preventive maintenance
- Use preventive maintenance checklists
- Track spare parts and stock
- Manage suppliers
- Manage maintenance contracts
- Store technical documents
- Track maintenance costs
- Calculate maintenance KPIs
- Generate reports
- Maintain an audit trail
- Control access using user roles

## 3. Primary Language

The interface must be primarily in **French**, using professional terminology appropriate for hospitals in Algeria and Francophone healthcare environments.

Examples:

- Tableau de bord
- Équipements
- Maintenance préventive
- Maintenance corrective
- Demandes d'intervention
- Interventions
- Techniciens
- Services hospitaliers
- Pièces de rechange
- Fournisseurs
- Contrats de maintenance
- Documents
- Rapports
- Notifications
- Utilisateurs
- Paramètres

The architecture should allow English localization to be added later.

## 4. Main User Roles

### Administrateur

Full system access.

### Responsable Maintenance

Manages requests, assignments, maintenance planning, technicians, spare parts, suppliers and reports.

### Ingénieur Biomédical

Manages biomedical equipment, inspections, preventive maintenance, corrective interventions and technical documentation.

### Technicien

Views assigned work orders, performs interventions, records diagnosis/actions, uses spare parts and closes interventions.

### Responsable de Service

Views equipment belonging to their service, reports failures and tracks requests.

### Demandeur / Utilisateur

Reports equipment problems and tracks submitted requests.

## 5. Main Application Modules

The application must contain:

1. Tableau de bord
2. Équipements
3. Demandes d'intervention
4. Ordres de travail / Interventions
5. Maintenance préventive
6. Techniciens
7. Services hospitaliers
8. Pièces de rechange
9. Fournisseurs
10. Contrats de maintenance
11. Documents
12. Notifications
13. Rapports
14. Analytics / KPI
15. Utilisateurs
16. Journal d'audit
17. Paramètres

## 6. Equipment Management

Each equipment record should support:

### Identification

- ID interne
- Code inventaire
- Nom
- Catégorie
- Sous-catégorie
- Marque
- Modèle
- Numéro de série
- Numéro de référence

### Classification

- Type d'équipement
- Criticité
- Niveau de risque
- Classe du dispositif médical
- Statut

### Location

- Établissement
- Service
- Unité
- Salle
- Localisation

### Acquisition

- Fournisseur
- Date d'achat
- Date de réception
- Date de mise en service
- Prix d'achat
- Garantie
- Fin de garantie

### Maintenance

- Type de maintenance
- Fréquence préventive
- Dernière maintenance
- Prochaine maintenance
- Responsable
- Contrat

### Technical information

- Fabricant
- Année de fabrication
- Alimentation
- Puissance
- Dimensions
- Poids
- Notes techniques

### Documents

Support for:

- Manuels utilisateur
- Manuels techniques
- Manuels de maintenance
- Factures
- Garanties
- Certificats
- Rapports d'inspection
- Photos

## 7. Equipment Statuses

Use:

- En service
- En maintenance
- Hors service
- En attente de réparation
- En quarantaine
- Réformé
- Stock
- Perdu

## 8. Equipment Categories

Include configurable categories such as:

- Monitoring
- Imagerie médicale
- Laboratoire
- Bloc opératoire
- Réanimation
- Respiratoire
- Stérilisation
- Dialyse
- Physiothérapie
- Électrochirurgie
- Mobilier médical
- Informatique médicale

Administrators can create additional categories.

## 9. Hospital Services

Include examples:

- Réanimation
- Urgences
- Cardiologie
- Radiologie
- Laboratoire
- Bloc opératoire
- Maternité
- Pédiatrie
- Neurologie
- Médecine interne
- Chirurgie
- Consultation externe

Each service should have:

- Name
- Code
- Responsible person
- Location
- Contact
- Equipment count
- Open requests
- Maintenance statistics

## 10. Intervention Requests

A user must be able to report an equipment problem.

Fields:

- Request ID
- Equipment
- Requesting department
- Requester
- Date/time
- Problem description
- Symptoms
- Priority
- Safety impact
- Equipment availability
- Attachments
- Status

Priorities:

- Faible
- Normale
- Haute
- Urgente
- Critique

Statuses:

- Nouvelle
- En attente
- Acceptée
- Assignée
- En cours
- En attente de pièce
- Résolue
- Clôturée
- Rejetée

## 11. Work Orders

Approved requests should be convertible into work orders.

Fields:

- Work order number
- Original request
- Equipment
- Technician
- Team
- Priority
- Date opened
- Scheduled date
- Start date
- Completion date
- Diagnosis
- Cause of failure
- Corrective action
- Work performed
- Parts used
- Labor time
- Downtime
- Cost
- Attachments
- Comments

Workflow:

**Demande → Approbation → Affectation → Planification → En cours → En attente de pièce → Résolue → Validation → Clôturée**

## 12. Preventive Maintenance

Each preventive maintenance plan should support:

- Equipment
- Maintenance type
- Frequency
- Checklist
- Responsible technician
- Last performed
- Next scheduled date
- Estimated duration
- Required spare parts
- Required tools
- Instructions
- Status

Frequencies:

- Daily
- Weekly
- Monthly
- Quarterly
- Semiannual
- Annual
- Custom

Provide:

- List view
- Calendar view
- Overdue view
- Upcoming maintenance view

## 13. Preventive Maintenance Checklists

Allow engineers to create equipment-specific checklists.

Example for a patient monitor:

- Visual inspection
- Power supply check
- Display check
- Alarm check
- ECG verification
- SpO2 verification
- NIBP verification
- Battery check
- Electrical safety check
- Cleaning

Checklist items should support:

- Pass
- Fail
- Not applicable
- Notes

Completed checklists become part of equipment history.

## 14. Corrective Maintenance

Corrective maintenance records should include:

- Equipment
- Failure date
- Failure type
- Symptoms
- Diagnosis
- Root cause
- Corrective action
- Technician
- Parts used
- Labor time
- Downtime
- Cost
- Completion date
- Validation
- Comments

## 15. Technician Management

Technician profiles:

- Name
- Employee ID
- Department
- Specialty
- Phone
- Email
- Skills
- Certifications
- Status
- Availability

Statistics:

- Assigned interventions
- Completed interventions
- Open interventions
- Average intervention time
- Equipment categories handled

Include a workload view.

## 16. Spare Parts

Each spare part should contain:

- Part number
- Part name
- Category
- Manufacturer
- Compatible equipment
- Quantity
- Minimum stock
- Maximum stock
- Unit cost
- Supplier
- Storage location
- Expiration date when applicable

Transactions:

- Add stock
- Remove stock
- Transfer stock
- Adjust stock
- Record usage

Alert when:

**Quantity < Minimum Stock**

Do not allow negative stock unless explicitly overridden by an administrator.

## 17. Suppliers

Supplier records:

- Company
- Contact person
- Address
- Phone
- Email
- Website
- Supplier category
- Products/services
- Contract status
- Notes

Supplier history should show:

- Equipment supplied
- Spare parts
- Maintenance contracts
- Purchases
- Interventions

## 18. Maintenance Contracts

Contract fields:

- Contract number
- Supplier
- Equipment covered
- Contract type
- Start date
- End date
- Annual cost
- SLA
- Response time
- Contact
- Documents
- Status

Statuses:

- Actif
- Expirant bientôt
- Expiré
- Suspendu

Show expiration warnings.

## 19. Documents

Documents may be attached to:

- Equipment
- Interventions
- Maintenance plans
- Suppliers
- Contracts
- Users

Supported types:

- PDF
- JPG
- PNG
- DOCX
- XLSX

Metadata:

- Filename
- Category
- Upload date
- Uploader

## 20. Notifications

Create an in-app notification center.

Notify users about:

- New intervention request
- Critical breakdown
- Assigned intervention
- Preventive maintenance due
- Preventive maintenance overdue
- Low stock
- Contract expiration
- Warranty expiration
- Completed intervention
- Validation required

Notifications need:

- Read/unread state
- Timestamp
- Type
- Related record

## 21. Reports

Reports should include:

- Equipment inventory
- Equipment maintenance history
- Preventive maintenance
- Corrective maintenance
- Breakdown statistics
- Maintenance costs
- Spare parts consumption
- Technician activity
- Equipment availability
- Equipment downtime
- Contracts

Filters:

- Date range
- Department
- Category
- Equipment
- Technician
- Maintenance type
- Status

Export where supported:

- PDF
- Excel/CSV

## 22. Maintenance Analytics

Calculate:

### MTBF

Mean Time Between Failures.

### MTTR

Mean Time To Repair.

### Availability

Based on operating time and downtime.

### Preventive Maintenance Compliance

Percentage of scheduled preventive maintenance completed on time.

### Failure Rate

Failures per equipment/category/time period.

### Maintenance Cost

By:

- Equipment
- Department
- Category
- Month
- Year

Clearly distinguish calculated metrics from manually entered values.

## 23. Dashboard

The dashboard should display:

### Equipment KPIs

- Total equipment
- In service
- Out of service
- Under maintenance
- Retired

### Maintenance KPIs

- Open requests
- Urgent requests
- Active interventions
- Completed interventions
- Upcoming preventive maintenance
- Overdue maintenance

### Performance

- Availability
- Preventive maintenance compliance
- Failure count
- Average intervention time
- Average repair time
- Maintenance cost

Charts:

- Equipment by category
- Equipment by department
- Corrective vs preventive maintenance
- Requests by priority
- Maintenance activity over time
- Failures over time
- Maintenance cost over time

Also include:

- Recent activity
- Upcoming maintenance
- Critical requests

## 24. Search

Global search across:

- Equipment
- Inventory codes
- Serial numbers
- Intervention IDs
- Requests
- Technicians
- Spare parts
- Suppliers
- Contracts

Group search results by type.

## 25. Audit Log

Record:

- User
- Action
- Entity
- Entity ID
- Timestamp
- Previous value where practical
- New value where practical

Examples:

- Equipment updated
- Intervention assigned
- Spare part consumed
- User created
- Maintenance completed

Only authorized administrators can view the audit log.

## 26. No Patient Data

This is a maintenance management system.

Do not create patient medical records.

Do not store:

- Patient names
- Patient diagnoses
- Patient identifiers
- Patient medical histories

The system manages equipment and maintenance, not clinical records.

## 27. Core Real-World Workflows

### Breakdown workflow

Department user:

**Equipment → Signaler une panne → Describe problem → Select priority → Attach evidence → Submit**

Maintenance manager:

**Review → Approve → Assign technician**

Technician:

**Open work order → Start → Diagnose → Repair → Use parts → Record work → Complete**

Engineer/manager:

**Validate → Close**

The equipment maintenance history must update automatically.

### Preventive maintenance workflow

System detects upcoming maintenance.

Technician:

**Open maintenance → Follow checklist → Record observations → Complete**

System updates:

- Last maintenance
- Next maintenance
- Maintenance history
- Equipment status where appropriate

## 28. Sample Demonstration Data

Populate the application with realistic fictional data.

At minimum:

- 30–50 equipment records
- 10+ hospital services
- 8+ technicians
- 25+ intervention requests
- 20+ preventive maintenance records
- 30+ spare parts
- 10+ suppliers
- 10+ contracts

Equipment examples:

- Patient monitors
- Ventilators
- Infusion pumps
- Syringe pumps
- ECG machines
- Defibrillators
- Ultrasound systems
- X-ray systems
- CT scanner
- Anesthesia machines
- Dialysis machines
- Laboratory analyzers
- Autoclaves
- Operating tables

Use fictional data only.

## 29. Portfolio Objective

The application should demonstrate that its developer can:

- Analyze business requirements
- Design databases
- Build full-stack applications
- Implement authentication
- Implement role-based authorization
- Build professional interfaces
- Implement complex workflows
- Manage documents
- Build dashboards
- Calculate business KPIs
- Generate reports
- Design maintainable software

The final result should look like a serious hospital CMMS/GMAO product, not a student CRUD project.
