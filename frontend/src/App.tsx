import { Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AppShell } from './shared/layout/AppShell';
import { AuthGate } from './shared/auth/AuthGate';
import { LoginPage } from './features/auth/LoginPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { PatientRegistrationPage } from './features/reception/PatientRegistrationPage';
import { InfantRegistrationPage } from './features/reception/InfantRegistrationPage';
import { PatientListPage } from './features/reception/PatientListPage';
import { ComingSoonPage } from './features/comingsoon/ComingSoonPage';
import { CataloguesPage } from './features/admin/catalogues/CataloguesPage';
import { VisitQueuePage } from './features/reception/visits/VisitQueuePage';
import { CashierQueuePage } from './features/cashier/CashierQueuePage';
import { AppointmentsPage } from './features/reception/appointments/AppointmentsPage';
import { UsersPage } from './features/admin/users/UsersPage';
import { DoctorsPage } from './features/admin/doctors/DoctorsPage';
import { MySchedulePage } from './features/doctor/MySchedulePage';
import { LaboratoryPage, RadiologyPage, EcoPage } from './features/departments/DepartmentWorkspace';
import { ClinicalExamPage } from './features/clinical/ClinicalExamPage';
import { PatientProfilePage } from './features/patients/PatientProfilePage';
import { PharmacyQueuePage } from './features/pharmacy/PharmacyQueuePage';
import { PharmacyInventoryPage } from './features/pharmacy/PharmacyInventoryPage';
import { PrematureWorkspacePage } from '@/features/premature/PrematureWorkspacePage';
import { BedAdminPage } from '@/features/premature/BedAdminPage';

export default function App() {
  return (
    <>
      <Toaster
        position="top-right"
        toastOptions={{
          duration: 3500,
          style: {
            background: 'white',
            color: '#0F172A',
            border: '1px solid #E2E8F0',
            borderRadius: '0.625rem',
            boxShadow: '0 10px 25px -5px rgb(0 0 0 / 0.08), 0 4px 6px -2px rgb(0 0 0 / 0.04)',
            padding: '12px 16px',
            fontSize: '14px',
          },
          success: { iconTheme: { primary: '#16A34A', secondary: 'white' } },
          error:   { iconTheme: { primary: '#C8102E', secondary: 'white' } },
        }}
      />
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <AuthGate>
              <AppShell />
            </AuthGate>
          }
        >
          <Route path="/" element={<DashboardPage />} />
          <Route path="/reception/patients" element={<PatientListPage />} />
          <Route path="/reception/patients/new" element={<PatientRegistrationPage />} />
          <Route path="/reception/patients/new-infant" element={<InfantRegistrationPage />} />
          <Route path="/patients/:id" element={<PatientProfilePage />} />

          {/* Stubs — implemented in upcoming releases. */}
          <Route path="/reception/appointments" element={<AppointmentsPage />} />
          <Route path="/reception/queue" element={<VisitQueuePage />} />
          <Route path="/departments/laboratory" element={<LaboratoryPage />} />
          <Route path="/departments/radiology" element={<RadiologyPage />} />
          <Route path="/departments/eco" element={<EcoPage />} />
          <Route path="/departments/emergency" element={<ComingSoonPage />} />
          <Route path="/departments/premature" element={<PrematureWorkspacePage />} />
          <Route path="/premature/beds" element={<BedAdminPage />} />
          <Route path="/pharmacy" element={<PharmacyQueuePage />} />
          <Route path="/pharmacy/inventory" element={<PharmacyInventoryPage />} />
          <Route path="/cashier" element={<CashierQueuePage />} />
          <Route path="/admin/users" element={<UsersPage />} />
          <Route path="/admin/doctors" element={<DoctorsPage />} />
          <Route path="/admin/catalogues" element={<CataloguesPage />} />
          <Route path="/my-schedule" element={<MySchedulePage />} />
          <Route path="/clinical/exam/:visitId" element={<ClinicalExamPage />} />
          <Route path="/admin/settings" element={<ComingSoonPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
