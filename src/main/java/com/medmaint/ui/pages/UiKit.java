package com.medmaint.ui.pages;

import com.medmaint.security.Permission;
import com.medmaint.ui.navigation.Router;
import com.medmaint.ui.pages.admin.AuditPage;
import com.medmaint.ui.pages.admin.UsersPage;
import com.medmaint.ui.pages.dashboard.DashboardPage;
import com.medmaint.ui.pages.equipment.EquipmentListPage;
import com.medmaint.ui.pages.equipment.EquipmentDetailPage;
import com.medmaint.ui.pages.maintenance.PreventivePage;
import com.medmaint.ui.pages.maintenance.WorkOrdersPage;
import com.medmaint.ui.pages.parts.PartsPage;
import com.medmaint.ui.pages.procurement.ContractsPage;
import com.medmaint.ui.pages.procurement.SuppliersPage;
import com.medmaint.ui.pages.reports.ReportsPage;
import com.medmaint.ui.pages.requests.RequestsPage;
import com.medmaint.ui.pages.resources.DepartmentsPage;
import com.medmaint.ui.pages.resources.DocumentsPage;
import com.medmaint.ui.pages.resources.TechniciansPage;
import com.medmaint.ui.pages.settings.SettingsPage;
import com.medmaint.ui.pages.shell.NotificationsPage;
import com.medmaint.ui.pages.shell.ProfilePage;

/** Assemblage du routeur : toutes les routes + permissions + sections de menu. */
public final class UiKit {

    private UiKit() {
    }

    public static Router buildRouter() {
        Router r = new Router();

        // Section maintenance
        r.register(new Router.Route("dashboard", "nav.dashboard", "▦",
                Permission.DASHBOARD_VIEW, "nav.section.analysis", DashboardPage::new));
        r.register(new Router.Route("equipment", "nav.equipment", "◫",
                Permission.EQUIPMENT_VIEW, "nav.section.maintenance",
                EquipmentListPage::new));
        r.register(new Router.Route("requests", "nav.requests", "⚠",
                Permission.REQUEST_VIEW, "nav.section.maintenance", RequestsPage::new));
        r.register(new Router.Route("workorders", "nav.workorders", "⚒",
                Permission.WORKORDER_VIEW, "nav.section.maintenance", WorkOrdersPage::new));
        r.register(new Router.Route("preventive", "nav.preventive", "⟳",
                Permission.PM_VIEW, "nav.section.maintenance", PreventivePage::new));

        // Section référentiels
        r.register(new Router.Route("technicians", "nav.technicians", "☺",
                Permission.TECHNICIAN_VIEW, "nav.section.references", TechniciansPage::new));
        r.register(new Router.Route("departments", "nav.departements", "⌂",
                Permission.DEPARTMENT_VIEW, "nav.section.references", DepartmentsPage::new));
        r.register(new Router.Route("parts", "nav.parts", "⚙",
                Permission.PART_VIEW, "nav.section.references", PartsPage::new));
        r.register(new Router.Route("suppliers", "nav.suppliers", "⇄",
                Permission.SUPPLIER_VIEW, "nav.section.references", SuppliersPage::new));
        r.register(new Router.Route("contracts", "nav.contracts", "§",
                Permission.CONTRACT_VIEW, "nav.section.references", ContractsPage::new));
        r.register(new Router.Route("documents", "nav.documents", "📄",
                Permission.DOCUMENT_VIEW, "nav.section.references", DocumentsPage::new));

        // Section analyse
        r.register(new Router.Route("reports", "nav.reports", "☰",
                Permission.REPORT_VIEW, "nav.section.analysis", ReportsPage::new));
        r.register(new Router.Route("notifications", "nav.notifications", "🔔",
                Permission.NOTIFICATION_VIEW, "nav.section.analysis", NotificationsPage::new));

        // Section administration
        r.register(new Router.Route("users", "nav.users", "👥",
                Permission.USER_VIEW, "nav.section.administration", UsersPage::new));
        r.register(new Router.Route("audit", "nav.audit", "📋",
                Permission.AUDIT_VIEW, "nav.section.administration", AuditPage::new));
        r.register(new Router.Route("settings", "nav.settings", "⚒",
                Permission.SETTINGS_VIEW, "nav.section.administration", SettingsPage::new));
        r.register(new Router.Route("profile", "nav.profile", "☺",
                null, "nav.section.administration", ProfilePage::new));

        return r;
    }
}
