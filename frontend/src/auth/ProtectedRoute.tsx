import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** specs/frontend/01-auth.md. */
export function ProtectedRoute() {
  const { person, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return <div>Loading…</div>;
  }
  if (!person) {
    return <Navigate to={`/login?redirect=${encodeURIComponent(location.pathname)}`} replace />;
  }
  return <Outlet />;
}
