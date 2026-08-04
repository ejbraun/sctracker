import { BrowserRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { AuthProvider } from './auth/AuthContext';
import { ProtectedRoute } from './auth/ProtectedRoute';
import { Layout } from './components/Layout';
import { Login } from './pages/Login';
import { Signup } from './pages/Signup';
import { Dashboard } from './pages/Dashboard';
import { Account } from './pages/Account';
import { Characters } from './pages/Characters';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { LoserboardsPage } from './pages/LoserboardsPage';
import { RunHistory } from './pages/RunHistory';
import { RunDetail } from './pages/RunDetail';

const queryClient = new QueryClient();

/** Route map from specs/frontend/00-overview.md. */
export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <AuthProvider>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/signup" element={<Signup />} />
            <Route element={<ProtectedRoute />}>
              <Route element={<Layout />}>
                <Route path="/" element={<Dashboard />} />
                <Route path="/account" element={<Account />} />
                <Route path="/characters" element={<Characters />} />
                <Route path="/leaderboards/:mapId" element={<LeaderboardPage />} />
                <Route path="/loserboards" element={<LoserboardsPage />} />
                <Route path="/runs" element={<RunHistory />} />
                <Route path="/runs/:id" element={<RunDetail />} />
              </Route>
            </Route>
          </Routes>
        </AuthProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
