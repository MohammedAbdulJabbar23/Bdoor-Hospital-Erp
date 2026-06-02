import { Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AppShell } from './shared/layout/AppShell';
import { AuthGate } from './shared/auth/AuthGate';
import { RoleGate } from './shared/auth/RoleGate';
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
import { PrematureCasePage } from '@/features/premature/PrematureCasePage';
import { EmergencyWorkspacePage } from '@/features/emergency/EmergencyWorkspacePage';
import { BedAdminPage as EmergencyBedAdminPage } from '@/features/emergency/BedAdminPage';

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
          {/* Dashboard + patient profile: viewable by every authenticated role. */}
          <Route path="/" element={<DashboardPage />} />
          <Route path="/patients/:id" element={<PatientProfilePage />} />

          {/* Reception */}
          <Route
            path="/reception/patients"
            element={<RoleGate roles={['RECEPTIONIST', 'ADMIN']}><PatientListPage /></RoleGate>}
          />
          <Route
            path="/reception/patients/new"
            element={<RoleGate roles={['RECEPTIONIST', 'ADMIN']}><PatientRegistrationPage /></RoleGate>}
          />
          <Route
            path="/reception/patients/new-infant"
            element={<RoleGate roles={['RECEPTIONIST', 'ADMIN']}><InfantRegistrationPage /></RoleGate>}
          />
          <Route
            path="/reception/appointments"
            element={<RoleGate roles={['RECEPTIONIST', 'ADMIN']}><AppointmentsPage /></RoleGate>}
          />
          <Route
            path="/reception/queue"
            element={<RoleGate roles={['RECEPTIONIST', 'ADMIN']}><VisitQueuePage /></RoleGate>}
          />

          {/* Departments */}
          <Route
            path="/departments/laboratory"
            element={<RoleGate roles={['LAB_STAFF', 'ADMIN']}><LaboratoryPage /></RoleGate>}
          />
          <Route
            path="/departments/radiology"
            element={<RoleGate roles={['RADIOLOGY_STAFF', 'ADMIN']}><RadiologyPage /></RoleGate>}
          />
          <Route
            path="/departments/eco"
            element={<RoleGate roles={['ECO_STAFF', 'ADMIN']}><EcoPage /></RoleGate>}
          />
          <Route
            path="/departments/emergency"
            element={<RoleGate roles={['EMERGENCY_STAFF', 'NURSE', 'DOCTOR', 'ADMIN']}><EmergencyWorkspacePage /></RoleGate>}
          />
          <Route
            path="/emergency/beds"
            element={<RoleGate roles={['EMERGENCY_STAFF', 'ADMIN']}><EmergencyBedAdminPage /></RoleGate>}
          />
          <Route
            path="/departments/premature"
            element={<RoleGate roles={['PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN']}><PrematureWorkspacePage /></RoleGate>}
          />
          <Route
            path="/premature/beds"
            element={<RoleGate roles={['PREMATURE_STAFF', 'ADMIN']}><BedAdminPage /></RoleGate>}
          />
          <Route
            path="/premature/admissions/:id"
            element={<RoleGate roles={['PREMATURE_STAFF', 'NURSE', 'DOCTOR', 'ADMIN']}><PrematureCasePage /></RoleGate>}
          />

          {/* Services */}
          <Route
            path="/pharmacy"
            element={<RoleGate roles={['PHARMACIST', 'ADMIN']}><PharmacyQueuePage /></RoleGate>}
          />
          <Route
            path="/pharmacy/inventory"
            element={<RoleGate roles={['PHARMACIST', 'ADMIN']}><PharmacyInventoryPage /></RoleGate>}
          />
          <Route
            path="/cashier"
            element={<RoleGate roles={['CASHIER', 'ADMIN']}><CashierQueuePage /></RoleGate>}
          />

          {/* Clinical */}
          <Route
            path="/clinical/exam/:visitId"
            element={<RoleGate roles={['DOCTOR', 'ADMIN']}><ClinicalExamPage /></RoleGate>}
          />
          <Route
            path="/my-schedule"
            element={<RoleGate roles={['DOCTOR', 'ADMIN']}><MySchedulePage /></RoleGate>}
          />

          {/* Administration */}
          <Route
            path="/admin/users"
            element={<RoleGate roles={['ADMIN']}><UsersPage /></RoleGate>}
          />
          <Route
            path="/admin/doctors"
            element={<RoleGate roles={['ADMIN']}><DoctorsPage /></RoleGate>}
          />
          <Route
            path="/admin/catalogues"
            element={<RoleGate roles={['ADMIN']}><CataloguesPage /></RoleGate>}
          />
          <Route
            path="/admin/settings"
            element={<RoleGate roles={['ADMIN']}><ComingSoonPage /></RoleGate>}
          />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
