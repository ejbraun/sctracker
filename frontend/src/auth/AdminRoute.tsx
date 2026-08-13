import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/** Nested inside ProtectedRoute, so `person` is already guaranteed non-null here. */
export function AdminRoute() {
  const { person } = useAuth();

  if (!person?.is_admin) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
