package com.medmaint.service;

/**
 * Conteneur de services (assemblage manuel, pas de framework DI).
 * Un seul point d'accès pour l'UI : {@code AppContext.get()}.
 */
public final class AppContext {

    private static AppContext instance;

    private final AuditService audit = new AuditService();
    private final NotificationService notifications = new NotificationService();
    private final SettingsService settings = new SettingsService(audit);
    private final DocumentService documents = new DocumentService(audit);
    private final AuthService auth = new AuthService(audit, notifications);
    private final EquipmentService equipment = new EquipmentService(audit, notifications, documents);
    private final ReferenceService references = new ReferenceService(audit);
    private final TechnicianService technicians = new TechnicianService(audit);
    private final WorkOrderService workOrders = new WorkOrderService(audit, notifications);
    private final RequestService requests;
    private final PreventiveService preventive = new PreventiveService(audit, notifications);
    private final StockService stock = new StockService(audit, notifications);
    private final SupplierContractService suppliers = new SupplierContractService(audit, notifications);
    private final AnalyticsService analytics = new AnalyticsService();
    private final ReportService reports = new ReportService();
    private final SearchService search = new SearchService();

    private AppContext() {
        this.requests = new RequestService(audit, notifications, workOrders);
    }

    public static AppContext get() {
        if (instance == null) {
            instance = new AppContext();
        }
        return instance;
    }

    /** À appeler une fois au démarrage (après migration du schéma). */
    public void bootstrap() {
        settings.ensureDefaults();
        preventive.scanDue();
        suppliers.scanExpiring();
        suppliers.scanWarranties();
        notifications.notifyRole("MANAGER", com.medmaint.domain.enums.NotificationType.PM_A_VENIR,
                "Bienvenue sur MedMaint",
                "Les échéances de maintenance et les alertes de stock ont été actualisées.",
                "INFO", null, null);
    }

    public AuditService audit() { return audit; }
    public NotificationService notifications() { return notifications; }
    public SettingsService settings() { return settings; }
    public DocumentService documents() { return documents; }
    public AuthService auth() { return auth; }
    public EquipmentService equipment() { return equipment; }
    public ReferenceService references() { return references; }
    public TechnicianService technicians() { return technicians; }
    public WorkOrderService workOrders() { return workOrders; }
    public RequestService requests() { return requests; }
    public PreventiveService preventive() { return preventive; }
    public StockService stock() { return stock; }
    public SupplierContractService suppliers() { return suppliers; }
    public AnalyticsService analytics() { return analytics; }
    public ReportService reports() { return reports; }
    public SearchService search() { return search; }
}
