import { Navigate, Route, Routes } from 'react-router-dom';
import { Toaster } from 'react-hot-toast';
import { AppShell } from './shared/layout/AppShell';
import { AuthGate } from './shared/auth/AuthGate';
import { LoginPage } from './features/auth/LoginPage';
import { DashboardPage } from './features/dashboard/DashboardPage';
import { PatientRegistrationPage } from './features/reception/PatientRegistrationPage';
import { PatientListPage } from './features/reception/PatientListPage';
import { ComingSoonPage } from './features/comingsoon/ComingSoonPage';
import { CataloguesPage } from './features/admin/catalogues/CataloguesPage';
import { VisitQueuePage } from './features/reception/visits/VisitQueuePage';

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

          {/* Stubs — implemented in upcoming releases. */}
          <Route path="/reception/appointments" element={<ComingSoonPage />} />
          <Route path="/reception/queue" element={<VisitQueuePage />} />
          <Route path="/departments/laboratory" element={<ComingSoonPage />} />
          <Route path="/departments/radiology" element={<ComingSoonPage />} />
          <Route path="/departments/eco" element={<ComingSoonPage />} />
          <Route path="/departments/emergency" element={<ComingSoonPage />} />
          <Route path="/departments/premature" element={<ComingSoonPage />} />
          <Route path="/pharmacy" element={<ComingSoonPage />} />
          <Route path="/cashier" element={<ComingSoonPage />} />
          <Route path="/admin/users" element={<ComingSoonPage />} />
          <Route path="/admin/catalogues" element={<CataloguesPage />} />
          <Route path="/admin/settings" element={<ComingSoonPage />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
