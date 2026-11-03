import { Routes, Route, Navigate } from 'react-router-dom';
import { Layout } from './components/Layout';
import { RequireAuth } from './components/RequireAuth';
import { DashboardPage } from './pages/DashboardPage';
import { AgentsListPage } from './pages/AgentsListPage';
import { AgentDetailPage } from './pages/AgentDetailPage';
import { EventsListPage } from './pages/EventsListPage';
import { DecisionsPage } from './pages/DecisionsPage';
import { AllowlistPage } from './pages/AllowlistPage';
import { LoginPage } from './pages/LoginPage';

function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<RequireAuth />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/agents" element={<AgentsListPage />} />
          <Route path="/agents/:agentId" element={<AgentDetailPage />} />
          <Route path="/events" element={<EventsListPage />} />
          <Route path="/blocks" element={<DecisionsPage />} />
          <Route path="/allowlist" element={<AllowlistPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
