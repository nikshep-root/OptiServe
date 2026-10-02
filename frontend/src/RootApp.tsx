import { FormEvent, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { ArrowRight, LockKeyhole, X } from 'lucide-react'
import { BrowserRouter, useLocation, useNavigate } from 'react-router-dom'
import { authApi } from './api'
import LandingPage from './LandingPage'
import PremiumConsole from './PremiumConsole'

const queryClient = new QueryClient({ defaultOptions: { queries: { retry: 1, staleTime: 15_000 } } })

export default function RootApp() {
  return <QueryClientProvider client={queryClient}><BrowserRouter><ApplicationRouter /></BrowserRouter></QueryClientProvider>
}

function ApplicationRouter() {
  const location = useLocation()
  const navigate = useNavigate()
  const [token, setToken] = useState(() => localStorage.getItem('optiserve-token'))
  const isDashboardPath = location.pathname === '/dashboard' || ['/overview', '/requests', '/queue', '/assignments', '/resources', '/types'].some((path) => location.pathname.startsWith(path))

  function handleSignOut() {
    localStorage.removeItem('optiserve-token')
    setToken(null)
    navigate('/')
  }

  if (!isDashboardPath) return <LandingPage />
  if (!token) return <LoginScreen onAuthenticated={(accessToken) => { localStorage.setItem('optiserve-token', accessToken); setToken(accessToken); navigate('/dashboard') }} />

  return <PremiumConsole onSignOut={handleSignOut} />
}

function LoginScreen({ onAuthenticated }: { onAuthenticated: (accessToken: string) => void }) {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')

  async function handleLogin(event: FormEvent) {
    event.preventDefault()
    setAuthError('')
    try {
      const response = await authApi.login(email, password)
      onAuthenticated(response.accessToken)
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : 'Authentication failed. Check your credentials.')
    }
  }

  return <main className="dashboard-access-shell"><button className="dashboard-access-close" onClick={() => navigate('/')} aria-label="Return to OptiServe home"><X size={18} /></button><form className="dashboard-access-panel" onSubmit={handleLogin}><span className="landing-kicker"><LockKeyhole size={13} /> Secure access</span><h1>Enter operations.</h1><p>Authenticate to view live backend-authoritative operations.</p><label>Email<input type="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label><label>Password<input type="password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>{authError && <div className="dashboard-access-error">{authError}</div>}<button className="landing-primary-cta" type="submit">Authenticate <ArrowRight size={16} /></button></form></main>
}